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
import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.Intersections;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;
import org.neo4j.graphdb.Direction;

import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Jaccard extends Algorithm<Jaccard> {

    private final Graph graph;

    public Jaccard(Graph graph) {

        this.graph = graph;
    }

    @Override
    public Jaccard me() {
        return this;
    }

    @Override
    public void release() {
        graph.release();
    }

    /**
     * Requires:
     * - Input graph must be bipartite:
     *       (:Person)-[:LIKES]->(:Thing)
     *       We collect all targets and use them only in the vectors, not as the thing for which we compute similarity.
     *       If (:Person)-[:LIKES]->(:Person) we would filter out the person nodes.
     *
     * Number of results: (n^2 - n) / 2
     */
    Stream<SimilarityResult> run(Direction direction) {
        if (direction == Direction.BOTH) {
            throw new IllegalArgumentException("Direction BOTH is not supported by the Jaccard algorithm.");
        }

        BitSet nodeFilter = new BitSet(graph.nodeCount());

        graph.forEachNode(node -> {
            if (graph.degree(node, direction) > 0L) {
                nodeFilter.set(node);
            }
            return true;
        });

        HugeObjectArray<long[]> vectors = HugeObjectArray.newArray(
                long[].class,
                graph.nodeCount(),
                AllocationTracker.EMPTY);

        graph.forEachNode(node -> {
            if (nodeFilter.get(node)) {
                long[] targetIds = new long[graph.degree(node, direction)];
                final int[] counter = {0};
                graph.forEachRelationship(node, direction, (source, target) -> {
                    targetIds[counter[0]++] = target;
                    return true;
                });
                vectors.set(node, targetIds);
            }
            return true;
        });

        return LongStream.range(0, graph.nodeCount())
                .filter(nodeFilter::get)
                .boxed()
                .flatMap(n1 -> LongStream.range(n1 + 1, graph.nodeCount())
                        .filter(nodeFilter::get)
                        .mapToObj(n2 -> {
                            System.out.println("n1 = " + n1 + " n2 = " + n2);
                                    long[] v1 = vectors.get(n1);
                                    long[] v2 = vectors.get(n2);
                                    // TODO: Assumes that the targets are sorted, need to check
                                    long intersection = Intersections.intersection3(v1, v2);
                                    double union = v1.length + v2.length - intersection;
                                    return new SimilarityResult(
                                            graph.toOriginalNodeId(n1),
                                            graph.toOriginalNodeId(n2),
                                            union == 0 ? 0 : intersection / union
                                    );
                                }
                        )
                );
    }

}
