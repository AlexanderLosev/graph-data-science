/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.graphalgo.centrality;

import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.AlphaAlgorithmFactory;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.utils.AtomicDoubleArray;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.write.NodePropertyExporter;
import org.neo4j.graphalgo.core.write.Translators;
import org.neo4j.graphalgo.impl.betweenness.BetweennessCentrality;
import org.neo4j.graphalgo.impl.betweenness.BetweennessCentralityConfig;
import org.neo4j.graphalgo.result.AbstractResultBuilder;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

/**
 * Betweenness Centrality Algorithms
 *
 * all procedures accept {@code in, incoming, <, out, outgoing, >, both, <>} as direction
 */
public class BetweennessCentralityProc extends AlgoBaseProc<BetweennessCentrality, BetweennessCentrality, BetweennessCentralityConfig> {

    private static final String DESCRIPTION =
        "Betweenness centrality is a way of detecting the amount of influence a node has " +
        "over the flow of information in a graph.";

    @Procedure(name = "gds.alpha.betweenness.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<BetweennessCentrality.Result> stream(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<BetweennessCentrality, BetweennessCentrality, BetweennessCentralityConfig> computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        if (computationResult.graph().isEmpty()) {
            return Stream.empty();
        }
        return computationResult.algorithm().resultStream();
    }

    @Procedure(value = "gds.alpha.betweenness.write", mode = WRITE)
    @Description(DESCRIPTION)
    public Stream<BetweennessCentralityProcResult> write(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<BetweennessCentrality, BetweennessCentrality, BetweennessCentralityConfig> computationResult = compute(
            graphNameOrConfig,
            configuration
        );

        BetweennessCentralityProcResult.Builder builder = BetweennessCentralityProcResult.builder();

        Graph graph = computationResult.graph();
        BetweennessCentrality algo = computationResult.algorithm();
        BetweennessCentralityConfig config = computationResult.config();

        if (graph.isEmpty()) {
            return Stream.of(builder.build());
        }

        computeStats(builder, algo.getCentrality());
        builder.withNodeCount(graph.nodeCount())
            .withComputeMillis(computationResult.computeMillis())
            .withCreateMillis((computationResult.createMillis()));

        graph.release();

        try(ProgressTimer ignore = ProgressTimer.start(builder::withWriteMillis)) {
            AtomicDoubleArray centrality = algo.getCentrality();
            NodePropertyExporter.builder(api, graph, algo.getTerminationFlag())
                .withLog(log)
                .parallel(Pools.DEFAULT, config.writeConcurrency())
                .build()
                .write(config.writeProperty(), centrality, Translators.ATOMIC_DOUBLE_ARRAY_TRANSLATOR);
        }
        algo.release();
        return Stream.of(builder.build());
    }

    private void computeStats(BetweennessCentralityProcResult.Builder builder, AtomicDoubleArray centrality) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        for (int i = centrality.length() - 1; i >= 0; i--) {
            double c = centrality.get(i);
            if (c < min) {
                min = c;
            }
            if (c > max) {
                max = c;
            }
            sum += c;
        }
        builder.withCentralityMax(max)
            .withCentralityMin(min)
            .withCentralitySum(sum);
    }

    @Override
    protected BetweennessCentralityConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return BetweennessCentralityConfig.of(graphName, maybeImplicitCreate, username, config);
    }

    @Override
    protected void validateConfigs(GraphCreateConfig graphCreateConfig, BetweennessCentralityConfig config) {
        config.validate(graphCreateConfig);
    }

    @Override
    protected AlgorithmFactory<BetweennessCentrality, BetweennessCentralityConfig> algorithmFactory(
        BetweennessCentralityConfig config
    ) {
        return new AlphaAlgorithmFactory<BetweennessCentrality, BetweennessCentralityConfig>() {
            @Override
            public BetweennessCentrality buildAlphaAlgo(
                Graph graph,
                BetweennessCentralityConfig configuration,
                AllocationTracker tracker,
                Log log
            ) {
                return new BetweennessCentrality(
                    graph,
                    Pools.DEFAULT,
                    configuration.concurrency(),
                    configuration.undirected()
                ).withTerminationFlag(TerminationFlag.wrap(transaction));
            }
        };
    }

    public static final class BetweennessCentralityProcResult {

        public final long createMillis;
        public final long computeMillis;
        public final long writeMillis;
        public final long nodes;
        public final double minCentrality;
        public final double maxCentrality;
        public final double sumCentrality;

        private BetweennessCentralityProcResult(
            Long createMillis,
            Long computeMillis,
            Long writeMillis,
            Long nodes,
            Double centralityMin,
            Double centralityMax,
            Double centralitySum
        ) {
            this.createMillis = createMillis;
            this.computeMillis = computeMillis;
            this.writeMillis = writeMillis;
            this.nodes = nodes;
            this.minCentrality = centralityMin;
            this.maxCentrality = centralityMax;
            this.sumCentrality = centralitySum;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder extends AbstractResultBuilder<BetweennessCentralityProcResult> {

            private double centralityMin = -1;
            private double centralityMax = -1;
            private double centralitySum = -1;

            public Builder withCentralityMin(double centralityMin) {
                this.centralityMin = centralityMin;
                return this;
            }

            public Builder withCentralityMax(double centralityMax) {
                this.centralityMax = centralityMax;
                return this;
            }

            public Builder withCentralitySum(double centralitySum) {
                this.centralitySum = centralitySum;
                return this;
            }

            public BetweennessCentralityProcResult build() {
                return new BetweennessCentralityProcResult(
                    createMillis,
                    computeMillis,
                    writeMillis,
                    nodeCount,
                    centralityMin,
                    centralityMax,
                    centralitySum
                );
            }
        }
    }
}
