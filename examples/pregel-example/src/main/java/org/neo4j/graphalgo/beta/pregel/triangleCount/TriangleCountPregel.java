/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.graphalgo.beta.pregel.triangleCount;

import com.carrotsearch.hppc.LongHashSet;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;
import org.neo4j.graphalgo.beta.pregel.Messages;
import org.neo4j.graphalgo.beta.pregel.PregelComputation;
import org.neo4j.graphalgo.beta.pregel.PregelSchema;
import org.neo4j.graphalgo.beta.pregel.annotation.GDSMode;
import org.neo4j.graphalgo.beta.pregel.annotation.PregelProcedure;
import org.neo4j.graphalgo.beta.pregel.context.ComputeContext;

import java.util.concurrent.atomic.LongAdder;
import java.util.stream.LongStream;

/**
 * ! Assuming an unweighted graph
 */
@PregelProcedure(name = "example.pregel.triangleCount", modes = {GDSMode.STREAM})
public class TriangleCountPregel implements PregelComputation<TriangleCountPregelConfig> {

    public static final String TRIANGLE_COUNT = "TRIANGLES";

    @Override
    public PregelSchema schema(TriangleCountPregelConfig config) {
        return new PregelSchema.Builder()
            .add(TRIANGLE_COUNT, ValueType.LONG)
            .build();
    }

    @Override
    public void compute(ComputeContext<TriangleCountPregelConfig> context, Messages messages) {
        if (context.isInitialSuperstep()) {
            context.setNodeValue(TRIANGLE_COUNT, 0);
        } else if (context.superstep() == Phase.MERGE_NEIGHBORS.step) {
            var neighbourStream = context.getNeighbours();
            if (context.isMultiGraph()) {
                neighbourStream = neighbourStream.distinct();
            }

            long[] neighbours = neighbourStream.toArray();
            var isNeighbourFromA = new LongHashSet(neighbours.length);

            for (long nodeB : neighbours) {
                isNeighbourFromA.add(nodeB);
            }

            long nodeA = context.nodeId();
            LongAdder trianglesFromNodeA = new LongAdder();

            for (long nodeB : neighbours) {
                if (nodeB > nodeA) {
                    LongStream cNodes = context.getNeighbours(nodeB);
                    if (context.isMultiGraph()) {
                        cNodes = cNodes.distinct();
                    }
                    cNodes.forEach(nodeC -> {
                        // find common neighbors
                        // check indexed neighbours of A
                        if (nodeC > nodeB && isNeighbourFromA.contains(nodeC)) {
                            trianglesFromNodeA.increment();
                            context.sendTo(nodeB, 1);
                            context.sendTo(nodeC, 1);
                        }
                    });
                }
            }
            context.setNodeValue(TRIANGLE_COUNT, trianglesFromNodeA.longValue());
        } else if (context.superstep() == Phase.COUNT_TRIANGLES.step) {
            long triangles = context.longNodeValue(TRIANGLE_COUNT);
            for (Double ignored : messages) {
                triangles++;
            }

            context.setNodeValue(TRIANGLE_COUNT, triangles);
            context.voteToHalt();
        }
    }

    enum Phase {
        MERGE_NEIGHBORS(1),
        COUNT_TRIANGLES(2);

        final long step;

        Phase(int i) {
            step = i;
        }
    }
}
