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
package org.neo4j.graphalgo.core;

import org.apache.commons.lang3.StringUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.neo4j.graphalgo.AbstractPropertyMappings;
import org.neo4j.graphalgo.ElementIdentifier;
import org.neo4j.graphalgo.NodeProjections;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.PropertyMappings;
import org.neo4j.graphalgo.RelationshipProjection;
import org.neo4j.graphalgo.RelationshipProjections;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.api.GraphSetup;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.newapi.GraphCreateConfig;
import org.neo4j.graphalgo.newapi.ImmutableGraphCreateConfig;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.internal.kernel.api.security.AuthSubject;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.logging.NullLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.neo4j.helpers.Exceptions.throwIfUnchecked;

/**
 * The GraphLoader provides a fluent interface and default values to configure
 * the {@link Graph} before loading it.
 * <p>
 * By default, the complete graph is loaded – no restriction based on
 * node label or relationship type is made.
 * Weights are also not loaded by default.
 */
public class GraphLoader {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType CTOR_METHOD = MethodType.methodType(
        void.class,
        GraphDatabaseAPI.class,
        GraphSetup.class
    );

    private String username = AuthSubject.ANONYMOUS.username();
    private String graphName = "";
    private int concurrency = Pools.DEFAULT_CONCURRENCY;
    private String label = null;
    private String relationshipType = null;
    private Direction direction = Direction.BOTH;

    private final GraphDatabaseAPI api;
    private ExecutorService executorService;
    private final Map<String, Object> params = new HashMap<>();
    private int batchSize = ParallelUtil.DEFAULT_BATCH_SIZE;

    private DeduplicationStrategy deduplicationStrategy = DeduplicationStrategy.DEFAULT;

    private Log log = NullLog.getInstance();
    private long logMillis = -1;
    private AllocationTracker tracker = AllocationTracker.EMPTY;
    private TerminationFlag terminationFlag = TerminationFlag.RUNNING_TRUE;
    private boolean undirected = false;
    private final AbstractPropertyMappings.Builder nodePropertyMappings = AbstractPropertyMappings.builder();
    private final AbstractPropertyMappings.Builder relPropertyMappings = AbstractPropertyMappings.builder();
    private boolean isLoadedGraph = false;
    private GraphCreateConfig createConfig;

    /**
     * Creates a new serial GraphLoader.
     */
    public GraphLoader(GraphDatabaseAPI api) {
        this.api = Objects.requireNonNull(api);
        this.executorService = null;
    }

    /**
     * Creates a new parallel GraphLoader.
     * What exactly parallel means depends on the {@link GraphFactory}
     * implementation provided in {@link #load(Class)}.
     */
    public GraphLoader(GraphDatabaseAPI api, ExecutorService executorService) {
        this.api = Objects.requireNonNull(api);
        this.executorService = Objects.requireNonNull(executorService);
    }

    @Deprecated
    public GraphLoader init(
        Log log,
        @Nullable String label,
        @Nullable String relationshipType,
        ProcedureConfiguration config
    ) {
        return withLog(log)
            .withUsername(config.getUsername())
            .withName(config.getGraphName(null))
            .withOptionalLabel(label)
            .withOptionalRelationshipType(relationshipType)
            .withConcurrency(config.getReadConcurrency())
            .withBatchSize(config.getBatchSize())
            .withDeduplicationStrategy(config.getDeduplicationStrategy())
            .withParams(config.getParams())
            .withLoadedGraph(config.getGraphImpl() == GraphCatalog.class);
    }

    public GraphLoader init(Log log, String username) {
        return withLog(log).withUsername(username);
    }

    public GraphLoader withGraphCreateConfig(GraphCreateConfig createConfig) {
        this.createConfig = createConfig;
        return this;
    }

    /**
     * Use the given {@link Log}instance to log the progress during loading.
     */
    public GraphLoader withLog(Log log) {
        this.log = log;
        return this;
    }

    /**
     * Log progress every {@code interval} time units.
     * At most 1 message will be logged within this interval, but it is not
     * guaranteed that a message will be logged at all.
     */
    public GraphLoader withLogInterval(long value, TimeUnit unit) {
        this.logMillis = unit.toMillis(value);
        return this;
    }

    public GraphLoader withLoadedGraph(boolean isLoadedGraph) {
        this.isLoadedGraph = isLoadedGraph;
        return this;
    }

    /**
     * @deprecated remove calls, there is no replacement
     */
    @Deprecated
    public GraphLoader sorted() {
        return this;
    }

    public GraphLoader undirected() {
        this.undirected = true;
        return this;
    }

    /**
     * Use the given {@link AllocationTracker} to track memory allocations during loading.
     *
     * If the tracker is {@code null}, we use {@link AllocationTracker#EMPTY}.
     */
    public GraphLoader withAllocationTracker(AllocationTracker tracker) {
        this.tracker = (tracker == null) ? AllocationTracker.EMPTY : tracker;
        return this;
    }

