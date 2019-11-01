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
package org.neo4j.graphalgo.impl.coloring;

import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.logging.Log;

public class K1ColoringFactory extends AlgorithmFactory<K1Coloring> {

    @Override
    public K1Coloring build(
            final Graph graph,
            final ProcedureConfiguration configuration,
            final AllocationTracker tracker,
            final Log log) {
        int concurrency = configuration.getConcurrency();
        int batchSize = configuration.getBatchSize();
        return new K1Coloring(
                graph,
                batchSize,
                concurrency,
                Pools.DEFAULT,
                tracker
        );
    }

    @Override
    public MemoryEstimation memoryEstimation() {
        return MemoryEstimations.builder(K1Coloring.class)
                .perNode("labels", HugeLongArray::memoryEstimation)
//                .perThread("votes", MemoryEstimations.builder()
//                        .field("init step", InitStep.class)
//                        .field("compute step", ComputeStep.class)
//                        .field("step runner", StepRunner.class)
//                        .field("compute step consumer", ComputeStepConsumer.class)
//                        .field("votes container", LongDoubleScatterMap.class)
//                        .rangePerNode("votes", nodeCount -> {
//                            int minBufferSize = OpenHashContainers.emptyBufferSize();
//                            int maxBufferSize = OpenHashContainers.expectedBufferSize((int) Math.min(nodeCount, Integer.MAX_VALUE));
//                            if (maxBufferSize < minBufferSize) {
//                                maxBufferSize = minBufferSize;
//                            }
//                            long min = sizeOfLongArray(minBufferSize) + sizeOfDoubleArray(minBufferSize);
//                            long max = sizeOfLongArray(maxBufferSize) + sizeOfDoubleArray(maxBufferSize);
//                            return MemoryRange.of(min, max);
//                        }).build())
                .build();
    }
}
