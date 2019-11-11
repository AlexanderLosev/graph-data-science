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

import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;
import org.neo4j.graphalgo.core.utils.queue.LongPriorityQueue;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TopKMap {

    static MemoryEstimation memoryEstimation(long items, int topk) {
        return MemoryEstimations.builder(TopKMap.class)
            .add("topk lists",
                MemoryEstimations.builder("topk lists", TopKList.class)
                    .add("queues", LongPriorityQueue.memoryEstimation(topk))
                    .build()
                    .times(items)
            )
            .build();
    }

    private final HugeObjectArray<TopKList> topKLists;

    TopKMap(long items, int topK, Comparator<SimilarityResult> comparator, AllocationTracker tracker) {
        int boundedTopK = (int) Math.min(topK, items);
        topKLists = HugeObjectArray.newArray(TopKList.class, items, tracker);
        topKLists.setAll(node1 -> new TopKList(
            comparator.equals(SimilarityResult.ASCENDING)
                ? TopKLongPriorityQueue.min(boundedTopK)
                : TopKLongPriorityQueue.max(boundedTopK)
            )
        );
    }

    public void accept(long node1, long node2, double similarity) {
        topKLists.get(node1).accept(node2, similarity);
    }

    public Stream<SimilarityResult> stream() {
        return LongStream.range(0, topKLists.size())
            .boxed()
            .flatMap(node1 -> topKLists.get(node1).stream(node1));
    }

    public static final class TopKList {

        private final TopKLongPriorityQueue queue;

        TopKList(TopKLongPriorityQueue queue) {
            this.queue = queue;
        }

        void accept(long node2, double similarity) {
            queue.offer(node2, similarity);
        }

        Stream<SimilarityResult> stream(long node1) {

            Iterable<SimilarityResult> iterable = () -> new Iterator<SimilarityResult>() {

                PrimitiveIterator.OfLong elementsIter = queue.elements().iterator();
                PrimitiveIterator.OfDouble prioritiesIter = queue.priorities().iterator();

                @Override
                public boolean hasNext() {
                    return elementsIter.hasNext() && prioritiesIter.hasNext();
                }

                @Override
                public SimilarityResult next() {
                    return new SimilarityResult(node1, elementsIter.nextLong(), prioritiesIter.nextDouble());
                }
            };

            return StreamSupport.stream(iterable.spliterator(), false);
        }
    }
}

