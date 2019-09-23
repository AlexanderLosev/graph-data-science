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
package org.neo4j.graphalgo.core.utils.paged;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.utils.BitUtil;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackingIntDoubleHashMapTest {

    @Test
    void shouldComputeMemoryEstimationForSinglePage() {
        GraphDimensions dimensions = new GraphDimensions.Builder().setNodeCount(100).build();
        MemoryRange memoryRange = TrackingIntDoubleHashMap
                .memoryEstimation()
                .estimate(dimensions, 1)
                .memoryUsage();

        long minBufferSize = 9L;
        long maxBufferSize = 257;

        long min =
                64 /* TrackingIntDoubleHashMap.class */ +
                BitUtil.align(16 + minBufferSize * 4, 8) /* sizeOfIntArray(minBufferSize) */ +
                BitUtil.align(16 + minBufferSize * 8, 8) /* sizeOfDoubleArray(minBufferSize) */;
        long max =
                64 /* TrackingIntDoubleHashMap.class */ +
                BitUtil.align(16 + maxBufferSize * 4, 8) /* sizeOfIntArray(maxBufferSize) */ +
                BitUtil.align(16 + maxBufferSize * 8, 8) /* sizeOfDoubleArray(maxBufferSize) */;

        assertEquals(MemoryRange.of(min, max), memoryRange);
    }

    @Test
    void shouldComputeMemoryEstimationForMultiplePages() {
        GraphDimensions dimensions = new GraphDimensions.Builder().setNodeCount(100_000).build();
        MemoryRange memoryRange = TrackingIntDoubleHashMap
                .memoryEstimation()
                .estimate(dimensions, 1)
                .memoryUsage();

        long minBufferSize = 9L;
        long maxBufferSize = 32769L;

        long min =
                64 /* TrackingIntDoubleHashMap.class */ +
                BitUtil.align(16 + minBufferSize * 4, 8) /* sizeOfIntArray(minBufferSize) */ +
                BitUtil.align(16 + minBufferSize * 8, 8) /* sizeOfDoubleArray(minBufferSize) */;
        long max =
                64 /* TrackingIntDoubleHashMap.class */ +
                BitUtil.align(16 + maxBufferSize * 4, 8) /* sizeOfIntArray(maxBufferSize) */ +
                BitUtil.align(16 + maxBufferSize * 8, 8) /* sizeOfDoubleArray(maxBufferSize) */;

        assertEquals(MemoryRange.of(min, max), memoryRange);
    }
}
