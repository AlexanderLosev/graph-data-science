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
package org.neo4j.graphalgo.beta.pregel.cc;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.processing.Generated;
import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.BaseProc;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.beta.pregel.Pregel;
import org.neo4j.graphalgo.beta.pregel.PregelConfig;
import org.neo4j.graphalgo.beta.pregel.PregelStreamProc;
import org.neo4j.graphalgo.beta.pregel.PregelStreamResult;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.results.MemoryEstimateResult;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

@Generated("org.neo4j.graphalgo.beta.pregel.PregelProcessor")
public final class ComputationStreamProc extends PregelStreamProc<ComputationAlgorithm, PregelConfig> {
    @Procedure(
            name = "gds.pregel.test.stream",
            mode = Mode.READ
    )
    @Description("Test computation description")
    public Stream<PregelStreamResult> stream(@Name("graphName") Object graphNameOrConfig,
            @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return stream(compute(graphNameOrConfig, configuration));
    }

    @Procedure(
            name = "gds.pregel.test.stream.estimate",
            mode = Mode.READ
    )
    @Description(BaseProc.ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> streamEstimate(@Name("graphName") Object graphNameOrConfig,
            @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected PregelStreamResult streamResult(long originalNodeId, long internalNodeId,
            NodeProperties nodeProperties) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected PregelConfig newConfig(String username, Optional<String> graphName,
            Optional<GraphCreateConfig> maybeImplicitCreate, CypherMapWrapper config) {
        return PregelConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<ComputationAlgorithm, PregelConfig> algorithmFactory() {
        return new AlgorithmFactory<ComputationAlgorithm, PregelConfig>() {
            @Override
            public ComputationAlgorithm build(Graph graph, PregelConfig configuration,
                    AllocationTracker tracker, Log log) {
                return new ComputationAlgorithm(graph, configuration, tracker, log);
            }

            @Override
            public MemoryEstimation memoryEstimation(PregelConfig configuration) {
                var schema = new Computation().schema();
                return Pregel.memoryEstimation(schema);
            }
        };
    }
}