    /**
     * Use the given {@link TerminationFlag} to check when the termination has been terminated.
     *
     * If the terminationFlag is {@code null}, we use {@link TerminationFlag#RUNNING_TRUE}.
     */
    public GraphLoader withTerminationFlag(TerminationFlag terminationFlag) {
        this.terminationFlag = (terminationFlag == null) ? TerminationFlag.RUNNING_TRUE : terminationFlag;
        return this;
    }

    /**
     * Sets an executor service.
     */
    public GraphLoader withExecutorService(ExecutorService executorService) {
        this.executorService = Objects.requireNonNull(executorService);
        return this;
    }

    /**
     * Change the concurrency level. Negative and zero values are not supported.
     */
    public GraphLoader withConcurrency(int newConcurrency) {
        if (newConcurrency <= 0) {
            throw new IllegalArgumentException(String.format(
                "Concurrency less than one is invalid: %d",
                newConcurrency
            ));
        }
        this.concurrency = Pools.allowedConcurrency(newConcurrency);
        return this;
    }

    /**
     * Change the concurrency level to the default concurrency, which is based
     * on the numbers of detected processors.
     */
    public GraphLoader withDefaultConcurrency() {
        return withConcurrency(Pools.DEFAULT_CONCURRENCY);
    }

    /**
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    @Deprecated
    public GraphLoader withName(String name) {
        this.graphName = name;
        return this;
    }

    /**
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    @Deprecated
    public GraphLoader withUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Instructs the loader to load only nodes with the given label.
     *
     * @param label Must not be null; to remove a label filter, use {@link #withAnyLabel()} instead.
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withLabel(String label) {
        this.label = Objects.requireNonNull(label);
        return this;
    }

    /**
     * Instructs the loader to load only nodes with the given label.
     *
     * @param label May be null
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withOptionalLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Instructs the loader to load only nodes with the given {@link Label}.
     *
     * @param label Must not be null; to remove a label filter, use {@link #withAnyLabel()} instead.
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withLabel(Label label) {
        this.label = Objects.requireNonNull(label).name();
        return this;
    }

    /**
     * Instructs the loader to load any node with no restriction to any label.
     *
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withAnyLabel() {
        this.label = null;
        return this;
    }

    /**
     * Instructs the loader to load only relationships with the given relationship type.
     *
     * @param relationshipType Must not be null; to remove a type filter, use {@link #withAnyRelationshipType()} instead.
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withRelationshipType(String relationshipType) {
        this.relationshipType = Objects.requireNonNull(relationshipType);
        return this;
    }

    /**
     * Instructs the loader to load only relationships with the given relationship type.
     * If the argument is null, all relationship types will be considered.
     *
     * @param relationshipType May be null
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withOptionalRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
        return this;
    }

    /**
     * Instructs the loader to load only relationships with the given {@link RelationshipType}.
     *
     * @param relationshipType Must not be null; to remove a type filter, use {@link #withAnyRelationshipType()} instead.
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withRelationshipType(RelationshipType relationshipType) {
        this.relationshipType = Objects.requireNonNull(relationshipType).name();
        return this;
    }

    /**
     * Instructs the loader to load all relationships.
     *
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withAnyRelationshipType() {
        this.relationshipType = null;
        return this;
    }

    /**
     * Instructs the loader to load only relationships of the given direction.
     *
     * @deprecated replaced with {@link #withGraphCreateConfig(GraphCreateConfig)}.
     */
    public GraphLoader withDirection(Direction direction) {
        this.direction = direction;
        return this;
    }

    public GraphLoader withOptionalNodeProperties(PropertyMapping... nodePropertyMappings) {
        this.nodePropertyMappings.addOptionalMappings(nodePropertyMappings);
        return this;
    }

    public GraphLoader withOptionalNodeProperties(PropertyMappings nodePropertyMappings) {
        this.nodePropertyMappings.addOptionalMappings(nodePropertyMappings.stream());
        return this;
    }

    public GraphLoader withRelationshipProperties(PropertyMapping... relPropertyMappings) {
        this.relPropertyMappings.addOptionalMappings(relPropertyMappings);
        return this;
    }

    public GraphLoader withRelationshipProperties(PropertyMappings relPropertyMappings) {
        this.relPropertyMappings.addOptionalMappings(relPropertyMappings.stream());
        return this;
    }

    /**
     * If the given direction is {@link Direction#BOTH}, we instruct the loader to load the graph as undirected
     * and only outgoing relationships. This potentially reduces memory consumption for the loaded graph.
     * In any other case, we load the graph using the given direction.
     *
     * @param direction The direction requested
     * @apiNote This must only be used for algorithms, that do not require
     *     storing outgoing and incoming relationships separately.
     */
    public GraphLoader withReducedRelationshipLoading(Direction direction) {
        if (direction == Direction.BOTH && !isLoadedGraph) {
            return undirected().withDirection(Direction.OUTGOING);
        } else {
            return withDirection(direction);
        }
    }

    public GraphLoader withParams(Map<String, Object> params) {
        this.params.putAll(params);
        return this;
    }

