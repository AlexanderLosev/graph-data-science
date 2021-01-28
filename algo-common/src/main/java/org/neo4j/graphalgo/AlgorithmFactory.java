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
package org.neo4j.graphalgo;

import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.config.AlgoBaseConfig;
import org.neo4j.graphalgo.config.RelationshipWeightConfig;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.progress.ProgressEventTracker;
import org.neo4j.logging.Log;

import java.util.Optional;

public interface AlgorithmFactory<ALGO extends Algorithm<ALGO, ?>, CONFIG extends AlgoBaseConfig> {

    ALGO build(
        Graph graph,
        CONFIG configuration,
        AllocationTracker tracker,
        Log log,
        ProgressEventTracker eventTracker
    );

    default GraphAndAlgo<ALGO> build(
        GraphStore graphStore,
        CONFIG configuration,
        AllocationTracker tracker,
        Log log,
        ProgressEventTracker eventTracker
    ) {
        Optional<String> weightProperty = configuration instanceof RelationshipWeightConfig
            ? Optional.ofNullable(((RelationshipWeightConfig) configuration).relationshipWeightProperty())
            : Optional.empty();

        var nodeLabels = configuration.nodeLabelIdentifiers(graphStore);
        var relationshipTypes = configuration.internalRelationshipTypes(graphStore);
        var graph = graphStore.getGraph(nodeLabels, relationshipTypes, weightProperty);

        var algo = build(
            graph,
            configuration,
            tracker,
            log,
            eventTracker
        );
        return GraphAndAlgo.of(graph, algo);
    }

    /**
     * Returns an estimation about the memory consumption of that algorithm. The memory estimation can be used to
     * compute the actual consumption depending on {@link org.neo4j.graphalgo.core.GraphDimensions} and concurrency.
     *
     * @return memory estimation
     * @see org.neo4j.graphalgo.core.utils.mem.MemoryEstimations
     * @see MemoryEstimation#estimate(org.neo4j.graphalgo.core.GraphDimensions, int)
     */
    MemoryEstimation memoryEstimation(CONFIG configuration);

    @ValueClass
    interface GraphAndAlgo<ALGORITHM extends Algorithm<ALGORITHM, ?>> {

        Graph graph();
        ALGORITHM algo();

        static <ALGORITHM extends Algorithm<ALGORITHM, ?>> GraphAndAlgo<ALGORITHM> of(
            Graph graph,
            ALGORITHM algo
        ) {
            return ImmutableGraphAndAlgo.of(graph, algo);
        }
    }
}
