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
package org.neo4j.graphalgo;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.write.Exporter;
import org.neo4j.graphalgo.impl.labelprop.LabelPropagation;
import org.neo4j.graphalgo.impl.results.LabelPropagationStats;
import org.neo4j.graphalgo.impl.results.LabelPropagationStats.StreamResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public final class LabelPropagationProc {

    private static final String CONFIG_WEIGHT_KEY = "weightProperty";
    private static final String CONFIG_WRITE_KEY = "writeProperty";
    private static final String CONFIG_LABEL_KEY = "labelProperty";
    private static final Integer DEFAULT_ITERATIONS = 1;
    private static final Boolean DEFAULT_WRITE = Boolean.TRUE;
    private static final String DEFAULT_WEIGHT_KEY = "weight";
    private static final String DEFAULT_LABEL_KEY = "label";

    @SuppressWarnings("WeakerAccess")
    @Context
    public GraphDatabaseAPI dbAPI;

    @SuppressWarnings("WeakerAccess")
    @Context
    public Log log;

    @SuppressWarnings("WeakerAccess")
    @Context
    public KernelTransaction transaction;

    @Procedure(name = "algo.labelPropagation", mode = Mode.WRITE)
    @Description("CALL algo.labelPropagation(" +
            "label:String, relationship:String, direction:String, " +
            "{iterations:1, weightProperty:'weight', labelProperty:'label', write:true, concurrency:4}) " +
            "YIELD nodes, iterations, didConverge, loadMillis, computeMillis, writeMillis, write, weightProperty, labelProperty - " +
            "simple label propagation kernel")
    public Stream<LabelPropagationStats> labelPropagation(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationshipType,
            @Name(value = "config", defaultValue="null") Object directionOrConfig,
            @Name(value = "deprecatedConfig", defaultValue = "{}") Map<String, Object> config) {
        Map<String, Object> rawConfig = config;
        if (directionOrConfig == null) {
            if (!config.isEmpty()) {
                directionOrConfig = "OUTGOING";
            }
        } else if (directionOrConfig instanceof Map) {
            rawConfig = (Map<String, Object>) directionOrConfig;
        }

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(rawConfig)
                .overrideNodeLabelOrQuery(label)
                .overrideRelationshipTypeOrQuery(relationshipType);

        if(directionOrConfig instanceof String) {
            configuration.overrideDirection((String) directionOrConfig);
        }

        final int iterations = configuration.getIterations(DEFAULT_ITERATIONS);
        final int batchSize = configuration.getBatchSize();
        final String labelProperty = configuration.getString(CONFIG_LABEL_KEY, DEFAULT_LABEL_KEY);
        final String writeProperty = configuration.get(CONFIG_WRITE_KEY, CONFIG_LABEL_KEY, DEFAULT_LABEL_KEY);
        final String weightProperty = configuration.getString(CONFIG_WEIGHT_KEY, DEFAULT_WEIGHT_KEY);

        LabelPropagationStats.Builder stats = new LabelPropagationStats.Builder()
                .iterations(iterations)
                .labelProperty(labelProperty)
                .writeProperty(writeProperty)
                .weightProperty(weightProperty);

        GraphLoader graphLoader = graphLoader(configuration, weightProperty, createPropertyMappings(labelProperty, weightProperty));
        Direction direction = configuration.getDirection(Direction.OUTGOING);
        if (direction == Direction.BOTH) {
            graphLoader.asUndirected(true);
            direction = Direction.OUTGOING;
        } else {
            graphLoader.withDirection(direction);
        }

        AllocationTracker tracker = AllocationTracker.create();
        Graph graph = load(graphLoader.withAllocationTracker(tracker), configuration, stats);

        if(graph.nodeCount() == 0) {
            graph.release();
            return Stream.of(LabelPropagationStats.EMPTY);
        }

        final LabelPropagation.Labels labels = compute(configuration, direction, iterations, batchSize, configuration.getConcurrency(), graph, tracker, stats);
        if (configuration.isWriteFlag(DEFAULT_WRITE) && writeProperty != null) {
            stats.withWrite(true);
            write(configuration.getWriteConcurrency(), writeProperty, graph, labels, stats);
        }

        return Stream.of(stats.build(tracker, graph.nodeCount(), labels::labelFor));
    }

    @Procedure(value = "algo.labelPropagation.stream")
    @Description("CALL algo.labelPropagation.stream(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "nodeId, label")
    public Stream<StreamResult> labelPropagationStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(config)
                .overrideNodeLabelOrQuery(label)
                .overrideRelationshipTypeOrQuery(relationship);

        final int iterations = configuration.getIterations(DEFAULT_ITERATIONS);
        final int batchSize = configuration.getBatchSize();
        final String labelProperty = configuration.getString(CONFIG_LABEL_KEY, null);
        final String weightProperty = configuration.getString(CONFIG_WEIGHT_KEY, null);

        PropertyMapping[] propertyMappings = createPropertyMappings(labelProperty, weightProperty);

        GraphLoader graphLoader = graphLoader(configuration, weightProperty, propertyMappings);
        Direction direction = configuration.getDirection(Direction.OUTGOING);
        if (direction == Direction.BOTH) {
            graphLoader.asUndirected(true);
            direction = Direction.OUTGOING;
        } else {
            graphLoader.withDirection(direction);
        }
        LabelPropagationStats.Builder stats = new LabelPropagationStats.Builder();

        AllocationTracker tracker = AllocationTracker.create();
        Graph graph = load(graphLoader.withAllocationTracker(tracker), configuration, stats);

        if(graph.nodeCount() == 0L) {
            graph.release();
            return Stream.empty();
        }

        LabelPropagation.Labels result = compute(configuration, direction, iterations, batchSize, configuration.getConcurrency(), graph, tracker, stats);

        return LongStream.range(0L, result.size())
                .mapToObj(i -> new StreamResult(graph.toOriginalNodeId(i), result.labelFor(i)));

    }

    private PropertyMapping[] createPropertyMappings(String labelProperty, String weightProperty) {
        ArrayList<PropertyMapping> propertyMappings = new ArrayList<>();
        if (labelProperty != null) {
            propertyMappings.add(PropertyMapping.of(LabelPropagation.LABEL_TYPE, labelProperty, 0d));
        }
        if (weightProperty != null) {
            propertyMappings.add(PropertyMapping.of(LabelPropagation.WEIGHT_TYPE, weightProperty, 1d));
        }
        return propertyMappings.toArray(new PropertyMapping[0]);
    }

    private Graph load(GraphLoader graphLoader, ProcedureConfiguration config, LabelPropagationStats.Builder stats) {
        Class<? extends GraphFactory> graphImpl = config.getGraphImpl();
        try (ProgressTimer ignored = stats.timeLoad()) {
            return graphLoader.load(graphImpl);
        }
    }

    private GraphLoader graphLoader(ProcedureConfiguration config, String weightKey, PropertyMapping... propertyMappings) {
        return new GraphLoader(dbAPI, Pools.DEFAULT)
                .init(log, config.getNodeLabelOrQuery(), config.getRelationshipOrQuery(), config)
                .withOptionalRelationshipWeightsFromProperty(weightKey, 1.0d)
                .withOptionalNodeProperties(propertyMappings);
    }

    private LabelPropagation.Labels compute(
            ProcedureConfiguration configuration,
            Direction direction,
            int iterations,
            int batchSize,
            int concurrency,
            Graph graph,
            AllocationTracker tracker,
            LabelPropagationStats.Builder stats) {
        try {
            return compute(direction, iterations, batchSize, concurrency, graph, graph, tracker, stats);
        } finally {
            graph.release();
        }
    }

    private LabelPropagation.Labels compute(
            Direction direction,
            int iterations,
            int batchSize,
            int concurrency,
            Graph graph,
            NodeProperties nodeProperties,
            AllocationTracker tracker,
            LabelPropagationStats.Builder stats) {

        ExecutorService pool = batchSize > 0 ? Pools.DEFAULT : null;
        batchSize = Math.max(1, batchSize);
        LabelPropagation labelPropagation = new LabelPropagation(graph, nodeProperties, batchSize, concurrency, pool, tracker);
        try (ProgressTimer ignored = stats.timeEval()) {
            labelPropagation
                    .withProgressLogger(ProgressLogger.wrap(log, "LabelPropagation"))
                    .withTerminationFlag(TerminationFlag.wrap(transaction))
                    .compute(direction, iterations);

            final LabelPropagation.Labels result = labelPropagation.labels();

            stats.iterations(labelPropagation.ranIterations());
            stats.didConverge(labelPropagation.didConverge());

            labelPropagation.release();
            graph.release();
            return result;
        }
    }

    private void write(
            int concurrency,
            String labelKey,
            Graph graph,
            LabelPropagation.Labels labels,
            LabelPropagationStats.Builder stats) {
        try (ProgressTimer ignored = stats.timeWrite()) {
            Exporter.of(dbAPI, graph)
                    .withLog(log)
                    .parallel(Pools.DEFAULT, concurrency, TerminationFlag.wrap(transaction))
                    .build()
                    .write(
                            labelKey,
                            labels,
                            LabelPropagation.LABEL_TRANSLATOR
                    );
        }
    }
}
