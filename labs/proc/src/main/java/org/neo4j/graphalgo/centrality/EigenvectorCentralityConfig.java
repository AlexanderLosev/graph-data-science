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

package org.neo4j.graphalgo.centrality;

import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.newapi.AlgoBaseConfig;
import org.neo4j.graphalgo.newapi.GraphCreateConfig;
import org.neo4j.graphalgo.newapi.IterationsConfig;
import org.neo4j.graphalgo.newapi.SourceNodesConfig;
import org.neo4j.graphalgo.newapi.WeightConfig;
import org.neo4j.graphalgo.newapi.WriteConfig;
import org.neo4j.graphdb.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ValueClass
@Configuration("EigenvectorCentralityConfigImpl")
public interface EigenvectorCentralityConfig extends
    AlgoBaseConfig,
    IterationsConfig,
    SourceNodesConfig,
    WriteConfig,
    WeightConfig {

    @Value.Default
    @Override
    default int maxIterations() {
        return 20;
    }

    @Value.Default
    @Override
    default List<Node> sourceNodes() {
        return new ArrayList<>();
    }

    @Value.Default
    @Override
    default String writeProperty() {
        return "eigenvector";
    }

    @Value.Default
    default String normalization() {
        return "NONE";
    }

    static EigenvectorCentralityConfig of(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper userInput
    ) {
        return new EigenvectorCentralityConfigImpl(
            graphName,
            maybeImplicitCreate,
            username,
            userInput
        );
    }
}
