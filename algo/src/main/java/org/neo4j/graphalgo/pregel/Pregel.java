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
package org.neo4j.graphalgo.pregel;

import com.carrotsearch.hppc.BitSet;
import org.jctools.queues.MpscLinkedQueue;
import org.neo4j.collection.primitive.PrimitiveLongIterable;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphalgo.api.Degrees;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.HugeNodeWeights;
import org.neo4j.graphalgo.api.RelationshipIterator;
import org.neo4j.graphalgo.core.huge.loader.HugeNodePropertiesBuilder;
import org.neo4j.graphalgo.core.utils.LazyMappingCollection;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeDoubleArray;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.LongStream;

public final class Pregel {

    // Marks the end of messages from the previous iteration
    private static final Double TERMINATION_SYMBOL = Double.NaN;

    private final Graph graph;

    private final HugeDoubleArray nodeValues;
    private final MpscLinkedQueue<Double>[] messageQueues;

    private final Supplier<Computation> computationFactory;
    private final int batchSize;
    private final int concurrency;
    private final ExecutorService executor;
    private final AllocationTracker tracker;
    private final ProgressLogger progressLogger;

    private int iterations;

    public static Pregel withDefaultNodeValues(
            final Graph graph,
            final Supplier<Computation> computationFactory,
            final int batchSize,
            final int concurrency,
            final ExecutorService executor,
            final AllocationTracker tracker,
            final ProgressLogger progressLogger) {

        final HugeNodeWeights nodeValues = HugeNodePropertiesBuilder
                .of(graph.nodeCount(), tracker, computationFactory.get().getDefaultNodeValue(), 0)
                .build();

        return new Pregel(
                graph,
                computationFactory,
                nodeValues,
                batchSize,
                concurrency,
                executor,
                tracker,
                progressLogger);
    }

    public static Pregel withInitialNodeValues(
            final Graph graph,
            final Supplier<Computation> computationFactory,
            final HugeNodeWeights nodeValues,
            final int batchSize,
            final int concurrency,
            final ExecutorService executor,
            final AllocationTracker tracker,
            final ProgressLogger progressLogger) {

        return new Pregel(
                graph,
                computationFactory,
                nodeValues,
                batchSize,
                concurrency,
                executor,
                tracker,
                progressLogger);
    }

    private Pregel(
            final Graph graph,
            final Supplier<Computation> computationFactory,
            final HugeNodeWeights nodeProperties,
            final int batchSize,
            final int concurrency,
            final ExecutorService executor,
            final AllocationTracker tracker,
            final ProgressLogger progressLogger) {
        this.graph = graph;
        this.computationFactory = computationFactory;
        this.tracker = tracker;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
        this.executor = executor;
        this.progressLogger = progressLogger;

        this.nodeValues = HugeDoubleArray.newArray(graph.nodeCount(), tracker);
        ParallelUtil.parallelStreamConsume(
                LongStream.range(0, graph.nodeCount()),
                nodeIds -> nodeIds.forEach(nodeId -> nodeValues.set(nodeId, nodeProperties.nodeWeight(nodeId)))
        );

        this.messageQueues = new MpscLinkedQueue[(int) graph.nodeCount()];
        ParallelUtil.parallelStreamConsume(
                LongStream.range(0, graph.nodeCount()),
                nodeIds -> nodeIds.forEach(nodeId -> messageQueues[(int) nodeId] = MpscLinkedQueue.newMpscLinkedQueue()));
    }

    public HugeDoubleArray run(final int maxIterations) {
        iterations = 0;

        boolean canHalt = false;

        // Tracks if a node received messages in the previous iteration
        BitSet receiverBits = new BitSet(graph.nodeCount());
        BitSet voteBits = new BitSet(graph.nodeCount());

        while (iterations < maxIterations && !canHalt) {
            int iteration = iterations++;

            final List<ComputeStep> computeSteps = runComputeSteps(iteration, receiverBits, voteBits);

            receiverBits = unionBitSets(computeSteps, ComputeStep::getSenders);
            voteBits = unionBitSets(computeSteps, ComputeStep::getVotes);

            // No messages have been sent
            if (receiverBits.nextSetBit(0) == -1) {
                canHalt = true;
            }
        }
        return nodeValues;
    }

    public int getIterations() {
        return iterations;
    }

