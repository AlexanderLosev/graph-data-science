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
package org.neo4j.graphalgo.api;

import org.jetbrains.annotations.NotNull;
import org.neo4j.graphalgo.NodeProjections;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.PropertyMappings;
import org.neo4j.graphalgo.RelationshipProjection;
import org.neo4j.graphalgo.RelationshipProjections;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.newapi.GraphCreateConfig;
import org.neo4j.graphalgo.newapi.GraphCreateFromCypherConfig;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.logging.Log;
import org.neo4j.stream.Streams;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class GraphSetup {

    private final GraphCreateConfig createConfig;

    private final Map<String, Object> params;

    private final Log log;
    private final AllocationTracker tracker;
    private final TerminationFlag terminationFlag;

    // the executor service for parallel execution. null means single threaded evaluation.
    private final ExecutorService executor;

    /**
     * @param executor  the executor. null means single threaded evaluation
     */
    public GraphSetup(
        Map<String, Object> params,
        ExecutorService executor,
        Log log,
        AllocationTracker tracker,
        TerminationFlag terminationFlag,
        GraphCreateConfig createConfig
    ) {
        this.params = params == null ? Collections.emptyMap() : params;
        this.executor = executor;
        this.log = log;
        this.tracker = tracker;
        this.terminationFlag = terminationFlag;
        this.createConfig = createConfig;
    }

    public String username() {
        return createConfig.username();
    }

    public String name() {
        return createConfig.graphName();
    }

    public int concurrency() {
        if (!loadConcurrent()) {
            return 1;
        }
        return createConfig.concurrency();
    }

    public @NotNull String relationshipType() {
        return createConfig.relationshipProjection().typeFilter();
    }

    public @NotNull NodeProjections nodeProjections() {
        return createConfig.nodeProjection();
    }

    public @NotNull RelationshipProjections relationshipProjections() {
        return createConfig.relationshipProjection();
    }

    public Optional<String> nodeQuery() {
        return createConfig instanceof GraphCreateFromCypherConfig
            ? Optional.ofNullable(((GraphCreateFromCypherConfig) createConfig).nodeQuery())
            : Optional.empty();
    }

    public Optional<String> relationshipQuery() {
        return createConfig instanceof GraphCreateFromCypherConfig
            ? Optional.ofNullable(((GraphCreateFromCypherConfig) createConfig).relationshipQuery())
            : Optional.empty();
    }

    /**
     * @deprecated There is no global relationship property anymore
     */
    @Deprecated
    public Optional<Double> relationshipDefaultPropertyValue() {
        return createConfig.relationshipProjection().allProjections().stream().flatMap(
            f -> Streams.ofOptional(f.properties().defaultWeight())
        ).findFirst();
    }

    /**
     * @deprecated There is no global node property configuration anymore
     */
    @Deprecated
    public PropertyMappings nodePropertyMappings() {
        Map<String, List<PropertyMapping>> groupedPropertyMappings = createConfig.nodeProjection()
            .allProjections()
            .stream()
            .flatMap(e -> e.properties().stream())
            .collect(Collectors.groupingBy(PropertyMapping::propertyKey));

        PropertyMappings.Builder builder = PropertyMappings.builder();
        groupedPropertyMappings.values().stream()
            .map(Iterables::first)
            .forEach(builder::addMapping);

        // Necessary for Cypher projections
        createConfig.nodeProperties().forEach(builder::addMapping);

        return builder.build();
    }

    /**
     * @deprecated There is no global relationship property configuration anymore
     */
    @Deprecated
    public PropertyMappings relationshipPropertyMappings() {
        Map<String, List<PropertyMapping>> groupedPropertyMappings = createConfig.relationshipProjection()
            .allProjections()
            .stream()
            .flatMap(e -> e.properties().stream())
            .collect(Collectors.groupingBy(PropertyMapping::propertyKey));

        PropertyMappings.Builder builder = PropertyMappings.builder();
        groupedPropertyMappings.values().stream()
            .map(Iterables::first)
            .forEach(builder::addMapping);

        // Necessary for Cypher projections
        createConfig.relationshipProperties().forEach(builder::addMapping);

        return builder.build();
    }

    /**
     * @deprecated There is no global relationship aggregation strategy anymore
     */
    @Deprecated
    public Aggregation aggregation() {
        return createConfig
            .relationshipProjection()
            .allProjections()
            .stream()
            .map(RelationshipProjection::aggregation)
            .findFirst()
            .orElse(Aggregation.DEFAULT);
    }

    public Map<String, Object> params() {
        return params;
    }

    public Log log() {
        return log;
    }

    public long logMillis() {
        return -1;
    }

    public AllocationTracker tracker() {
        return tracker;
    }

    public TerminationFlag terminationFlag() {
        return terminationFlag;
    }

    public ExecutorService executor() {
        return executor;
    }

    private boolean loadConcurrent() {
        return executor != null;
    }
}
