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
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.graphalgo.core.write.NodePropertyExporter;
import org.neo4j.graphalgo.core.write.Translators;
import org.neo4j.graphalgo.impl.scc.ForwardBackwardScc;
import org.neo4j.graphalgo.impl.scc.SCCAlgorithm;
import org.neo4j.graphalgo.impl.scc.SCCTunedTarjan;
import org.neo4j.graphalgo.results.SCCResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class StronglyConnectedComponentsProc extends LabsProc {

    public static final String CONFIG_WRITE_PROPERTY = "writeProperty";
    public static final String CONFIG_OLD_WRITE_PROPERTY = "partitionProperty";
    public static final String CONFIG_CLUSTER = "partition";

    // default algo.scc -> iterative tarjan
    @Procedure(value = "algo.scc", mode = Mode.WRITE)
    @Description("CALL algo.scc(label:String, relationship:String, config:Map<String, Object>) YIELD " +
                 "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> sccDefaultMethod(
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationship,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {

        return sccIterativeTarjan(label, relationship, config);
    }

    // default algo.scc -> iter tarjan
    @Procedure(value = "algo.scc.stream", mode = READ)
    @Description("CALL algo.scc.stream(label:String, relationship:String, config:Map<String, Object>) YIELD " +
                 "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCAlgorithm.StreamResult> sccDefaultMethodStream(
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationship,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {

        return sccIterativeTarjanStream(label, relationship, config);
    }

    // algo.scc.tunedTarjan
    @Procedure(value = "algo.scc.recursive.tunedTarjan", mode = Mode.WRITE)
    @Description("CALL algo.scc.recursive.tunedTarjan(label:String, relationship:String, config:Map<String, Object>) YIELD " +
                 "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> sccTunedTarjan(
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationship,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config, getUsername());

        AllocationTracker tracker = AllocationTracker.create();
        SCCResult.Builder builder = new SCCResult.Builder(true, true, tracker);

        ProgressTimer loadTimer = builder.timeLoad();
        Graph graph = new GraphLoader(api, Pools.DEFAULT)
            .init(log, label, relationship, configuration)
            .withAllocationTracker(tracker)
            .withDirection(Direction.OUTGOING)
            .load(configuration.getGraphImpl());
        loadTimer.stop();

        if (graph.isEmpty()) {
            return Stream.of(SCCResult.EMPTY);
        }

        SCCTunedTarjan tarjan = new SCCTunedTarjan(graph)
            .withProgressLogger(ProgressLogger.wrap(log, "SCC(TunedTarjan)"))
            .withTerminationFlag(TerminationFlag.wrap(transaction));

        builder.timeCompute(tarjan::compute);

        if (configuration.isWriteFlag()) {
            builder.withWrite(true);
            String partitionProperty = configuration.get(
                CONFIG_WRITE_PROPERTY,
                CONFIG_OLD_WRITE_PROPERTY,
                CONFIG_CLUSTER
            );
            builder.withPartitionProperty(partitionProperty);

            builder.timeWrite(() -> NodePropertyExporter
                .of(api, graph, tarjan.terminationFlag)
                .withLog(log)
                .parallel(Pools.DEFAULT, configuration.getWriteConcurrency())
                .build()
                .write(
                    partitionProperty,
                    tarjan.getConnectedComponents(),
                    Translators.OPTIONAL_INT_ARRAY_TRANSLATOR
                ));
        }

        final int[] connectedComponents = tarjan.getConnectedComponents();

        builder.withNodeCount(graph.nodeCount());
        builder.withCommunityFunction( l -> (long) connectedComponents[((int) l)]);

        return Stream.of(builder.build());
    }

    // algo.scc.tunedTarjan.stream
    @Procedure(name = "algo.scc.recursive.tunedTarjan.stream", mode = READ)
    @Description("CALL algo.scc.recursive.tunedTarjan.stream(label:String, relationship:String, config:Map<String, Object>) YIELD " +
                 "nodeId, partition")
    public Stream<SCCAlgorithm.StreamResult> sccTunedTarjanStream(
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationship,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config, getUsername());

        Graph graph = new GraphLoader(api, Pools.DEFAULT)
            .init(log, label, relationship, configuration)
            .withDirection(Direction.OUTGOING)
            .load(configuration.getGraphImpl());

        if (graph.isEmpty()) {
            return Stream.empty();
        }

        SCCTunedTarjan sccTunedTarjan = new SCCTunedTarjan(graph)
            .withProgressLogger(ProgressLogger.wrap(log, "SCC(TunedTarjan)"))
            .withTerminationFlag(TerminationFlag.wrap(transaction));

        sccTunedTarjan.compute();
        return sccTunedTarjan.resultStream();
    }

    private Stream<SCCResult> sccIterativeTarjan(String label, String relationship, Map<String, Object> config) {

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(config, getUsername());

        final AllocationTracker tracker = AllocationTracker.create();
        SCCResult.Builder builder = new SCCResult.Builder(true, true, tracker);

        final ProgressTimer loadTimer = builder.timeLoad();
        final Graph graph = new GraphLoader(api, Pools.DEFAULT)
            .init(log, label, relationship, configuration)
            .withDirection(Direction.OUTGOING)
            .load(configuration.getGraphImpl());
        loadTimer.stop();

        if (graph.isEmpty()) {
            return Stream.of(SCCResult.EMPTY);
        }

        final TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        final SCCAlgorithm tarjan = SCCAlgorithm.iterativeTarjan(graph, tracker)
            .withProgressLogger(ProgressLogger.wrap(log, "SCC(IterativeTarjan)"))
            .withTerminationFlag(terminationFlag);

        builder.timeCompute(tarjan::compute);

        if (configuration.isWriteFlag()) {
            builder.withWrite(true);
            String partitionProperty = configuration.get(
                CONFIG_WRITE_PROPERTY,
                CONFIG_OLD_WRITE_PROPERTY,
                CONFIG_CLUSTER
            );
            builder.withPartitionProperty(partitionProperty).withWriteProperty(partitionProperty);

            builder.timeWrite(() -> NodePropertyExporter.of(api, graph, terminationFlag)
                .withLog(log)
                .parallel(Pools.DEFAULT, configuration.getWriteConcurrency())
                .build()
                .write(
                    partitionProperty,
                    tarjan.getConnectedComponents(),
                    HugeLongArray.Translator.INSTANCE
                ));
        }

        final HugeLongArray connectedComponents = tarjan.getConnectedComponents();
        tarjan.release();
        graph.release();

        builder.withNodeCount(graph.nodeCount());
        builder.withCommunityFunction( connectedComponents::get);

        return Stream.of(builder.build());
    }

    private Stream<SCCAlgorithm.StreamResult> sccIterativeTarjanStream(
        String label,
        String relationship,
        Map<String, Object> config
    ) {

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(config, getUsername());

        final Graph graph = new GraphLoader(api, Pools.DEFAULT)
            .init(log, label, relationship, configuration)
            .withDirection(Direction.OUTGOING)
            .load(configuration.getGraphImpl());

        if (graph.isEmpty()) {
            graph.release();
            return Stream.empty();
        }

        final AllocationTracker tracker = AllocationTracker.create();
        final SCCAlgorithm algo = SCCAlgorithm.iterativeTarjan(graph, tracker)
            .withProgressLogger(ProgressLogger.wrap(log, "SCC(IterativeTarjan)"))
            .withTerminationFlag(TerminationFlag.wrap(transaction));
        algo.compute();

        graph.release();

        return algo.resultStream();
    }

    // algo.scc.forwardBackward.stream
    @Procedure(name = "algo.scc.forwardBackward.stream", mode = READ)
    @Description("CALL algo.scc.forwardBackward.stream(long startNodeId, label:String, relationship:String, {write:true, concurrency:4}) YIELD " +
                 "nodeId, partition")
    public Stream<ForwardBackwardScc.Result> fwbwStream(
        @Name(value = "startNodeId", defaultValue = "0") long startNodeId,
        @Name(value = "label", defaultValue = "") String label,
        @Name(value = "relationship", defaultValue = "") String relationship,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config, getUsername());
        Graph graph = new GraphLoader(api, Pools.DEFAULT)
            .init(log, label, relationship, configuration)
            .load(configuration.getGraphImpl());
        if (graph.isEmpty()) {
            graph.release();
            return Stream.empty();
        }
        final ForwardBackwardScc algo = new ForwardBackwardScc(graph, Pools.DEFAULT,
            configuration.concurrency()
        )
            .withProgressLogger(ProgressLogger.wrap(log, "SCC(ForwardBackward)"))
            .withTerminationFlag(TerminationFlag.wrap(transaction))
            .compute(graph.toMappedNodeId(startNodeId));
        graph.release();
        return algo.resultStream();
    }

    public static class SCCStreamResult {

        /**
         * the node id
         */
        public final long nodeId;

        /**
         * the set id of the stronly connected component or
         * -1 of not part of a SCC
         */
        public final long partition;

        public SCCStreamResult(long nodeId, long clusterId) {
            this.nodeId = nodeId;
            this.partition = clusterId;
        }
    }
}
