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
package org.neo4j.graphalgo.pagerank;

import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.MutatePropertyProc;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.result.AbstractResultBuilder;
import org.neo4j.graphalgo.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.graphalgo.pagerank.PageRankProc.ARTICLE_RANK_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

public class ArticleRankMutateProc extends MutatePropertyProc<PageRankAlgorithm, PageRankResult, PageRankMutateProc.MutateResult, PageRankMutateConfig> {

    @Procedure(value = "gds.articleRank.mutate", mode = READ)
    @Description(ARTICLE_RANK_DESCRIPTION)
    public Stream<PageRankMutateProc.MutateResult> mutate(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<PageRankAlgorithm, PageRankResult, PageRankMutateConfig> computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        return mutate(computationResult);
    }

    @Procedure(value = "gds.articleRank.mutate.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected PageRankMutateConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return PageRankMutateConfig.of(username, graphName, maybeImplicitCreate, config);

    }

    @Override
    protected AlgorithmFactory<PageRankAlgorithm, PageRankMutateConfig> algorithmFactory() {
        return new PageRankAlgorithmFactory<>(PageRankAlgorithmFactory.Mode.ARTICLE_RANK);
    }

    @Override
    protected NodeProperties nodeProperties(ComputationResult<PageRankAlgorithm, PageRankResult, PageRankMutateConfig> computationResult) {
        return PageRankProc.nodeProperties(computationResult);
    }

    @Override
    protected AbstractResultBuilder<PageRankMutateProc.MutateResult> resultBuilder(ComputationResult<PageRankAlgorithm, PageRankResult, PageRankMutateConfig> computeResult) {
        return PageRankProc.resultBuilder(
            new PageRankMutateProc.MutateResult.Builder(callContext, computeResult.config().concurrency()),
            computeResult
        );
    }

    @Override
    protected void validateConfigs(GraphCreateConfig graphCreateConfig, PageRankMutateConfig config) {
        super.validateConfigs(graphCreateConfig, config);
        PageRankProc.validateAlgoConfig(config, log);
    }
}