    /**
     * provide statement to load nodes, has to return "id" and optionally "weight" or "value"
     *
     * @param nodeStatement
     */
    public GraphLoader withNodeStatement(@Language("Cypher") String nodeStatement) {
        this.label = nodeStatement;
        return this;
    }

    /**
     * provide statement to load unique relationships, has to return ids of start "source" and end-node "target" and optionally "weight"
     *
     * @param relationshipStatement
     */
    public GraphLoader withRelationshipStatement(@Language("Cypher") String relationshipStatement) {
        this.relationshipType = relationshipStatement;
        return this;
    }

    /**
     * provide batch size for parallel loading
     *
     * @param batchSize
     */
    public GraphLoader withBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    /**
     * Defines the default strategy for relationship deduplication.
     *
     * If set, this overrides the deduplication strategy for all {@link PropertyMapping}s where the deduplication strategy is not set.
     *
     * @param deduplicationStrategy strategy for handling duplicate relationships unless not explicitly specified in the property mappings
     */
    public GraphLoader withDeduplicationStrategy(DeduplicationStrategy deduplicationStrategy) {
        this.deduplicationStrategy = deduplicationStrategy;
        return this;
    }

    /**
     * Returns an instance of the factory that can be used to load the graph.
     */
    public final <T extends GraphFactory> T build(final Class<T> factoryType) {
        final MethodHandle constructor = findConstructor(factoryType);
        return factoryType.cast(invokeConstructor(constructor));
    }

    /**
     * Loads the graph using the provided GraphFactory, passing the built
     * configuration as parameters.
     * <p>
     * The chosen implementation determines the performance characteristics
     * during load and usage of the Graph.
     *
     * @return the freshly loaded graph
     */
    public Graph load(Class<? extends GraphFactory> factoryType) {
        return build(factoryType).build();
    }

    /**
     * Calculates the required memory to load the graph.
     *
     * @return
     */
    public MemoryEstimation memoryEstimation(Class<? extends GraphFactory> factoryType) {
        return build(factoryType).memoryEstimation();
    }

    private MethodHandle findConstructor(Class<?> factoryType) {
        try {
            return LOOKUP.findConstructor(factoryType, CTOR_METHOD);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private GraphFactory invokeConstructor(MethodHandle constructor) {

        final GraphSetup setup = toSetup();

        try {
            return (GraphFactory) constructor.invoke(api, setup);
        } catch (Throwable throwable) {
            throwIfUnchecked(throwable);
            throw new RuntimeException(throwable.getMessage(), throwable);
        }
    }

    public GraphSetup toSetup() {
        this.relPropertyMappings.setGlobalDeduplicationStrategy(deduplicationStrategy);
        if (createConfig == null) {
            PropertyMappings relProperties = this.relPropertyMappings.build();
            RelationshipProjections.Builder projectionsBuilder = RelationshipProjections.builder();
            if (!undirected && direction == Direction.BOTH) {
                String outIdentifier;
                String inIdentifier;
                if (StringUtils.isEmpty(relationshipType)) {
                    // TODO: fake select all
                    outIdentifier = "*->";
                    inIdentifier = "->*";
                } else {
                    outIdentifier = relationshipType + "_OUT";
                    inIdentifier = relationshipType + "_IN";
                }

                projectionsBuilder
                    .putProjection(
                        ElementIdentifier.of(outIdentifier),
                        RelationshipProjection
                            .builder()
                            .type(relationshipType)
                            .aggregation(deduplicationStrategy)
                            .properties(relProperties)
                            .projection(Projection.NATURAL)
                            .build()
                    )
                    .putProjection(
                        ElementIdentifier.of(inIdentifier),
                        RelationshipProjection
                            .builder()
                            .type(relationshipType)
                            .aggregation(deduplicationStrategy)
                            .properties(relProperties)
                            .projection(Projection.REVERSE)
                            .build()
                    );
            } else {
                Projection projection = undirected
                    ? Projection.UNDIRECTED
                    : direction == Direction.INCOMING ? Projection.REVERSE : Projection.NATURAL;

                projectionsBuilder
                    .putProjection(
                        // TODO: fake select all
                        ElementIdentifier.of(StringUtils.isEmpty(relationshipType) ? "*" : relationshipType),
                        RelationshipProjection
                            .builder()
                            .type(relationshipType)
                            .aggregation(deduplicationStrategy)
                            .properties(relProperties)
                            .projection(projection)
                            .build()
                    );
            }

            NodeProjections nodeProjections = NodeProjections.of(label);
            createConfig = ImmutableGraphCreateConfig
                .builder()
                .graphName(StringUtils.trimToEmpty(graphName))
                .concurrency(concurrency)
                .username(username)
                .nodeProperties(nodePropertyMappings.build())
                .nodeProjection(nodeProjections)
                .relationshipProjection(projectionsBuilder.build())
                .build();
        }

        return new GraphSetup(
            params,
            executorService,
            batchSize,
            log,
            logMillis,
            tracker,
            terminationFlag,
            createConfig
        );
    }
}
