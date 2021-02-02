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
package org.neo4j.gds.ml.linkmodels;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.neo4j.gds.ml.batch.HugeBatchQueue;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionData;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionPredictor;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionTrain;
import org.neo4j.gds.ml.linkmodels.logisticregression.LinkLogisticRegressionTrainConfig;
import org.neo4j.gds.ml.linkmodels.metrics.LinkMetric;
import org.neo4j.gds.ml.nodemodels.ImmutableModelStats;
import org.neo4j.gds.ml.nodemodels.MetricData;
import org.neo4j.gds.ml.nodemodels.ModelStats;
import org.neo4j.gds.ml.splitting.NodeSplit;
import org.neo4j.gds.ml.splitting.StratifiedKFoldSplitter;
import org.neo4j.gds.ml.util.ShuffleUtil;
import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.model.Model;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.neo4j.gds.ml.nodemodels.ModelStats.COMPARE_AVERAGE;

public class LinkPredictionTrain
    extends Algorithm<LinkPredictionTrain, Model<LinkLogisticRegressionData, LinkPredictionTrainConfig>> {

    public static final String MODEL_TYPE = "Link Prediction";

    private final Graph trainGraph;
    private final Graph testGraph;
    private final LinkPredictionTrainConfig config;
    private final Log log;
    private final AllocationTracker allocationTracker;

    public LinkPredictionTrain(
        Graph trainGraph,
        Graph testGraph,
        LinkPredictionTrainConfig config,
        Log log
    ) {
        this.trainGraph = trainGraph;
        this.testGraph = testGraph;
        this.config = config;
        this.log = log;
        this.allocationTracker = AllocationTracker.empty();
    }

    @Override
    public Model<LinkLogisticRegressionData, LinkPredictionTrainConfig> compute() {

        // init and shuffle node ids
        var nodeIds = HugeLongArray.newArray(trainGraph.nodeCount(), allocationTracker);
        nodeIds.setAll(i -> i);
        ShuffleUtil.shuffleHugeLongArray(nodeIds, getRandomDataGenerator());

        var modelSelectResult = modelSelect(nodeIds);
        var bestParameters = modelSelectResult.bestParameters();

        // train best model on the entire test set
        var predictor = trainModel(nodeIds, bestParameters);

        // evaluate the best model on the train and test graphs
        var outerTrainMetrics = computeMetric(trainGraph, nodeIds, predictor);
        var testMetrics = computeMetric(testGraph, nodeIds, predictor);

        var metrics = mergeMetrics(modelSelectResult, outerTrainMetrics, testMetrics);

        return Model.of(
            config.username(),
            config.modelName(),
            MODEL_TYPE,
            trainGraph.schema(),
            predictor.modelData(),
            config,
            LinkPredictionModelInfo.of(
                modelSelectResult.bestParameters(),
                metrics
            )
        );
    }

    private RandomDataGenerator getRandomDataGenerator() {
        var random = new RandomDataGenerator();
        config.randomSeed().ifPresent(random::reSeed);
        return random;
    }

    private Map<LinkMetric, MetricData> mergeMetrics(
        ModelSelectResult modelSelectResult,
        Map<LinkMetric, Double> outerTrainMetrics,
        Map<LinkMetric, Double> testMetrics
    ) {
        return modelSelectResult.validationStats().keySet().stream().collect(Collectors.toMap(
            metric -> metric,
            metric ->
                MetricData.of(
                    modelSelectResult.trainStats().get(metric),
                    modelSelectResult.validationStats().get(metric),
                    outerTrainMetrics.get(metric),
                    testMetrics.get(metric)
                )
        ));
    }

    private ModelSelectResult modelSelect(HugeLongArray allNodeIds) {
        var splits = trainValidationSplits(allNodeIds);

        var trainStats = initStatsMap();
        var validationStats = initStatsMap();

        config.params().forEach(modelParams -> {
            var trainStatsBuilder = new ModelStatsBuilder(
                modelParams,
                config.validationFolds()
            );
            var validationStatsBuilder = new ModelStatsBuilder(
                modelParams,
                config.validationFolds()
            );
            for (NodeSplit split : splits) {
                // 3. train each model candidate on the train sets
                var trainSet = split.trainSet();
                var validationSet = split.testSet();
                var predictor = trainModel(trainSet, modelParams);

                // 4. evaluate each model candidate on the train and validation sets
                computeMetric(trainGraph, trainSet, predictor).forEach(trainStatsBuilder::update);
                computeMetric(trainGraph, validationSet, predictor).forEach(validationStatsBuilder::update);
            }
            // insert the candidates metrics into trainStats and validationStats
            config.metrics().forEach(metric -> {
                validationStats.get(metric).add(validationStatsBuilder.modelStats(metric));
                trainStats.get(metric).add(trainStatsBuilder.modelStats(metric));
            });
        });

        // 5. pick the best-scoring model candidate, according to the main metric
        var mainMetric = config.metrics().get(0);
        var modelStats = validationStats.get(mainMetric);
        var winner = Collections.max(modelStats, COMPARE_AVERAGE);

        var bestConfig = winner.params();
        return ModelSelectResult.of(bestConfig, trainStats, validationStats);
    }

    private List<NodeSplit> trainValidationSplits(HugeLongArray allNodeIds) {
        var globalTargets = HugeLongArray.newArray(trainGraph.nodeCount(), allocationTracker);
        globalTargets.setAll(i -> 0L);
        var splitter = new StratifiedKFoldSplitter(config.validationFolds(), allNodeIds, globalTargets);
        return splitter.splits();
    }

    @ValueClass
    public interface ModelSelectResult {
        Map<String, Object> bestParameters();

        // key is metric
        Map<LinkMetric, List<ModelStats>> trainStats();
        // key is metric
        Map<LinkMetric, List<ModelStats>> validationStats();

        static ModelSelectResult of(
            Map<String, Object> bestConfig,
            Map<LinkMetric, List<ModelStats>> trainStats,
            Map<LinkMetric, List<ModelStats>> validationStats
        ) {
            return ImmutableModelSelectResult.of(bestConfig, trainStats, validationStats);
        }

    }

    private Map<LinkMetric, List<ModelStats>> initStatsMap() {
        var statsMap = new HashMap<LinkMetric, List<ModelStats>>();
        statsMap.put(LinkMetric.AUCPR, new ArrayList<>());
        return statsMap;
    }

    private Map<LinkMetric, Double> computeMetric(
        Graph evaluationGraph,
        HugeLongArray evaluationSet,
        LinkLogisticRegressionPredictor predictor
    ) {
        var signedProbabilities = SignedProbabilities.create(evaluationGraph.relationshipCount());

        var queue = new HugeBatchQueue(evaluationSet);
        queue.parallelConsume(config.concurrency(), ignore -> new SignedProbabilitiesCollector(
            evaluationGraph.concurrentCopy(),
            predictor,
            signedProbabilities,
            progressLogger
        ));

        return config.metrics().stream().collect(Collectors.toMap(
            metric -> metric,
            metric -> metric.compute(signedProbabilities, config.classRatio())
        ));
    }

    private LinkLogisticRegressionPredictor trainModel(
        HugeLongArray trainSet,
        Map<String, Object> modelParams
    ) {
        var llrTrain = new LinkLogisticRegressionTrain(
            trainGraph,
            trainSet,
            LinkLogisticRegressionTrainConfig.of(config.featureProperties(), modelParams),
            log
        );

        return llrTrain.compute();
    }

    private static class ModelStatsBuilder {
        private final Map<LinkMetric, Double> min;
        private final Map<LinkMetric, Double> max;
        private final Map<LinkMetric, Double> sum;
        private final Map<String, Object> modelParams;
        private final int numberOfSplits;

        ModelStatsBuilder(Map<String, Object> modelParams, int numberOfSplits) {
            this.modelParams = modelParams;
            this.numberOfSplits = numberOfSplits;
            this.min = new HashMap<>();
            this.max = new HashMap<>();
            this.sum = new HashMap<>();
        }

        void update(LinkMetric metric, double value) {
            min.merge(metric, value, Math::min);
            max.merge(metric, value, Math::max);
            sum.merge(metric, value, Double::sum);
        }

        ModelStats modelStats(LinkMetric metric) {
            return ImmutableModelStats.of(
                modelParams,
                sum.get(metric) / numberOfSplits,
                min.get(metric),
                max.get(metric)
            );
        }
    }

    @Override
    public LinkPredictionTrain me() {
        return this;
    }

    @Override
    public void release() {

    }
}
