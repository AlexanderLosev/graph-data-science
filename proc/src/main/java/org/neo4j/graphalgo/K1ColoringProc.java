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

package org.neo4j.graphalgo;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.graphalgo.core.write.Exporter;
import org.neo4j.graphalgo.impl.coloring.K1Coloring;
import org.neo4j.graphalgo.impl.coloring.K1ColoringFactory;
import org.neo4j.graphalgo.impl.results.AbstractCommunityResultBuilder;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class K1ColoringProc extends BaseAlgoProc<K1Coloring> {

    public static final String COLOR_COUNT_FIELD_NAME = "colorCount";
    @Context
    public GraphDatabaseAPI dbAPI;

    @Context
    public Log log;

    @Context
    public KernelTransaction transaction;

    @Procedure(name = "algo.beta.k1coloring", mode = Mode.WRITE)
    @Description("CALL algo.beta.k1coloring(" +
                 "label:String, relationship:String, " +
                 "{iterations: 10, direction: 'OUTGOING', write: true, concurrency: 4}) " +
                 "YIELD nodes, ranIterations, didConverge, loadMillis, computeMillis, writeMillis, write, colorCount")
    public Stream<WriteResult> betaK1Coloring(
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationshipType,
        @Name(value = "config", defaultValue = "null") Map<String, Object> config
    ) {

        return run(label, relationshipType, config);
    }

    @Procedure(name = "algo.beta.k1coloring.stream", mode = READ)
    @Description("CALL algo.beta.k1coloring.stream(label:String, relationship:String, " +
                 "{iterations: 10, direction: 'OUTGOING', concurrency: 4}) " +
                 "YIELD nodeId, color")
    public Stream<StreamResult> betaK1ColoringStream(
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationshipType,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {

        return stream(label, relationshipType, config);
    }

    public Stream<WriteResult> run(String label, String relationshipType, Map<String, Object> config) {
        ProcedureSetup setup = setup(label, relationshipType, config);

        if (setup.graph.isEmpty()) {
            setup.graph.release();
            return Stream.of(WriteResult.EMPTY);
        }

        K1Coloring coloring = compute(setup);

        setup.builder.withCommunityFunction(coloring.colors()::get);

        if (callContext.outputFields().anyMatch((field) -> field.equals(COLOR_COUNT_FIELD_NAME))) {
            setup.builder.withColorCount(coloring.getColorMap().size());
        }
        setup.builder.withRanIterations(coloring.ranIterations());
        setup.builder.withDidConverge(coloring.didConverge());

        if (setup.procedureConfig.isWriteFlag()) {
            String writeProperty = setup.procedureConfig.getWriteProperty();
            setup.builder.withWrite(true);
            setup.builder.withWriteProperty(writeProperty);

            write(
                setup.builder::timeWrite,
                setup.graph,
                coloring.colors(),
                setup.procedureConfig,
                writeProperty,
                setup.tracker
            );

            setup.graph.releaseProperties();
        }

        return Stream.of(setup.builder.build());
    }

    public Stream<StreamResult> stream(
        String label,
        String relationship,
        Map<String, Object> config
    ) {

        ProcedureSetup setup = setup(label, relationship, config);

        if (setup.graph.isEmpty()) {
            setup.graph.release();
            return Stream.empty();
        }

        K1Coloring coloring = compute(setup);

        return LongStream.range(0, setup.graph.nodeCount())
            .mapToObj(nodeId -> {
                final long neoNodeId = setup.graph.toOriginalNodeId(nodeId);
                return new StreamResult(neoNodeId, coloring.colors().get(nodeId));
            });
    }

    @Override
    protected GraphLoader configureAlgoLoader(
        GraphLoader loader, ProcedureConfiguration config
    ) {
        return loader.withDirection(config.getDirection(Direction.OUTGOING));
    }

    @Override
    protected AlgorithmFactory<K1Coloring> algorithmFactory(ProcedureConfiguration config) {
        return new K1ColoringFactory();
    }

    private K1Coloring compute(final ProcedureSetup setup) {
        final K1Coloring k1Coloring = newAlgorithm(setup.graph, setup.procedureConfig, setup.tracker);
        K1Coloring algoResult = runWithExceptionLogging(
            K1Coloring.class.getSimpleName() + " failed",
            () -> setup.builder.timeEval(k1Coloring::compute)
        );

        log.info(K1Coloring.class.getSimpleName() + ": overall memory usage %s", setup.tracker.getUsageString());

        k1Coloring.release();
        setup.graph.releaseTopology();

        return algoResult;
    }

    private ProcedureSetup setup(
        String label,
        String relationship,
        Map<String, Object> config
    ) {
        AllocationTracker tracker = AllocationTracker.create();
        WriteResultBuilder builder = new WriteResultBuilder(callContext.outputFields(), tracker);

        ProcedureConfiguration configuration = newConfig(label, relationship, config);

        Graph graph = loadGraph(configuration, tracker, builder);

        return new ProcedureSetup(builder, graph, tracker, configuration);
    }

    private void write(
        Supplier<ProgressTimer> timer,
        Graph graph,
        HugeLongArray coloring,
        ProcedureConfiguration configuration,
        String writeProperty,
        AllocationTracker tracker
    ) {
        try (ProgressTimer ignored = timer.get()) {
            write(graph, coloring, configuration, writeProperty, tracker);
        }
    }

    private void write(
        Graph graph,
        HugeLongArray coloring,
        ProcedureConfiguration procedureConfiguration,
        String writeProperty,
        AllocationTracker tracker
    ) {
        log.debug("Writing results");

        Exporter exporter = Exporter.of(api, graph)
            .withLog(log)
            .parallel(
                Pools.DEFAULT,
                procedureConfiguration.getWriteConcurrency(),
                TerminationFlag.wrap(transaction)
            )
            .build();
        exporter.write(
            writeProperty,
            coloring,
            HugeLongArray.Translator.INSTANCE
        );
    }

    public static class ProcedureSetup {
        final WriteResultBuilder builder;
        final Graph graph;
        final AllocationTracker tracker;
        final ProcedureConfiguration procedureConfig;

        ProcedureSetup(
            final WriteResultBuilder builder,
            final Graph graph,
            final AllocationTracker tracker,
            final ProcedureConfiguration procedureConfig
        ) {
            this.builder = builder;
            this.graph = graph;
            this.tracker = tracker;
            this.procedureConfig = procedureConfig;
        }
    }

    public static class WriteResult {

        public static final WriteResult EMPTY = new WriteResult(
            0,
            0,
            0,
            0,
            0,
            0,
            false,
            false
        );

        public final long loadMillis;
        public final long computeMillis;
        public final long writeMillis;

        public final long nodes;
        public final long colorCount;
        public final long ranIterations;
        public final boolean didConverge;

        public final boolean write;

        public WriteResult(
            long loadMillis,
            long computeMillis,
            long writeMillis,
            long nodes,
            long colorCount,
            long ranIterations,
            boolean write,
            boolean didConverge
        ) {
            this.loadMillis = loadMillis;
            this.computeMillis = computeMillis;
            this.writeMillis = writeMillis;
            this.nodes = nodes;
            this.colorCount = colorCount;
            this.ranIterations = ranIterations;
            this.write = write;
            this.didConverge = didConverge;
        }
    }

    public static class WriteResultBuilder extends AbstractCommunityResultBuilder<WriteResult> {

        private long colorCount = -1L;
        private long ranIterations;
        private boolean didConverge;

        WriteResultBuilder(Stream<String> returnFields, AllocationTracker tracker) {
            super(returnFields, tracker);
        }

        public WriteResultBuilder withColorCount(long colorCount) {
            this.colorCount = colorCount;
            return this;
        }

        public WriteResultBuilder withRanIterations(long ranIterations) {
            this.ranIterations = ranIterations;
            return this;
        }

        public WriteResultBuilder withDidConverge(boolean didConverge) {
            this.didConverge = didConverge;
            return this;
        }

        @Override
        protected WriteResult buildResult() {
            return new WriteResult(
                loadMillis,
                computeMillis,
                writeMillis,
                nodeCount,
                colorCount,
                ranIterations,
                write,
                didConverge
            );
        }
    }

    public static class StreamResult {
        public final long nodeId;
        public final long color;

        public StreamResult(long nodeId, long color) {
            this.nodeId = nodeId;
            this.color = color;
        }
    }
}
