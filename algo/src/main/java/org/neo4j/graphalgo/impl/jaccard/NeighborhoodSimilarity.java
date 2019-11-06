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

import com.carrotsearch.hppc.ArraySizingStrategy;
import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.LongArrayList;
import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.Intersections;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;
import org.neo4j.graphdb.Direction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class NeighborhoodSimilarity extends Algorithm<NeighborhoodSimilarity> {

    private final Graph graph;
    private final Config config;

    private final ExecutorService executorService;
    private final AllocationTracker tracker;
    private final Log log;

    private final BitSet nodeFilter;

    private HugeObjectArray<long[]> vectors;

    public NeighborhoodSimilarity(
            Graph graph,
            Config config,
            ExecutorService executorService,
            AllocationTracker tracker,
            Log log) {
        this.graph = graph;
        this.config = config;
        this.executorService = executorService;
        this.tracker = tracker;
        this.log = log;
        this.nodeFilter = new BitSet(graph.nodeCount());
    }

    @Override
    public NeighborhoodSimilarity me() {
        return this;
    }

    @Override
    public void release() {
        graph.release();
    }

    private static final ArraySizingStrategy ARRAY_SIZING_STRATEGY =
        (currentBufferLength, elementsCount, expectedAdditions) -> expectedAdditions + elementsCount;

    /**
     * Requires:
     * - Input graph must be bipartite:
     * (:Person)-[:LIKES]->(:Thing)
     * We collect all targets and use them only in the vectors, not as the thing for which we compute similarity.
     * If (:Person)-[:LIKES]->(:Person) we would filter out the person nodes.
     *
     * Number of results: (n^2 - n) / 2
     */
    public Stream<SimilarityResult> run(Direction direction) {

        this.vectors = HugeObjectArray.newArray(long[].class, graph.nodeCount(), tracker);

        if (direction == Direction.BOTH) {
            throw new IllegalArgumentException(
                "Direction BOTH is not supported by the NeighborhoodSimilarity algorithm.");
        }

        graph.forEachNode(node -> {
            if (graph.degree(node, direction) > 0L) {
                nodeFilter.set(node);
            }
            return true;
        });

        graph.forEachNode(node -> {
            if (nodeFilter.get(node)) {
                int degree = graph.degree(node, direction);
                final LongArrayList targetIds = new LongArrayList(degree, ARRAY_SIZING_STRATEGY);
                graph.forEachRelationship(node, direction, (source, target) -> {
                    targetIds.add(target);
                    return true;
                });
                vectors.set(node, targetIds.buffer);
            }
            return true;
        });

        Stream<SimilarityResult> similarityResultStream;

        if (config.topk > 0) {
            similarityResultStream = similarityTopKComputation();
        } else {
            similarityResultStream = similarityComputation();
        }

        if (config.top > 0) {
            similarityResultStream = topN(similarityResultStream);
        }

        // TODO: step d (writing)

        return similarityResultStream;
    }

    private SimilarityResult jaccard(long n1, long n2, long[] v1, long[] v2) {
        long intersection = Intersections.intersection3(v1, v2);
        double union = v1.length + v2.length - intersection;
        double similarity = union == 0 ? 0 : intersection / union;
        return new SimilarityResult(n1, n2, similarity);
    }

    private Stream<SimilarityResult> similarityComputation() {
        return LongStream.range(0, graph.nodeCount())
            .filter(nodeFilter::get)
            .boxed()
            .flatMap(n1 -> {
                long[] v1 = vectors.get(n1);
                return LongStream.range(n1 + 1, graph.nodeCount())
                    .filter(nodeFilter::get)
                    .mapToObj(n2 -> jaccard(n1, n2, v1, vectors.get(n2)));
            });
    }

    private Stream<SimilarityResult> similarityTopKComputation() {
        // TODO replace this with an efficient data structure
        Map<Long, List<SimilarityResult>> result = new HashMap<>();

        LongStream.range(0, graph.nodeCount())
            .filter(nodeFilter::get)
            .forEach(n1 -> {
                long[] v1 = vectors.get(n1);
                LongStream.range(n1 + 1, graph.nodeCount())
                    .filter(nodeFilter::get)
                    .mapToObj(n2 -> jaccard(n1, n2, v1, vectors.get(n2)))
                    .forEach(similarity ->
                        result.compute(n1, (node, topkSims) -> {
                            if (topkSims == null) {
                                topkSims = new ArrayList<>();
                            }
                            if (topkSims.size() < config.topk) {
                                topkSims.add(similarity);
                            } else {
                                Optional<SimilarityResult> maybeSmallest = topkSims
                                    .stream()
                                    .filter(sim -> sim.similarity < similarity.similarity)
                                    .min(Comparator.comparingDouble(o -> o.similarity));

                                if (maybeSmallest.isPresent()) {
                                    topkSims.remove(maybeSmallest.get());
                                    topkSims.add(similarity);
                                }
                            }
                            return topkSims;
                        }));
            });

        return result.values().stream().flatMap(Collection::stream);
    }

    private Stream<SimilarityResult> topN(Stream<SimilarityResult> similarities) {
        Comparator<SimilarityResult> comparator = config.top > 0 ? SimilarityResult.DESCENDING : SimilarityResult.ASCENDING;
        return similarities.sorted(comparator).limit(config.top);
    }

    public static final class Config {
        public static final Config DEFAULT = new NeighborhoodSimilarity.Config(
                0.0,
                0,
                0,
                0,
                Pools.DEFAULT_CONCURRENCY,
                ParallelUtil.DEFAULT_BATCH_SIZE);

        private final double similarityCutoff;
        private final double degreeCutoff;

        private final int top;
        private final int topk;

        private final int concurrency;
        private final int minBatchSize;

        public Config(
                double similarityCutoff,
                int degreeCutoff,
                int top,
                int topk,
                int concurrency,
                int minBatchSize) {
            this.similarityCutoff = similarityCutoff;
            this.degreeCutoff = degreeCutoff;
            this.top = top;
            this.topk = topk;
            this.concurrency = concurrency;
            this.minBatchSize = minBatchSize;
        }
    }

}
