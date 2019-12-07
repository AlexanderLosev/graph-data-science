/*
 * Copyright (c) 2017-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.loading;

import com.carrotsearch.hppc.ObjectLongHashMap;
import com.carrotsearch.hppc.ObjectLongMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.PropertyMappings;
import org.neo4j.graphalgo.RelationshipTypeMapping;
import org.neo4j.graphalgo.api.GraphSetup;
import org.neo4j.graphalgo.core.DeduplicationStrategy;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.neo4j.graphalgo.core.DeduplicationStrategy.NONE;

class CypherRelationshipLoader extends CypherRecordLoader<Pair<GraphDimensions, ObjectLongMap<RelationshipTypeMapping>>> {

    private final IdMap idMap;
    private final Context loaderContext;
    private final GraphDimensions outerDimensions;
    private final boolean hasExplicitPropertyMappings;
    private final DeduplicationStrategy globalDeduplicationStrategy;
    private final double globalDefaultPropertyValue;
    private final Map<RelationshipTypeMapping, LongAdder> relationshipCounters;

    // Property mappings are either defined upfront in
    // the procedure configuration or during load time
    // by looking at the columns returned by the query.
    private Map<String, Integer> propertyKeyIdsByName;
    private Map<String, Double> propertyDefaultValueByName;
    private boolean importWeights;
    private int[] propertyKeyIds;
    private double[] propertyDefaultValues;
    private DeduplicationStrategy[] deduplicationStrategies;
    private boolean initializedFromResult;

    private GraphDimensions resultDimensions;

    CypherRelationshipLoader(
        IdMap idMap,
        GraphDatabaseAPI api,
        GraphSetup setup,
        GraphDimensions dimensions
    ) {
        super(setup.relationshipType(), idMap.nodeCount(), api, setup);
        this.idMap = idMap;
        this.outerDimensions = dimensions;
        this.loaderContext = new Context();
        this.relationshipCounters = new HashMap<>();

        this.hasExplicitPropertyMappings = dimensions.relProperties().hasMappings();

        this.globalDeduplicationStrategy = setup.deduplicationStrategy() == DeduplicationStrategy.DEFAULT
            ? NONE
            : setup.deduplicationStrategy();
        this.globalDefaultPropertyValue = setup.relationshipDefaultPropertyValue().orElse(Double.NaN);

        this.resultDimensions = initFromDimension(dimensions);
    }

    private GraphDimensions initFromDimension(GraphDimensions dimensions) {
        MutableInt propertyKeyId = new MutableInt(0);

        propertyKeyIdsByName = dimensions
            .relProperties()
            .stream()
            .collect(toMap(PropertyMapping::neoPropertyKey, unused -> propertyKeyId.getAndIncrement()));
        propertyDefaultValueByName = dimensions
            .relProperties()
            .stream()
            .collect(toMap(PropertyMapping::neoPropertyKey, PropertyMapping::defaultValue));

        // We can not rely on what the token store gives us.
        // We need to resolve the given property mappings
        // using our newly created property key identifiers.
        GraphDimensions newDimensions = new GraphDimensions.Builder(dimensions)
            .setRelationshipProperties(PropertyMappings.of(dimensions.relProperties().stream()
                .map(PropertyMapping::copyFrom)
                .map(mapping -> mapping.resolveWith(propertyKeyIdsByName.get(mapping.neoPropertyKey)))
                .toArray(PropertyMapping[]::new)))
            .build();

        importWeights = newDimensions.relProperties().atLeastOneExists();
        propertyKeyIds = newDimensions.relProperties().allPropertyKeyIds();
        propertyDefaultValues = newDimensions.relProperties().allDefaultWeights();
        deduplicationStrategies = getDeduplicationStrategies(newDimensions);

        return newDimensions;
    }

    @Override
    BatchLoadResult loadOneBatch(long offset, int batchSize, int bufferSize) {
        Result queryResult = runLoadingQuery(offset, batchSize);

        List<String> allColumns = queryResult.columns();

        // If the user specifies property mappings, we use those.
        // Otherwise, we create new property mappings from the result columns.
        // We do that only once, as each batch has the same columns.
        if (!hasExplicitPropertyMappings && !initializedFromResult) {
            Predicate<String> contains = RelationshipRowVisitor.RESERVED_COLUMNS::contains;
            List<String> propertyColumns = allColumns.stream().filter(contains.negate()).collect(Collectors.toList());

            PropertyMapping[] propertyMappings = propertyColumns
                .stream()
                .map(propertyColumn -> PropertyMapping.of(
                    propertyColumn,
                    propertyColumn,
                    globalDefaultPropertyValue,
                    globalDeduplicationStrategy
                ))
                .toArray(PropertyMapping[]::new);

            GraphDimensions innerDimensions = new GraphDimensions.Builder(outerDimensions)
                .setRelationshipProperties(PropertyMappings.of(propertyMappings))
                .build();

            resultDimensions = initFromDimension(innerDimensions);

            initializedFromResult = true;
        }

        boolean isAnyRelTypeQuery = !allColumns.contains(RelationshipRowVisitor.TYPE_COLUMN);

        if (isAnyRelTypeQuery) {
            loaderContext.getOrCreateImporterBuilder(RelationshipTypeMapping.all());
        }

        RelationshipRowVisitor visitor = new RelationshipRowVisitor(
            idMap,
            loaderContext,
            propertyKeyIdsByName,
            propertyDefaultValueByName,
            bufferSize,
            isAnyRelTypeQuery
        );

        queryResult.accept(visitor);
        visitor.flushAll();
        return new BatchLoadResult(offset, visitor.rows(), -1L, visitor.relationshipCount());
    }

    @Override
    void updateCounts(BatchLoadResult result) { }

    @Override
    Pair<GraphDimensions, ObjectLongMap<RelationshipTypeMapping>> result() {
        List<Runnable> flushTasks = loaderContext.importerBuildersByType
            .values()
            .stream()
            .flatMap(SingleTypeRelationshipImporter.Builder.WithImporter::flushTasks)
            .collect(Collectors.toList());

        ParallelUtil.run(flushTasks, setup.executor());

        ObjectLongMap<RelationshipTypeMapping> relationshipCounters = new ObjectLongHashMap<>(this.relationshipCounters.size());
        this.relationshipCounters.forEach((mapping, counter) -> relationshipCounters.put(mapping, counter.sum()));
        return Pair.of(resultDimensions, relationshipCounters);
    }

    Map<RelationshipTypeMapping, RelationshipsBuilder> allBuilders() {
        return loaderContext.allBuilders;
    }

    private DeduplicationStrategy[] getDeduplicationStrategies(GraphDimensions dimensions) {
        DeduplicationStrategy[] deduplicationStrategies = dimensions
            .relProperties()
            .stream()
            .map(property -> property.deduplicationStrategy() == DeduplicationStrategy.DEFAULT
                ? NONE
                : property.deduplicationStrategy()
            )
            .toArray(DeduplicationStrategy[]::new);
        // TODO: backwards compat code
        if (deduplicationStrategies.length == 0) {
            deduplicationStrategies = new DeduplicationStrategy[]{globalDeduplicationStrategy};
        }
        return deduplicationStrategies;
    }

    class Context {

        private final Map<RelationshipTypeMapping, SingleTypeRelationshipImporter.Builder.WithImporter> importerBuildersByType;
        private final Map<RelationshipTypeMapping, RelationshipsBuilder> allBuilders;

        private final int pageSize;
        private final int numberOfPages;

        Context() {
            this.importerBuildersByType = new HashMap<>();
            this.allBuilders = new HashMap<>();

            ImportSizing importSizing = ImportSizing.of(setup.concurrency(), idMap.nodeCount());
            this.pageSize = importSizing.pageSize();
            this.numberOfPages = importSizing.numberOfPages();
        }

        synchronized SingleTypeRelationshipImporter.Builder.WithImporter getOrCreateImporterBuilder(
            RelationshipTypeMapping relationshipTypeMapping
        ) {
            SingleTypeRelationshipImporter.Builder.WithImporter importerBuilder;
            if (importerBuildersByType.containsKey(relationshipTypeMapping)) {
                importerBuilder = importerBuildersByType.get(relationshipTypeMapping);
            } else {
                importerBuilder = createImporter(relationshipTypeMapping);
                importerBuildersByType.put(relationshipTypeMapping, importerBuilder);
            }
            return importerBuilder;
        }

        private SingleTypeRelationshipImporter.Builder.WithImporter createImporter(RelationshipTypeMapping typeMapping) {
            RelationshipsBuilder builder = new RelationshipsBuilder(
                deduplicationStrategies,
                setup.tracker(),
                propertyKeyIds.length
            );

            allBuilders.put(typeMapping, builder);

            SingleTypeRelationshipImporter.Builder importerBuilder = createImporterBuilder(
                pageSize,
                numberOfPages,
                typeMapping,
                builder,
                setup.tracker()
            );

            relationshipCounters.put(typeMapping, importerBuilder.relationshipCounter());

            return importerBuilder.loadImporter(
                false,
                true,
                false,
                importWeights
            );
        }

        private SingleTypeRelationshipImporter.Builder createImporterBuilder(
            int pageSize,
            int numberOfPages,
            RelationshipTypeMapping mapping,
            RelationshipsBuilder relationshipsBuilder,
            AllocationTracker tracker
        ) {
            LongAdder relationshipCounter = new LongAdder();
            AdjacencyBuilder adjacencyBuilder = AdjacencyBuilder.compressing(
                relationshipsBuilder,
                numberOfPages,
                pageSize,
                tracker,
                relationshipCounter,
                propertyKeyIds,
                propertyDefaultValues
            );

            RelationshipImporter relationshipImporter = new RelationshipImporter(
                setup.tracker(),
                adjacencyBuilder,
                null
            );

            return new SingleTypeRelationshipImporter.Builder(mapping, relationshipImporter, relationshipCounter);
        }
    }
}
