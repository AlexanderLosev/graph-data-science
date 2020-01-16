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
package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.api.IntersectionConsumer;
import org.neo4j.graphalgo.api.RelationshipConsumer;
import org.neo4j.graphalgo.api.RelationshipIntersect;

/**
 * An instance of this is not thread-safe; Iteration/Intersection on multiple threads will
 * throw misleading {@link NullPointerException}s.
 * Instances are however safe to use concurrently with other {@link org.neo4j.graphalgo.api.RelationshipIterator}s.
 */

class HugeGraphIntersectImpl implements RelationshipIntersect {

    private AdjacencyList adjacency;
    private AdjacencyOffsets offsets;
    private AdjacencyList.DecompressingCursor empty;
    private AdjacencyList.DecompressingCursor cache;
    private AdjacencyList.DecompressingCursor cacheA;
    private AdjacencyList.DecompressingCursor cacheB;

    HugeGraphIntersectImpl(final AdjacencyList adjacency, final AdjacencyOffsets offsets) {
        assert adjacency != null;
        assert offsets != null;
        this.adjacency = adjacency;
        this.offsets = offsets;
        cache = adjacency.rawDecompressingCursor();
        cacheA = adjacency.rawDecompressingCursor();
        cacheB = adjacency.rawDecompressingCursor();
        empty = adjacency.rawDecompressingCursor();
    }

    @Override
    public void intersectAll(long nodeIdA, IntersectionConsumer consumer) {
        AdjacencyOffsets offsets = this.offsets;
        AdjacencyList adjacency = this.adjacency;

        AdjacencyList.DecompressingCursor mainDecompressingCursor = cursor(nodeIdA, cache, offsets, adjacency);
        long nodeIdB = mainDecompressingCursor.skipUntil(nodeIdA);
        if (nodeIdB <= nodeIdA) {
            return;
        }

        AdjacencyList.DecompressingCursor lead, follow, decompressingCursorA = cacheA, decompressingCursorB = cacheB;
        long nodeIdC, currentA, s, t;
        boolean hasNext = true;

        while (hasNext) {
            decompressingCursorB = cursor(nodeIdB, decompressingCursorB, offsets, adjacency);
            nodeIdC = decompressingCursorB.skipUntil(nodeIdB);
            if (nodeIdC > nodeIdB) {
                decompressingCursorA.copyFrom(mainDecompressingCursor);
                currentA = decompressingCursorA.advance(nodeIdC);

                if (currentA == nodeIdC) {
                    consumer.accept(nodeIdA, nodeIdB, nodeIdC);
                }

                if (decompressingCursorA.remaining() <= decompressingCursorB.remaining()) {
                    lead = decompressingCursorA;
                    follow = decompressingCursorB;
                } else {
                    lead = decompressingCursorB;
                    follow = decompressingCursorA;
                }

                while (lead.hasNextVLong() && follow.hasNextVLong()) {
                    s = lead.nextVLong();
                    t = follow.advance(s);
                    if (t == s) {
                        consumer.accept(nodeIdA, nodeIdB, s);
                    }
                }
            }

            if (hasNext = mainDecompressingCursor.hasNextVLong()) {
                nodeIdB = mainDecompressingCursor.nextVLong();
            }
        }
    }

    private int degree(long node, AdjacencyOffsets offsets, AdjacencyList array) {
        long offset = offsets.get(node);
        if (offset == 0L) {
            return 0;
        }
        return array.getDegree(offset);
    }

    private AdjacencyList.DecompressingCursor cursor(
            long node,
            AdjacencyList.DecompressingCursor reuse,
            AdjacencyOffsets offsets,
            AdjacencyList array) {
        final long offset = offsets.get(node);
        if (offset == 0L) {
            return empty;
        }
        return array.decompressingCursor(reuse, offset);
    }

    private void consumeNodes(
            long startNode,
            AdjacencyList.DecompressingCursor decompressingCursor,
            RelationshipConsumer consumer) {
        //noinspection StatementWithEmptyBody
        while (decompressingCursor.hasNextVLong() && consumer.accept(startNode, decompressingCursor.nextVLong())) ;
    }
}
