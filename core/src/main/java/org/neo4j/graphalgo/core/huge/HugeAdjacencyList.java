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
package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.core.huge.loader.MutableIntValue;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.mem.MemoryUsage;
import org.neo4j.graphalgo.core.utils.paged.PageUtil;

import static org.neo4j.graphalgo.core.huge.loader.VarLongEncoding.encodedVLongSize;
import static org.neo4j.graphalgo.core.utils.BitUtil.ceilDiv;
import static org.neo4j.graphalgo.core.utils.paged.PageUtil.indexInPage;
import static org.neo4j.graphalgo.core.utils.paged.PageUtil.pageIndex;

public final class HugeAdjacencyList {

    public static final int PAGE_SHIFT = 18;
    public static final int PAGE_SIZE = 262144; // 1 << PAGE_SHIFT
    public static final long PAGE_MASK = 262143L; // PAGE_SIZE - 1

    private final long allocatedMemory;
    private byte[][] pages;

    public static MemoryEstimation memoryEstimation(boolean undirected) {

        return MemoryEstimations
                .builder(HugeAdjacencyList.class)
                .rangePerGraphDimension("pages", dim -> {
                    long nodeCount = dim.nodeCount();
                    long relCount = undirected ? dim.maxRelCount() * 2 : dim.maxRelCount();
                    long avgDegree = (nodeCount > 0) ? ceilDiv(relCount, nodeCount) : 0L;

                    // Best case scenario:
                    // Difference between node identifiers in each adjacency list is 1.
                    // This leads to ideal compression through delta encoding.
                    int deltaBestCase = 1;
                    long bestCaseAdjacencySize = computeAdjacencyByteSize(avgDegree, nodeCount, deltaBestCase);

                    // Worst case scenario:
                    // Relationships are equally distributed across nodes, i.e. each node has the same number of rels.
                    // Within each adjacency list, all identifiers have the highest possible difference between each other.
                    // Highest possible difference is the number of nodes divided by the average degree.
                    long deltaWorstCase = (avgDegree > 0) ? ceilDiv(nodeCount, avgDegree) : 0L;
                    long worstCaseAdjacencySize = computeAdjacencyByteSize(avgDegree, nodeCount, deltaWorstCase);

                    int minPages = PageUtil.numPagesFor(bestCaseAdjacencySize, PAGE_SHIFT, PAGE_MASK);
                    int maxPages = PageUtil.numPagesFor(worstCaseAdjacencySize, PAGE_SHIFT, PAGE_MASK);

                    long bytesPerPage = MemoryUsage.sizeOfByteArray(PAGE_SIZE);
                    long minMemoryReqs = minPages * bytesPerPage + MemoryUsage.sizeOfObjectArray(minPages);
                    long maxMemoryReqs = maxPages * bytesPerPage + MemoryUsage.sizeOfObjectArray(maxPages);

                    return MemoryRange.of(minMemoryReqs, maxMemoryReqs);
                })
                .build();
    }

    /* test private */ static long computeAdjacencyByteSize(long avgDegree, long nodeCount, long delta) {
        long firstAdjacencyIdAvgByteSize = (avgDegree > 0) ? ceilDiv(encodedVLongSize(nodeCount), 2) : 0L;
        int relationshipByteSize = encodedVLongSize(delta);
        int degreeByteSize = Integer.BYTES;
        long compressedAdjacencyByteSize = relationshipByteSize * Math.max(0, (avgDegree - 1));
        return (degreeByteSize + firstAdjacencyIdAvgByteSize + compressedAdjacencyByteSize) * nodeCount;
    }

    public HugeAdjacencyList(byte[][] pages) {
        this.pages = pages;
        this.allocatedMemory = memoryOfPages(pages);
    }

    private static long memoryOfPages(byte[][] pages) {
        long memory = MemoryUsage.sizeOfObjectArray(pages.length);
        for (byte[] page : pages) {
            if (page != null) {
                memory += MemoryUsage.sizeOfByteArray(page.length);
            }
        }
        return memory;
    }

    int getDegree(long index) {
        return AdjacencyDecompression.readInt(
                pages[pageIndex(index, PAGE_SHIFT)],
                indexInPage(index, PAGE_MASK));
    }

    Cursor newCursor() {
        return new Cursor(pages);
    }

    public final long release() {
        if (pages == null) {
            return 0L;
        }
        pages = null;
        return allocatedMemory;
    }

    /**
     * Initialise the given cursor with the given offset
     */
    Cursor deltaCursor(Cursor reuse, long offset) {
        return reuse.init(offset);
    }

    /**
     * Get a new cursor initialised on the given offset
     */
    Cursor deltaCursor(long offset) {
        return newCursor().init(offset);
    }

    public static final class Cursor extends MutableIntValue {

        // TODO: free
        private byte[][] pages;
        private final AdjacencyDecompression decompress;

        private int maxTargets;
        private int currentTarget;

        private Cursor(byte[][] pages) {
            this.pages = pages;
            this.decompress = new AdjacencyDecompression();
        }

        /**
         * Copy iteration state from another cursor without changing {@code other}.
         */
        void copyFrom(Cursor other) {
            decompress.copyFrom(other.decompress);
            currentTarget = other.currentTarget;
            maxTargets = other.maxTargets;
        }

        /**
         * Return how many targets can be decoded in total. This is equivalent to the degree.
         */
        public int cost() {
            return maxTargets;
        }

        /**
         * Return how many targets are still left to be decoded.
         */
        int remaining() {
            return maxTargets - currentTarget;
        }

        /**
         * Return true iff there is at least one more target to decode.
         */
        boolean hasNextVLong() {
            return currentTarget < maxTargets;
        }

        /**
         * Read and decode the next target id.
         * It is undefined behavior if this is called after {@link #hasNextVLong()} returns {@code false}.
         */
        long nextVLong() {
            int current = currentTarget++;
            int remaining = maxTargets - current;
            return decompress.next(remaining);
        }

        /**
         * Read and decode target ids until it is strictly larger than (`>`) the provided {@code target}.
         * Might return an id that is less than or equal to {@code target} iff the cursor did exhaust before finding an
         * id that is large enough.
         * {@code skipUntil(target) <= target} can be used to distinguish the no-more-ids case and afterwards {@link #hasNextVLong()}
         * will return {@code false}
         */
        long skipUntil(long target) {
            long value = decompress.skipUntil(target, remaining(), this);
            this.currentTarget += this.value;
            return value;
        }

        /**
         * Read and decode target ids until it is larger than or equal (`>=`) the provided {@code target}.
         * Might return an id that is less than {@code target} iff the cursor did exhaust before finding an
         * id that is large enough.
         * {@code advance(target) < target} can be used to distinguish the no-more-ids case and afterwards {@link #hasNextVLong()}
         * will return {@code false}
         */
        long advance(long target) {
            long value = decompress.advance(target, remaining(), this);
            this.currentTarget += this.value;
            return value;
        }

        Cursor init(long fromIndex) {
            maxTargets = decompress.reset(
                    pages[pageIndex(fromIndex, PAGE_SHIFT)],
                    indexInPage(fromIndex, PAGE_MASK));
            currentTarget = 0;
            return this;
        }
    }
}
