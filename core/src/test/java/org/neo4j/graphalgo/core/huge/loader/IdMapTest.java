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

import org.junit.Test;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.mem.MemoryTree;

import static org.junit.Assert.assertEquals;

public class IdMapTest {

    @Test
    public void shouldComputeMemoryEstimation() {
        GraphDimensions dimensions = new GraphDimensions.Builder().setNodeCount(0).setHighestNeoId(0).build();
        MemoryTree memRec = IdMap.memoryEstimation().apply(dimensions, 1);
        assertEquals(MemoryRange.of(32L + 40L + 48L), memRec.memoryUsage());

        dimensions = new GraphDimensions.Builder().setNodeCount(100L).setHighestNeoId(100L).build();
        memRec = IdMap.memoryEstimation().apply(dimensions, 1);
        assertEquals(MemoryRange.of(32L + 840L + 32840L), memRec.memoryUsage());

        dimensions = new GraphDimensions.Builder().setNodeCount(1L).setHighestNeoId(100_000_000_000L).build();
        memRec = IdMap.memoryEstimation().apply(dimensions, 1);
        assertEquals(MemoryRange.of(32L + 48L + 97_689_088L), memRec.memoryUsage());

        dimensions = new GraphDimensions.Builder().setNodeCount(10_000_000L).setHighestNeoId(100_000_000_000L).build();
        memRec = IdMap.memoryEstimation().apply(dimensions, 1);
        assertEquals(MemoryRange.of(32L + 80_000_040L + 177_714_832L, 32L + 80_000_040L + 327_937_656_304L), memRec.memoryUsage());

        dimensions = new GraphDimensions.Builder().setNodeCount(100_000_000L).setHighestNeoId(100_000_000_000L).build();
        memRec = IdMap.memoryEstimation().apply(dimensions, 1);
        assertEquals(MemoryRange.of(32L + 800_000_040L + 898_077_664L, 32L + 800_000_040L + 800_488_297_696L), memRec.memoryUsage());
    }
}
