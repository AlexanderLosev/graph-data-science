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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.neo4j.gds.ml.Training;
import org.neo4j.gds.ml.batch.BatchQueue;
import org.neo4j.gds.ml.batch.HugeBatchQueue;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.logging.Log;

import java.util.function.Supplier;

public class LinkLogisticRegressionTrain {

    private final Graph graph;
    private final HugeLongArray trainSet;
    private final LinkLogisticRegressionTrainConfig config;
    private final Log log;

    public LinkLogisticRegressionTrain(
        Graph graph,
        HugeLongArray trainSet,
        LinkLogisticRegressionTrainConfig config,
        Log log
    ) {
        this.graph = graph;
        this.trainSet = trainSet;
        this.config = config;
        this.log = log;
    }

    public LinkLogisticRegressionPredictor compute() {
        var llrData = LinkLogisticRegressionData.from(
            graph,
            config.featureProperties(),
            LinkFeatureCombiner.valueOf(config.linkFeatureCombiner())
        );
        var objective = new LinkLogisticRegressionObjective(
            llrData,
            config.penalty(),
            graph
        );
        var training = new Training(config, log, graph.nodeCount());
        Supplier<BatchQueue> queueSupplier = () -> new HugeBatchQueue(trainSet, config.batchSize());
        training.train(objective, queueSupplier, config.concurrency());
        return new LinkLogisticRegressionPredictor(objective.modelData);
    }
}
