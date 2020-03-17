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
package org.neo4j.graphalgo.base2;

import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.config.AlgoBaseConfig;
import org.neo4j.graphalgo.config.MutatePropertyConfig;
import org.neo4j.graphalgo.core.loading.GraphStore;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.write.PropertyTranslator;
import org.neo4j.graphalgo.result.AbstractResultBuilder;

import java.util.stream.Stream;

public abstract class MutateProc<
    ALGO extends Algorithm<ALGO, ALGO_RESULT>,
    ALGO_RESULT,
    PROC_RESULT,
    CONFIG extends AlgoBaseConfig> extends WriteOrMutateProc<ALGO, ALGO_RESULT, PROC_RESULT, CONFIG> {

    protected abstract PropertyTranslator<ALGO_RESULT> nodePropertyTranslator(ComputationResult2<ALGO, ALGO_RESULT, CONFIG> computationResult);

    protected Stream<PROC_RESULT> mutate(ComputationResult2<ALGO, ALGO_RESULT, CONFIG> computeResult) {
        return writeOrMutate(computeResult,
            (writeBuilder, computationResult) -> mutateNodeProperties(writeBuilder, computationResult)
        );
    }

    private void mutateNodeProperties(
        AbstractResultBuilder<?> resultBuilder,
        ComputationResult2<ALGO, ALGO_RESULT, CONFIG> computationResult
    ) {
        PropertyTranslator<ALGO_RESULT> resultPropertyTranslator = nodePropertyTranslator(computationResult);

        CONFIG config = computationResult.config();
        if (!(config instanceof MutatePropertyConfig)) {
            throw new IllegalArgumentException(String.format(
                "Can only mutate results if the config implements %s.",
                MutatePropertyConfig.class
            ));
        }

        MutatePropertyConfig mutatePropertyConfig = (MutatePropertyConfig) config;
        ALGO_RESULT result = computationResult.result();
        try (ProgressTimer ignored = ProgressTimer.start(resultBuilder::withMutateMillis)) {
            log.debug("Updating in-memory graph store");
            GraphStore graphStore = computationResult.graphStore();
            graphStore.addNodeProperty(
                mutatePropertyConfig.writeProperty(),
                nodeId -> resultPropertyTranslator.toDouble(result, nodeId)
            );
            resultBuilder.withNodePropertiesWritten(computationResult.graph().nodeCount());
        }
    }

    /* private void mutateRelationshipTypes(....) */

}
