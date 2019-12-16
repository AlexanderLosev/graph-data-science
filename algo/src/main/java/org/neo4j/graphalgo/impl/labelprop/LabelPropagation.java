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
package org.neo4j.graphalgo.impl.labelprop;

import com.carrotsearch.hppc.LongDoubleScatterMap;
import org.neo4j.collection.primitive.PrimitiveLongCollections;
import org.neo4j.collection.primitive.PrimitiveLongIterable;
import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.loading.NullPropertyMap;
import org.neo4j.graphalgo.core.utils.LazyBatchCollection;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.mem.MemoryUsage;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.graphalgo.labelpropagation.LabelPropagationConfigBase;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.StatementConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfDoubleArray;
import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfLongArray;

public class LabelPropagation extends Algorithm<LabelPropagation, LabelPropagation> {

    public static final double DEFAULT_WEIGHT = 1.0;

    private final long nodeCount;
    private final AllocationTracker tracker;
    private final NodeProperties nodeProperties;
    private final NodeProperties nodeWeights;
    private final LabelPropagationConfigBase config;
    private final ExecutorService executor;

    private Graph graph;
    private HugeLongArray labels;
    private final long maxLabelId;
    private long ranIterations;
    private boolean didConverge;
    private int batchSize;

    public LabelPropagation(
        Graph graph,
        LabelPropagationConfigBase config,
        ExecutorService executor,
        AllocationTracker tracker
    ) {
        this.graph = graph;
        this.nodeCount = graph.nodeCount();
        this.config = config;
        this.executor = executor;
        this.tracker = tracker;
        this.batchSize = ParallelUtil.DEFAULT_BATCH_SIZE;

        NodeProperties seedProperty = graph.nodeProperties(config.seedProperty());
        if (seedProperty == null) {
            seedProperty = new NullPropertyMap(0.0);
        }
        this.nodeProperties = seedProperty;

        NodeProperties weightProperty = graph.nodeProperties(config.weightProperty());
        if (weightProperty == null) {
            weightProperty = new NullPropertyMap(1.0);
        }
        this.nodeWeights = weightProperty;
        maxLabelId = nodeProperties.getMaxPropertyValue().orElse(StatementConstants.NO_SUCH_LABEL);
    }

    @Override
    public LabelPropagation me() {
        return this;
    }

    @Override
    public void release() {
        graph = null;
    }

    public long ranIterations() {
        return ranIterations;
    }

    public boolean didConverge() {
        return didConverge;
    }

    public HugeLongArray labels() {
        return labels;
    }

    @Override
    public LabelPropagation compute() {
        if (config.maxIterations() <= 0L) {
            throw new IllegalArgumentException("Must iterate at least 1 time");
        }

        if (labels == null || labels.size() != nodeCount) {
            labels = HugeLongArray.newArray(nodeCount, tracker);
        }

        ranIterations = 0L;
        didConverge = false;

        List<StepRunner> stepRunners = stepRunners(config.direction());

        long currentIteration = 0L;
        while (currentIteration < config.maxIterations()) {
            ParallelUtil.runWithConcurrency(config.concurrency(), stepRunners, 1L, MICROSECONDS, terminationFlag, executor);
            ++currentIteration;
        }

        long maxIteration = 0L;
        boolean converged = true;
        for (StepRunner stepRunner : stepRunners) {
            Step current = stepRunner.current;
            if (current instanceof ComputeStep) {
                ComputeStep step = (ComputeStep) current;
                if (step.iteration > maxIteration) {
                    maxIteration = step.iteration;
                }
                converged = converged && !step.didChange;
                step.release();
            }
        }

        ranIterations = maxIteration;
        didConverge = converged;

        return me();
    }

    private List<StepRunner> stepRunners(Direction direction) {
        long nodeCount = graph.nodeCount();
        long batchSize = ParallelUtil.adjustedBatchSize(nodeCount, this.batchSize);

        Collection<PrimitiveLongIterable> nodeBatches = LazyBatchCollection.of(
            nodeCount,
            batchSize,
            (start, length) -> () -> PrimitiveLongCollections.range(start, start + length - 1L)
        );

        int threads = nodeBatches.size();
        List<StepRunner> tasks = new ArrayList<>(threads);
        for (PrimitiveLongIterable iter : nodeBatches) {
            InitStep initStep = new InitStep(
                graph,
                nodeProperties,
                nodeWeights,
                iter,
                labels,
                getProgressLogger(),
                direction,
                maxLabelId
            );
            StepRunner task = new StepRunner(initStep);
            tasks.add(task);
        }
        ParallelUtil.runWithConcurrency(config.concurrency(), tasks, 1, MICROSECONDS, terminationFlag, executor);
        return tasks;
    }

    public static MemoryEstimation memoryEstimation() {
        return MemoryEstimations.builder(LabelPropagation.class)
            .perNode("labels", HugeLongArray::memoryEstimation)
            .perThread("votes", MemoryEstimations.builder()
                .field("init step", InitStep.class)
                .field("compute step", ComputeStep.class)
                .field("step runner", StepRunner.class)
                .field("compute step consumer", ComputeStepConsumer.class)
                .field("votes container", LongDoubleScatterMap.class)
                .rangePerNode("votes", nodeCount -> {
                    long minBufferSize = MemoryUsage.sizeOfEmptyOpenHashContainer();
                    long maxBufferSize = MemoryUsage.sizeOfOpenHashContainer(nodeCount);
                    if (maxBufferSize < minBufferSize) {
                        maxBufferSize = minBufferSize;
                    }
                    long min = sizeOfLongArray(minBufferSize) + sizeOfDoubleArray(minBufferSize);
                    long max = sizeOfLongArray(maxBufferSize) + sizeOfDoubleArray(maxBufferSize);
                    return MemoryRange.of(min, max);
                }).build())
            .build();
    }

    void withBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
