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
package org.neo4j.graphalgo.nodesim;

import org.HdrHistogram.DoubleHistogram;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.compat.MapUtil;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.write.RelationshipExporter;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.result.AbstractResultBuilder;
import org.neo4j.graphalgo.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

public class NodeSimilarityWriteProc extends NodeSimilarityBaseProc<NodeSimilarityWriteConfig> {

    @Procedure(name = "gds.nodeSimilarity.write", mode = WRITE)
    @Description(NODE_SIMILARITY_DESCRIPTION)
    public Stream<WriteResult> write(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<NodeSimilarity, NodeSimilarityResult, NodeSimilarityWriteConfig> result = compute(
            graphNameOrConfig,
            configuration
        );
        return write(result);
    }

    @Procedure(value = "gds.nodeSimilarity.write.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimateWrite(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected NodeSimilarityWriteConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper userInput
    ) {
        return NodeSimilarityWriteConfig.of(username, graphName, maybeImplicitCreate, userInput);
    }

    public Stream<WriteResult> write(ComputationResult<NodeSimilarity, NodeSimilarityResult, NodeSimilarityWriteConfig> computationResult) {
        NodeSimilarityWriteConfig config = computationResult.config();

        if (computationResult.isGraphEmpty()) {
            return Stream.of(
                new WriteResult(
                    computationResult.createMillis(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    Collections.emptyMap(),
                    config.toMap()
                )
            );
        }

        NodeSimilarityResult result = computationResult.result();
        NodeSimilarity algorithm = computationResult.algorithm();
        SimilarityGraphResult similarityGraphResult = result.maybeGraphResult().get();
        Graph similarityGraph = similarityGraphResult.similarityGraph();

        WriteResultBuilder resultBuilder = new WriteResultBuilder();
        resultBuilder
            .withNodesCompared(similarityGraphResult.comparedNodes())
            .withRelationshipsWritten(similarityGraphResult.similarityGraph().relationshipCount());
        resultBuilder.withCreateMillis(computationResult.createMillis());
        resultBuilder.withComputeMillis(computationResult.computeMillis());
        resultBuilder.withConfig(config);

        boolean shouldComputeHistogram = callContext
            .outputFields()
            .anyMatch(s -> s.equalsIgnoreCase("similarityDistribution"));
        if (similarityGraph.relationshipCount() > 0) {
            String writeRelationshipType = config.writeRelationshipType();
            String writeProperty = config.writeProperty();

            runWithExceptionLogging(
                "NodeSimilarity write-back failed",
                () -> {
                    try (ProgressTimer ignored = ProgressTimer.start(resultBuilder::withWriteMillis)) {
                        RelationshipExporter exporter = RelationshipExporter
                            .of(api, similarityGraph, algorithm.getTerminationFlag())
                            .withLog(log)
                            .build();
                        if (shouldComputeHistogram) {
                            DoubleHistogram histogram = new DoubleHistogram(5);
                            exporter.write(
                                writeRelationshipType,
                                writeProperty,
                                (node1, node2, similarity) -> {
                                    histogram.recordValue(similarity);
                                    return true;
                                }
                            );
                            resultBuilder.withHistogram(histogram);
                        } else {
                            exporter.write(writeRelationshipType, writeProperty);
                        }
                    }
                }
            );
        }
        return Stream.of(resultBuilder.build());
    }

    public static class WriteResult {
        public final long createMillis;
        public final long computeMillis;
        public final long writeMillis;
        public final long postProcessingMillis;

        public final long nodesCompared;
        public final long relationshipsWritten;

        public final Map<String, Object> similarityDistribution;
        public final Map<String, Object> configuration;

        WriteResult(
            long createMillis,
            long computeMillis,
            long writeMillis,
            long postProcessingMillis,
            long nodesCompared,
            long relationshipsWritten,
            Map<String, Object> similarityDistribution,
            Map<String, Object> configuration
        ) {
            this.createMillis = createMillis;
            this.computeMillis = computeMillis;
            this.writeMillis = writeMillis;
            this.postProcessingMillis = postProcessingMillis;
            this.nodesCompared = nodesCompared;
            this.relationshipsWritten = relationshipsWritten;
            this.similarityDistribution = similarityDistribution;
            this.configuration = configuration;
        }
    }

    static class WriteResultBuilder extends AbstractResultBuilder<WriteResult> {

        private long nodesCompared = 0L;

        private long postProcessingMillis = -1L;

        private Optional<DoubleHistogram> maybeHistogram = Optional.empty();

        public WriteResultBuilder withNodesCompared(long nodesCompared) {
            this.nodesCompared = nodesCompared;
            return this;
        }

        WriteResultBuilder withHistogram(DoubleHistogram histogram) {
            this.maybeHistogram = Optional.of(histogram);
            return this;
        }

        void setPostProcessingMillis(long postProcessingMillis) {
            this.postProcessingMillis = postProcessingMillis;
        }

        ProgressTimer timePostProcessing() {
            return ProgressTimer.start(this::setPostProcessingMillis);
        }

        private Map<String, Object> distribution() {
            if (maybeHistogram.isPresent()) {
                DoubleHistogram definitelyHistogram = maybeHistogram.get();
                return MapUtil.map(
                    "min", definitelyHistogram.getMinValue(),
                    "max", definitelyHistogram.getMaxValue(),
                    "mean", definitelyHistogram.getMean(),
                    "stdDev", definitelyHistogram.getStdDeviation(),
                    "p1", definitelyHistogram.getValueAtPercentile(1),
                    "p5", definitelyHistogram.getValueAtPercentile(5),
                    "p10", definitelyHistogram.getValueAtPercentile(10),
                    "p25", definitelyHistogram.getValueAtPercentile(25),
                    "p50", definitelyHistogram.getValueAtPercentile(50),
                    "p75", definitelyHistogram.getValueAtPercentile(75),
                    "p90", definitelyHistogram.getValueAtPercentile(90),
                    "p95", definitelyHistogram.getValueAtPercentile(95),
                    "p99", definitelyHistogram.getValueAtPercentile(99),
                    "p100", definitelyHistogram.getValueAtPercentile(100)
                );
            }
            return Collections.emptyMap();
        }

        @Override
        public WriteResult build() {
            return new WriteResult(
                createMillis,
                computeMillis,
                writeMillis,
                postProcessingMillis,
                nodesCompared,
                relationshipsWritten,
                distribution(),
                config.toMap()
            );
        }
    }
}
