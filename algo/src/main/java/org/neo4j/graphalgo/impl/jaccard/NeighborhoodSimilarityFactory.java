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

package org.neo4j.graphalgo.impl.jaccard;

import com.carrotsearch.hppc.BitSet;
import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;
import org.neo4j.logging.Log;

import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfLongArray;

public class NeighborhoodSimilarityFactory extends AlgorithmFactory<NeighborhoodSimilarity> {

    private final NeighborhoodSimilarity.Config jaccardConfig;
    private final boolean computesSimilarityGraph;
    public NeighborhoodSimilarityFactory(NeighborhoodSimilarity.Config jaccardConfig, boolean computesSimilarityGraph) {
        this.jaccardConfig = jaccardConfig;
        this.computesSimilarityGraph = computesSimilarityGraph;
    }

    @Override
    public NeighborhoodSimilarity build(Graph graph, ProcedureConfiguration configuration, AllocationTracker tracker, Log log) {
        return new NeighborhoodSimilarity(graph, jaccardConfig, Pools.DEFAULT, tracker);
    }

    @Override
    public MemoryEstimation memoryEstimation() {
        MemoryEstimations.Builder builder = MemoryEstimations.builder(NeighborhoodSimilarity.class)
            .perNode("node filter", nodeCount -> sizeOfLongArray(BitSet.bits2words(nodeCount)))
            .add(
                "vectors",
                MemoryEstimations.setup("", (dimensions, concurrency) -> {
                    int averageDegree = dimensions.nodeCount() == 0
                        ? 0
                        : Math.toIntExact(dimensions.maxRelCount() / dimensions.nodeCount());
                    long averageVectorSize = sizeOfLongArray(averageDegree);
                    return MemoryEstimations.builder(HugeObjectArray.class)
                        .perNode("array", nodeCount -> nodeCount * averageVectorSize).build();
                })
            );
        if (computesSimilarityGraph) {
            builder.add(
                "similarity graph",
                SimilarityGraphBuilder.memoryEstimation(jaccardConfig.topK(), jaccardConfig.top())
            );
        }
        if (jaccardConfig.topK() > 0) {
            builder.add(
                "topK map",
                MemoryEstimations.setup("", (dimensions, concurrency) ->
                    TopKMap.memoryEstimation(dimensions.nodeCount(), jaccardConfig.topK()))
            );
        }
        return builder.build();
    }
}
