/*
 * Copyright (c) 2017-2020 "Neo4j,"
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

import com.carrotsearch.hppc.ObjectLongMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.neo4j.graphalgo.RelationshipTypeMapping;
import org.neo4j.graphalgo.ResolvedPropertyMapping;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.api.GraphSetup;
import org.neo4j.graphalgo.api.ImmutableImportResult;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.huge.AdjacencyList;
import org.neo4j.graphalgo.core.huge.AdjacencyOffsets;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.NotInTransactionException;
import org.neo4j.internal.kernel.api.security.AuthSubject;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.KernelTransaction.Revertable;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.neo4j.internal.kernel.api.security.AccessMode.Static.READ;

public class CypherGraphFactory extends GraphFactory {

    public static final String TYPE = "cypher";

    static final String LIMIT = "limit";
    static final String SKIP = "skip";

    private final GraphDatabaseAPI api;
    private final GraphSetup setup;

    public CypherGraphFactory(GraphDatabaseAPI api, GraphSetup setup) {
        super(api, setup, false);
        this.api = api;
        this.setup = setup;
    }

    public final MemoryEstimation memoryEstimation() {
        BatchLoadResult nodeCount = new CountingCypherRecordLoader(nodeQuery(), api, setup).load();
        dimensions.nodeCount(nodeCount.rows());
        BatchLoadResult relCount = new CountingCypherRecordLoader(relationshipQuery(), api, setup).load();
        dimensions.maxRelCount(relCount.rows());
        return HugeGraphFactory.getMemoryEstimation(setup, dimensions);
    }

    @Override
    public MemoryEstimation memoryEstimation(GraphSetup setup, GraphDimensions dimensions) {
        return HugeGraphFactory.getMemoryEstimation(setup, dimensions);
    }

    @Override
    public ImportResult build() {
        // Temporarily override the security context to enforce read-only access during load
        try (Revertable ignored = setReadOnlySecurityContext()) {
            BatchLoadResult nodeCount = new CountingCypherRecordLoader(nodeQuery(), api, setup).load();

            Pair<GraphDimensions, IdsAndProperties> loadResult = new CypherNodeLoader(
                nodeQuery(),
                nodeCount.rows(),
                api,
                setup,
                dimensions
            ).load();

            ImportResult importResult = loadRelationships(
                relationshipQuery(),
                loadResult.getTwo(),
                loadResult.getOne()
            );
            progressLogger.logDone(setup.tracker());

            return importResult;
        }
    }

    private String nodeQuery() {
        return setup.nodeQuery().orElseThrow(() -> new IllegalArgumentException("Missing node query"));
    }

    private String relationshipQuery() {
        return setup.relationshipQuery().orElseThrow(() -> new IllegalArgumentException("Missing relationship query"));
    }

    private ImportResult loadRelationships(
        String relationshipQuery,
        IdsAndProperties idsAndProperties,
        GraphDimensions nodeLoadDimensions
    ) {
        CypherRelationshipLoader relationshipLoader = new CypherRelationshipLoader(
            relationshipQuery,
            idsAndProperties.idMap(),
            api,
            setup,
            nodeLoadDimensions
        );

        Pair<GraphDimensions, ObjectLongMap<RelationshipTypeMapping>> result = relationshipLoader.load();

        GraphDimensions resultDimensions = result.getOne();
        ObjectLongMap<RelationshipTypeMapping> relationshipCounts = result.getTwo();

        Map<String, Map<String, Graph>> graphs = relationshipLoader
            .allBuilders()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().typeName(),
                entry -> {

                    RelationshipTypeMapping relationshipTypeMapping = entry.getKey();
                    RelationshipsBuilder relationshipsBuilder = entry.getValue();

                    if (relationshipsBuilder == null) {
                        throw new IllegalStateException(
                            String.format(
                                "RelationshipsBuilder must not be `null` for relationship type `%s`.",
                                relationshipTypeMapping.typeName()
                            )
                        );
                    }

                    AdjacencyList adjacencyList = relationshipsBuilder.adjacency.build();
                    AdjacencyOffsets adjacencyOffsets = relationshipsBuilder.globalAdjacencyOffsets;

                    long relationshipCount = relationshipCounts.getOrDefault(relationshipTypeMapping, 0L);

                    if (!resultDimensions.relProperties().hasMappings()) {
                        HugeGraph graph = HugeGraph.create(
                            setup.tracker(),
                            idsAndProperties.hugeIdMap,
                            idsAndProperties.properties,
                            adjacencyList,
                            adjacencyOffsets,
                            null,
                            null,
                            relationshipCount,
                            setup.loadAsUndirected()
                        );
                        return Collections.singletonMap(ANY_REL_TYPE, graph);
                    } else {
                        return resultDimensions.relProperties().enumerate().map(propertyEntry -> {
                            int propertyKeyId = propertyEntry.getOne();
                            ResolvedPropertyMapping propertyMapping = propertyEntry.getTwo();
                            HugeGraph graph = create(
                                setup.tracker(),
                                idsAndProperties.hugeIdMap,
                                idsAndProperties.properties,
                                relationshipsBuilder,
                                adjacencyList,
                                adjacencyOffsets,
                                propertyKeyId,
                                propertyMapping,
                                relationshipCount,
                                setup.loadAsUndirected()
                            );
                            return Tuples.pair(propertyMapping.propertyKey(), graph);
                        }).collect(Collectors.toMap(Pair::getOne, Pair::getTwo));
                    }
                }
            ));

        return ImmutableImportResult
            .builder()
            .dimensions(resultDimensions)
            .graphs(GraphsByRelationshipType.of(graphs))
            .build();
    }

    private HugeGraph create(
        AllocationTracker tracker,
        IdMap idMapping,
        Map<String, NodeProperties> nodeProperties,
        RelationshipsBuilder relationshipsBuilder,
        AdjacencyList adjacencyList,
        AdjacencyOffsets adjacencyOffsets,
        int propertyKeyId,
        ResolvedPropertyMapping propertyMapping,
        long relationshipCount,
        boolean loadAsUndirected
    ) {

        AdjacencyList propertyList = null;
        AdjacencyOffsets propertyOffsets = null;
        if (relationshipsBuilder != null) {
            if (propertyMapping.propertyKeyId() != StatementConstants.NO_SUCH_PROPERTY_KEY) {
                propertyOffsets = relationshipsBuilder.globalWeightOffsets[propertyKeyId];
                if (propertyOffsets != null) {
                    propertyList = relationshipsBuilder.weights[propertyKeyId].build();
                }
            }
        }

        Optional<Double> maybeDefaultValue = propertyMapping.exists()
            ? Optional.empty()
            : Optional.of(propertyMapping.defaultValue());

        return HugeGraph.create(
            tracker,
            idMapping,
            nodeProperties,
            relationshipCount,
            null,
            adjacencyList,
            null,
            adjacencyOffsets,
            maybeDefaultValue,
            Optional.empty(),
            Optional.ofNullable(propertyList),
            Optional.empty(),
            Optional.ofNullable(propertyOffsets),
            loadAsUndirected
        );
    }

    private Revertable setReadOnlySecurityContext() {
        try {
            KernelTransaction kernelTransaction = api
                .getDependencyResolver()
                .resolveDependency(ThreadToStatementContextBridge.class)
                .getKernelTransactionBoundToThisThread(true);
            AuthSubject subject = kernelTransaction.securityContext().subject();
            SecurityContext securityContext = new SecurityContext(subject, READ);
            return kernelTransaction.overrideWith(securityContext);
        } catch (NotInTransactionException ex) {
            // happens only in tests
            throw new IllegalStateException("Must run in a transaction.", ex);
        }
    }
}