    private BitSet unionBitSets(List<ComputeStep> computeSteps, Function<ComputeStep, BitSet> fn) {
        BitSet target = fn.apply(computeSteps.get(0));
        for (int i = 1; i < computeSteps.size(); i++) {
            target.union(fn.apply(computeSteps.get(i)));
        }
        return target;
    }

    private List<ComputeStep> runComputeSteps(
            final int iteration,
            BitSet messageBits,
            BitSet voteToHaltBits) {
        Collection<PrimitiveLongIterable> iterables = graph.batchIterables(batchSize);

        int threadCount = iterables.size();

        final List<ComputeStep> tasks = new ArrayList<>(threadCount);

        if (computationFactory.get().isSynchronous()) {
            // Synchronization barrier:
            // Add termination flag to message queues that
            // received messages in the previous iteration.
            if (iteration > 0) {
                ParallelUtil.parallelStreamConsume(
                        LongStream.range(0, graph.nodeCount()),
                        nodeIds -> nodeIds.forEach(nodeId -> {
                            if (messageBits.get(nodeId)) messageQueues[(int) nodeId].add(TERMINATION_SYMBOL);
                        }));
            }
        }

        Collection<ComputeStep> computeSteps = LazyMappingCollection.of(
                iterables,
                nodeIterable -> {
                    ComputeStep task = new ComputeStep(
                            computationFactory.get(),
                            graph.nodeCount(),
                            iteration,
                            nodeIterable,
                            graph,
                            nodeValues,
                            messageBits,
                            voteToHaltBits,
                            messageQueues,
                            graph);
                    tasks.add(task);
                    return task;
                });

        ParallelUtil.runWithConcurrency(concurrency, computeSteps, executor);
        return tasks;
    }

    public static final class ComputeStep implements Runnable {

        private final int iteration;
        private final Computation computation;
        private final BitSet senderBits;
        private final BitSet receiverBits;
        private final BitSet voteBits;
        private final PrimitiveLongIterable nodes;
        private final Degrees degrees;
        private final HugeDoubleArray nodeProperties;
        private final MpscLinkedQueue<Double>[] messageQueues;
        private final RelationshipIterator relationshipIterator;

        private ComputeStep(
                final Computation computation,
                final long globalNodeCount,
                final int iteration,
                final PrimitiveLongIterable nodes,
                final Degrees degrees,
                final HugeDoubleArray nodeProperties,
                final BitSet receiverBits,
                final BitSet voteBits,
                final MpscLinkedQueue<Double>[] messageQueues,
                final RelationshipIterator relationshipIterator) {
            this.iteration = iteration;
            this.computation = computation;
            this.senderBits = new BitSet(globalNodeCount);
            this.receiverBits = receiverBits;
            this.voteBits = voteBits;
            this.nodes = nodes;
            this.degrees = degrees;
            this.nodeProperties = nodeProperties;
            this.messageQueues = messageQueues;
            this.relationshipIterator = relationshipIterator.concurrentCopy();

            computation.setComputeStep(this);
        }

        @Override
        public void run() {
            final PrimitiveLongIterator nodesIterator = nodes.iterator();

            while (nodesIterator.hasNext()) {
                final long nodeId = nodesIterator.next();

                if (receiverBits.get(nodeId) || !voteBits.get(nodeId)) {
                    voteBits.clear(nodeId);
                    computation.compute(nodeId, receiveMessages(nodeId));
                }
            }
        }

        BitSet getSenders() {
            return senderBits;
        }

        BitSet getVotes() {
            return voteBits;
        }

        public int getIteration() {
            return iteration;
        }

        int getDegree(final long nodeId, Direction direction) {
            return degrees.degree(nodeId, direction);
        }

        double getNodeValue(final long nodeId) {
            return nodeProperties.get(nodeId);
        }

        void setNodeValue(final long nodeId, final double value) {
            nodeProperties.set(nodeId, value);
        }

        void voteToHalt(long nodeId) {
            voteBits.set(nodeId);
        }

        void sendMessages(final long nodeId, final double message, Direction direction) {
            relationshipIterator.forEachRelationship(nodeId, direction, (sourceNodeId, targetNodeId) -> {
                messageQueues[(int) targetNodeId].add(message);
                senderBits.set(targetNodeId);
                return true;
            });
        }

        private MpscLinkedQueue<Double> receiveMessages(final long nodeId) {
            return receiverBits.get(nodeId) ? messageQueues[(int) nodeId] : null;
        }
    }
}
