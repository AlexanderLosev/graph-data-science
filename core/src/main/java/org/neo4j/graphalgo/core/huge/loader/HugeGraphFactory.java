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
package org.neo4j.graphalgo.core.huge.loader;

import com.carrotsearch.hppc.ObjectLongMap;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.graphalgo.KernelPropertyMapping;
import org.neo4j.graphalgo.RelationshipTypeMapping;
import org.neo4j.graphalgo.RelationshipTypeMappings;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.api.GraphSetup;
import org.neo4j.graphalgo.api.WeightMapping;
import org.neo4j.graphalgo.core.DeduplicateRelationshipsStrategy;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.huge.AdjacencyList;
import org.neo4j.graphalgo.core.huge.AdjacencyOffsets;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.loading.GraphsByRelationshipType;
import org.neo4j.graphalgo.core.utils.ApproximatedImportProgress;
import org.neo4j.graphalgo.core.utils.ImportProgress;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HugeGraphFactory extends GraphFactory {

    // TODO: make this configurable from somewhere
    private static final boolean LOAD_DEGREES = false;

    public HugeGraphFactory(GraphDatabaseAPI api, GraphSetup setup) {
        super(api, setup);
    }

    @Override
    public MemoryEstimation memoryEstimation() {
        return getMemoryEstimation(setup, dimensions);
    }

    public static MemoryEstimation getMemoryEstimation(
            final GraphSetup setup,
            final GraphDimensions dimensions) {
        MemoryEstimations.Builder builder = MemoryEstimations
                .builder(HugeGraph.class)
                .add("nodeIdMap", IdMap.memoryEstimation());

        // Node properties
        for (KernelPropertyMapping propertyMapping : dimensions.nodeProperties()) {
            int propertyId = propertyMapping.propertyKeyId;
            if (propertyId == StatementConstants.NO_SUCH_PROPERTY_KEY) {
                builder.add(propertyMapping.propertyName, NullWeightMap.MEMORY_USAGE);
            } else {
                builder.add(propertyMapping.propertyName, NodePropertyMap.memoryEstimation());
            }
        }

        // Relationship weight properties
        if (dimensions.relWeightId() != StatementConstants.NO_SUCH_PROPERTY_KEY) {
            // Adjacency lists and Adjacency offsets
            MemoryEstimation adjacencyListSize = AdjacencyList.uncompressedMemoryEstimation(setup.loadAsUndirected);
            MemoryEstimation adjacencyOffsetsSetup = AdjacencyOffsets.memoryEstimation();
            if (setup.loadOutgoing || setup.loadAsUndirected) {
                builder.add("outgoing weights", adjacencyListSize);
                builder.add("outgoing weight offsets", adjacencyOffsetsSetup);

            }
            if (setup.loadIncoming && !setup.loadAsUndirected) {
                builder.add("incoming weights", adjacencyListSize);
                builder.add("incoming weight offsets", adjacencyOffsetsSetup);
            }
        }

        // Adjacency lists and Adjacency offsets
        MemoryEstimation adjacencyListSize = AdjacencyList.compressedMemoryEstimation(setup.loadAsUndirected);
        MemoryEstimation adjacencyOffsetsSetup = AdjacencyOffsets.memoryEstimation();
        if (setup.loadOutgoing || setup.loadAsUndirected) {
            builder.add("outgoing", adjacencyListSize);
            builder.add("outgoing offsets", adjacencyOffsetsSetup);

        }
        if (setup.loadIncoming && !setup.loadAsUndirected) {
            builder.add("incoming", adjacencyListSize);
            builder.add("incoming offsets", adjacencyOffsetsSetup);
        }

        return builder.build();
    }

    @Override
    protected ImportProgress importProgress(
            final ProgressLogger progressLogger,
            final GraphDimensions dimensions,
            final GraphSetup setup) {

        // ops for scanning degrees
        long relOperations = LOAD_DEGREES ? dimensions.maxRelCount() : 0L;

        // batching for undirected double the amount of rels imported
        if (setup.loadIncoming || setup.loadAsUndirected) {
            relOperations += dimensions.maxRelCount();
        }
        if (setup.loadOutgoing || setup.loadAsUndirected) {
            relOperations += dimensions.maxRelCount();
        }

        return new ApproximatedImportProgress(
                progressLogger,
                setup.tracker,
                dimensions.nodeCount(),
                relOperations
        );
    }

    @Override
    public Graph importGraph() {
        RelationshipTypeMappings relationshipTypeIds = dimensions.relationshipTypeMappings();
        if (relationshipTypeIds.isMultipleTypes()) {
            String message = String.format(
                    "It is not possible to use multiple relationship types in implicit graph loading. Please use `algo.graph.load()` for this. Found relationship types: %s",
                    relationshipTypeIds
                            .stream()
                            .map(RelationshipTypeMapping::typeName)
                            .collect(Collectors.toList()));
            throw new IllegalArgumentException(message);
        }

        Map<String, HugeGraph> graphs = importAllGraphs();
        return Iterables.single(graphs.values());
    }

    public GraphsByRelationshipType loadGraphs() {
        validateTokens();
        return new GraphsByRelationshipType(importAllGraphs());
    }

    private Map<String, HugeGraph> importAllGraphs() {
        GraphDimensions dimensions = this.dimensions;
        int concurrency = setup.concurrency();
        AllocationTracker tracker = setup.tracker;
        IdsAndProperties mappingAndProperties = loadIdMap(tracker, concurrency);
        Map<String, HugeGraph> graphs = loadRelationships(dimensions, tracker, mappingAndProperties, concurrency);

        progressLogger.logDone(tracker);
        return graphs;
    }

    private IdsAndProperties loadIdMap(AllocationTracker tracker, int concurrency) {
        return new ScanningNodesImporter(
                api,
                dimensions,
                progress,
                tracker,
                setup.terminationFlag,
                threadPool,
                concurrency,
                setup.nodePropertyMappings)
                .call(setup.log);
    }

    private Map<String, HugeGraph> loadRelationships(
            GraphDimensions dimensions,
            AllocationTracker tracker,
            IdsAndProperties idsAndProperties,
            int concurrency) {
        DeduplicateRelationshipsStrategy deduplicateRelationshipsStrategy =
                setup.deduplicateRelationshipsStrategy == DeduplicateRelationshipsStrategy.DEFAULT
                        ? DeduplicateRelationshipsStrategy.SKIP
                        : setup.deduplicateRelationshipsStrategy;

        Map<RelationshipTypeMapping, Pair<RelationshipsBuilder, RelationshipsBuilder>> allBuilders = dimensions
                .relationshipTypeMappings()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        mapping -> createBuilderForRelationshipType(deduplicateRelationshipsStrategy, tracker)
                ));

        ScanningRelationshipsImporter scanningImporter = new ScanningRelationshipsImporter(
                setup,
                api,
                dimensions,
                progress,
                tracker,
                idsAndProperties.hugeIdMap,
                allBuilders,
                threadPool,
                concurrency
        );
        ObjectLongMap<RelationshipTypeMapping> relationshipCounts = scanningImporter.call(setup.log);

        return allBuilders.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().typeName(),
                entry -> {
                    Pair<RelationshipsBuilder, RelationshipsBuilder> builders = entry.getValue();
                    RelationshipsBuilder outgoingRelationshipsBuilder = builders.getLeft();
                    RelationshipsBuilder incomingRelationshipsBuilder = builders.getRight();
                    return buildGraph(
                            tracker,
                            idsAndProperties.hugeIdMap,
                            idsAndProperties.properties,
                            incomingRelationshipsBuilder,
                            outgoingRelationshipsBuilder,
                            setup.relationDefaultWeight,
                            relationshipCounts.getOrDefault(entry.getKey(), 0L),
                            setup.loadAsUndirected
                    );
                }));
    }

    private Pair<RelationshipsBuilder, RelationshipsBuilder> createBuilderForRelationshipType(
            DeduplicateRelationshipsStrategy deduplicateRelationshipsStrategy,
            AllocationTracker tracker) {
        RelationshipsBuilder outgoingRelationshipsBuilder = null;
        RelationshipsBuilder incomingRelationshipsBuilder = null;


        if (setup.loadAsUndirected) {
            outgoingRelationshipsBuilder = new RelationshipsBuilder(
                    deduplicateRelationshipsStrategy,
                    tracker,
                    setup.shouldLoadRelationshipWeight());
        } else {
            if (setup.loadOutgoing) {
                outgoingRelationshipsBuilder = new RelationshipsBuilder(
                        deduplicateRelationshipsStrategy,
                        tracker,
                        setup.shouldLoadRelationshipWeight());
            }
            if (setup.loadIncoming) {
                incomingRelationshipsBuilder = new RelationshipsBuilder(
                        deduplicateRelationshipsStrategy,
                        tracker,
                        setup.shouldLoadRelationshipWeight());
            }
        }

        return Pair.of(outgoingRelationshipsBuilder, incomingRelationshipsBuilder);
    }

    private HugeGraph buildGraph(
            final AllocationTracker tracker,
            final IdMap idMapping,
            final Map<String, WeightMapping> nodeProperties,
            final RelationshipsBuilder inRelationshipsBuilder,
            final RelationshipsBuilder outRelationshipsBuilder,
            final double defaultWeight,
            final long relationshipCount,
            final boolean loadAsUndirected) {

        AdjacencyList outAdjacencyList = null;
        AdjacencyOffsets outAdjacencyOffsets = null;
        AdjacencyList outWeightList = null;
        AdjacencyOffsets outWeightOffsets = null;
        if (outRelationshipsBuilder != null) {
            outAdjacencyList = outRelationshipsBuilder.adjacency.build();
            outAdjacencyOffsets = outRelationshipsBuilder.globalAdjacencyOffsets;

            if (setup.shouldLoadRelationshipWeight()) {
                outWeightList = outRelationshipsBuilder.weights.build();
                outWeightOffsets = outRelationshipsBuilder.globalWeightOffsets;
            }
        }

        AdjacencyList inAdjacencyList = null;
        AdjacencyOffsets inAdjacencyOffsets = null;
        AdjacencyList inWeightList = null;
        AdjacencyOffsets inWeightOffsets = null;
        if (inRelationshipsBuilder != null) {
            inAdjacencyList = inRelationshipsBuilder.adjacency.build();
            inAdjacencyOffsets = inRelationshipsBuilder.globalAdjacencyOffsets;

            if (setup.shouldLoadRelationshipWeight()) {
                inWeightList = inRelationshipsBuilder.weights.build();
                inWeightOffsets = inRelationshipsBuilder.globalWeightOffsets;
            }
        }

        return new HugeGraph(
                tracker,
                idMapping,
                nodeProperties,
                relationshipCount,
                inAdjacencyList,
                outAdjacencyList,
                inAdjacencyOffsets,
                outAdjacencyOffsets,
                defaultWeight,
                inWeightList,
                outWeightList,
                inWeightOffsets,
                outWeightOffsets,
                loadAsUndirected);
    }

}
