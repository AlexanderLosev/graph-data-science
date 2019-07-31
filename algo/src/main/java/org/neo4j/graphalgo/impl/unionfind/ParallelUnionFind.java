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
package org.neo4j.graphalgo.impl.unionfind;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.HugeWeightMapping;
import org.neo4j.graphalgo.api.RelationshipConsumer;
import org.neo4j.graphalgo.api.RelationshipIterator;
import org.neo4j.graphalgo.api.WeightedRelationshipConsumer;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.dss.HugeAtomicDisjointSetStruct;
import org.neo4j.graphalgo.core.utils.paged.dss.DisjointSetStruct;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

/**
 * Parallel Union-Find Algorithm based on the
 * "Wait-free Parallel Algorithms for the Union-Find Problem" paper.
 * @see HugeAtomicDisjointSetStruct
 * @see <a href="http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.56.8354&rep=rep1&type=pdf">the paper</a>
 */
public class ParallelUnionFind extends UnionFind<ParallelUnionFind> {

    private final ExecutorService executor;
    private final AllocationTracker tracker;
    private final long nodeCount;
    private final long batchSize;
    private final int threadSize;

    public static MemoryEstimation memoryEstimation(boolean incremental) {
        return MemoryEstimations
            .builder(ParallelUnionFind.class)
            .add("dss", HugeAtomicDisjointSetStruct.memoryEstimation(incremental))
            .build();
    }

    public ParallelUnionFind(
            Graph graph,
            ExecutorService executor,
            int minBatchSize,
            int concurrency,
            UnionFind.Config algoConfig,
            AllocationTracker tracker) {
        super(graph, algoConfig);
        this.executor = executor;
        this.tracker = tracker;
        this.nodeCount = graph.nodeCount();
        this.batchSize = ParallelUtil.adjustBatchSize(
                nodeCount,
                concurrency,
                minBatchSize,
                Integer.MAX_VALUE);
        long threadSize = ParallelUtil.threadSize(batchSize, nodeCount);
        if (threadSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(String.format(
                    "Too many nodes (%d) to run union find with the given concurrency (%d) and batchSize (%d)",
                    nodeCount,
                    concurrency,
                    batchSize));
        }
        this.threadSize = (int) threadSize;
    }

    @Override
    public DisjointSetStruct computeUnrestricted() {
        return compute(Double.NaN);
    }

    @Override
    public DisjointSetStruct compute(double threshold) {
        long nodeCount = graph.nodeCount();
        HugeWeightMapping communityMap = algoConfig.communityMap;
        DisjointSetStruct dss = communityMap == null
                ? new HugeAtomicDisjointSetStruct(nodeCount, tracker)
                : new HugeAtomicDisjointSetStruct(nodeCount, communityMap, tracker);

        final Collection<Runnable> tasks = new ArrayList<>(threadSize);
        for (long i = 0L; i < this.nodeCount; i += batchSize) {
            UnionFindTask unionFindTask = Double.isNaN(threshold)
                    ? new UnionFindTask(dss, i)
                    : new UnionFindWithThresholdTask(threshold, dss, i);
            tasks.add(unionFindTask);
        }
        ParallelUtil.run(tasks, executor);
        return dss;
    }

    private class UnionFindTask implements Runnable, RelationshipConsumer {

        final DisjointSetStruct struct;
        final RelationshipIterator rels;
        private final long offset;
        private final long end;

        UnionFindTask(
                DisjointSetStruct struct,
                long offset) {
            this.struct = struct;
            this.rels = graph.concurrentCopy();
            this.offset = offset;
            this.end = Math.min(offset + batchSize, nodeCount);
        }

        @Override
        public void run() {
            for (long node = offset; node < end; node++) {
                compute(node);
            }
            getProgressLogger().logProgress((end - 1.0) / (nodeCount - 1.0));
        }

        void compute(final long node) {
            rels.forEachRelationship(node, Direction.OUTGOING, this);
        }

        @Override
        public boolean accept(final long sourceNodeId, final long targetNodeId) {
            struct.union(sourceNodeId, targetNodeId);
            return true;
        }
    }

    private class UnionFindWithThresholdTask extends UnionFindTask implements WeightedRelationshipConsumer {

        private final double threshold;

        UnionFindWithThresholdTask(
                double threshold,
                DisjointSetStruct struct,
                long offset) {
            super(struct, offset);
            this.threshold = threshold;
        }

        @Override
        void compute(final long node) {
            rels.forEachRelationship(node, Direction.OUTGOING, (WeightedRelationshipConsumer) this);
        }

        @Override
        public boolean accept(final long sourceNodeId, final long targetNodeId, final double weight) {
            if (weight > threshold) {
                struct.union(sourceNodeId, targetNodeId);
            }
            return true;
        }
    }
}
