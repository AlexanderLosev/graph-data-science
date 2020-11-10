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

import org.neo4j.graphalgo.api.AdjacencyCursor;
import org.neo4j.graphalgo.api.IntersectionConsumer;
import org.neo4j.graphalgo.api.RelationshipIntersect;

import java.util.function.LongPredicate;

import static org.neo4j.graphalgo.api.AdjacencyCursor.NOT_FOUND;

/**
 * An instance of this is not thread-safe; Iteration/Intersection on multiple threads will
 * throw misleading {@link NullPointerException}s.
 * Instances are however safe to use concurrently with other {@link org.neo4j.graphalgo.api.RelationshipIterator}s.
 */

public abstract class GraphIntersect<CURSOR extends AdjacencyCursor> implements RelationshipIntersect {

    protected CURSOR empty;
    private final CURSOR cache;
    private final CURSOR cacheA;
    private final CURSOR cacheB;
    private final LongPredicate degreeFilter;

    protected GraphIntersect(
        CURSOR cache,
        CURSOR cacheA,
        CURSOR cacheB,
        CURSOR empty,
        long maxDegree
    ) {
        this.cache = cache;
        this.cacheA = cacheA;
        this.cacheB = cacheB;
        this.empty = empty;

        this.degreeFilter = maxDegree < Long.MAX_VALUE
            ? (node) -> degree(node) <= maxDegree
            : (ignore) -> true;
    }

    @Override
    public void intersectAll(long nodeA, IntersectionConsumer consumer) {
        // check the first node's degree
        if (!degreeFilter.test(nodeA)) {
            return;
        }

        CURSOR neighboursAMain = cursor(nodeA, cache);

        // find first neighbour B of A with id > A
        long nodeB = neighboursAMain.skipUntil(nodeA);
        // if there is no such neighbour -> no triangle (or we already found it)
        if (nodeB == NOT_FOUND) {
            return;
        }

        // iterates over neighbours of A
        CURSOR neighboursA = cacheA;
        // current neighbour of A
        long nodeCfromA = NOT_FOUND;
        // iterates over neighbours of B
        CURSOR neighboursB = cacheB;
        // current neighbour of B
        long nodeCfromB;

        // last node where Ca = Cb
        // prevents counting a new triangle for parallel relationships
        long triangleC;

        // for all neighbors of A
        while (neighboursAMain.hasNextVLong()) {
            // we have not yet seen a triangle
            triangleC = NOT_FOUND;
            // check the second node's degree
            if (degreeFilter.test(nodeB)) {
                neighboursB = cursor(nodeB, neighboursB);
                // find first neighbour Cb of B with id > B
                nodeCfromB = neighboursB.skipUntil(nodeB);

                // if B had no neighbors, find a new B
                if (nodeCfromB != NOT_FOUND) {
                    // copy the state of A's cursor
                    neighboursA.copyFrom(neighboursAMain);

                    if (degreeFilter.test(nodeCfromB)) {
                        // find the first neighbour Ca of A with id >= Cb
                        nodeCfromA = neighboursA.advance(nodeCfromB);
                        triangleC = checkForAndEmitTriangle(consumer, nodeA, nodeB, nodeCfromA, nodeCfromB, triangleC);
                    }

                    // while both A and B have more neighbours
                    while (neighboursA.hasNextVLong() && neighboursB.hasNextVLong()) {
                        // take the next neighbour Cb of B
                        nodeCfromB = neighboursB.nextVLong();
                        if (degreeFilter.test(nodeCfromB)) {
                            if (nodeCfromB > nodeCfromA) {
                                // if Cb > Ca, take the next neighbour Ca of A with id >= Cb
                                nodeCfromA = neighboursA.advance(nodeCfromB);
                            }
                            triangleC = checkForAndEmitTriangle(
                                consumer,
                                nodeA,
                                nodeB,
                                nodeCfromA,
                                nodeCfromB,
                                triangleC
                            );
                        }
                    }

                    // it is possible that the last Ca > Cb, but there are no more neighbours Ca of A
                    // so if there are more neighbours Cb of B
                    if (neighboursB.hasNextVLong()) {
                        // we take the next neighbour Cb of B with id >= Ca
                        nodeCfromB = neighboursB.advance(nodeCfromA);
                        if (degreeFilter.test(nodeCfromB)) {
                            checkForAndEmitTriangle(consumer, nodeA, nodeB, nodeCfromA, nodeCfromB, triangleC);
                        }
                    }
                }
            }

            // skip until the next neighbour B of A with id > (current) B
            nodeB = neighboursAMain.skipUntil(nodeB);
        }
    }

    private long checkForAndEmitTriangle(
        IntersectionConsumer consumer,
        long nodeA,
        long nodeB,
        long nodeCa,
        long nodeCb,
        long triangleC
    ) {
        // if Ca = Cb there exists a triangle
        // if Ca = triangleC we have already counted it
        if (nodeCa == nodeCb && nodeCa > triangleC) {
            consumer.accept(nodeA, nodeB, nodeCa);
            return nodeCa;
        }
        return triangleC;
    }

    protected CURSOR cursor(long node, CURSOR reuse) {
        reuse.init(node);
        return reuse;
    }

    protected abstract int degree(long node);
}
