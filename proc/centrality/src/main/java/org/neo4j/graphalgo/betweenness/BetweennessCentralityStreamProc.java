/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.betweenness;

import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.StreamProc;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.utils.paged.HugeAtomicDoubleArray;
import org.neo4j.graphalgo.core.write.PropertyTranslator;
import org.neo4j.graphalgo.core.write.Translators;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.graphalgo.betweenness.BetweennessCentralityProc.BETWEENNESS_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

public class BetweennessCentralityStreamProc extends StreamProc<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityStreamProc.StreamResult, BetweennessCentralityStreamConfig> {

    @Procedure(value = "gds.betweenness.stream", mode = READ)
    @Description(BETWEENNESS_DESCRIPTION)
    public Stream<StreamResult> stream(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        var computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        return stream(computationResult);
    }

    @Override
    protected StreamResult streamResult(long originalNodeId, double value) {
        return new StreamResult(originalNodeId, value);
    }

    @Override
    protected PropertyTranslator<HugeAtomicDoubleArray> nodePropertyTranslator(ComputationResult<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityStreamConfig> computationResult) {
        return BetweennessCentralityProc.propertyTranslator();
    }

    @Override
    protected BetweennessCentralityStreamConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return BetweennessCentralityStreamConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<BetweennessCentrality, BetweennessCentralityStreamConfig> algorithmFactory(
        BetweennessCentralityStreamConfig config
    ) {
        return BetweennessCentralityProc.algorithmFactory(config);
    }

    public static final class StreamResult {

        public final long nodeId;
        public final double centrality;

        public StreamResult(long nodeId, double centrality) {
            this.nodeId = nodeId;
            this.centrality = centrality;
        }
    }
}
