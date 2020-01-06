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

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.loading.CompressedLongArray;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;

import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressedLongArrayTest {

    @Test
    void add() {
        CompressedLongArray compressedLongArray = new CompressedLongArray(AllocationTracker.EMPTY);

        final long[] inValues = {1, 2, 3, 4};
        compressedLongArray.add(inValues.clone(), 0, inValues.length);

        assertTrue(compressedLongArray.storage().length >= inValues.length);

        long[] outValues = new long[4];
        int uncompressedValueCount = compressedLongArray.uncompress(outValues);

        assertEquals(4, uncompressedValueCount);
        assertArrayEquals(inValues, outValues);
    }

    @Test
    void addSameValues() {
        CompressedLongArray compressedLongArray1 = new CompressedLongArray(AllocationTracker.EMPTY);
        CompressedLongArray compressedLongArray2 = new CompressedLongArray(AllocationTracker.EMPTY);

        int count = 10;

        long[] inValues1 = LongStream.range(0, count).toArray();
        compressedLongArray1.add(inValues1, 0, count);

        long[] inValues2 = LongStream.range(0, count).toArray();
        for (int i = 0; i < count; i++) {
            compressedLongArray2.add(inValues2, i, i + 1);
        }

        long[] outValues1 = new long[count];
        long[] outValues2 = new long[count];
        int uncompressedValueCount1 = compressedLongArray1.uncompress(outValues1);
        int uncompressedValueCount2 = compressedLongArray2.uncompress(outValues2);

        assertEquals(uncompressedValueCount1, uncompressedValueCount2);
        assertArrayEquals(outValues1, outValues2);
    }

    @Test
    void addReverseOrder() {
        CompressedLongArray compressedLongArray = new CompressedLongArray(AllocationTracker.EMPTY);

        int count = 10;

        long[] inValues = LongStream.range(0, count).map(i -> count - i).toArray();
        compressedLongArray.add(inValues.clone(), 0, inValues.length);

        assertTrue(compressedLongArray.storage().length >= 10);

        long[] outValues = new long[count];
        int uncompressedValueCount = compressedLongArray.uncompress(outValues);

        assertEquals(count, uncompressedValueCount);
        assertArrayEquals(inValues, outValues);
    }

    @Test
    void addWithWeights() {
        CompressedLongArray compressedLongArray = new CompressedLongArray(AllocationTracker.EMPTY, 1);

        final long[] inValues = {1, 2, 3, 4};
        final long[] inWeights = DoubleStream.of(1.0, 2.0, 3.0, 4.0).mapToLong(Double::doubleToLongBits).toArray();
        compressedLongArray.add(inValues.clone(), new long[][]{inWeights.clone()}, 0, inValues.length);

        // 10 bytes are enough to store the input values (1 byte each)
        assertTrue(compressedLongArray.storage().length >= inValues.length);

        long[] outValues = new long[4];
        int uncompressedValueCount = compressedLongArray.uncompress(outValues);
        assertEquals(4, uncompressedValueCount);
        assertArrayEquals(inValues, outValues);

        assertArrayEquals(inWeights, Arrays.copyOf(compressedLongArray.weights()[0], inWeights.length));
    }
}
