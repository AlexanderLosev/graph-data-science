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
package org.neo4j.graphalgo.impl.betweenness;

import org.immutables.value.Value;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.config.WriteConfig;

public interface BaseBetweennessCentralityConfig extends WriteConfig {

    @Value.Default
    default String writeProperty() {
        return "centrality";
    }

    @Value.Check
    default void validate() {
        implicitCreateConfig().ifPresent(this::validate);
    }

    @Configuration.Ignore
    default void validate(GraphCreateConfig graphCreateConfig) {
        if (graphCreateConfig.relationshipProjections().projections().size() > 1) {
            throw new IllegalArgumentException(
                "Betweenness Centrality does not support multiple relationship projections.");
        }
    }

    @Configuration.Ignore
    default boolean undirected() {
        return implicitCreateConfig()
            .map(config -> config
                .relationshipProjections()
                .projections()
                .values()
                .stream()
                .anyMatch(p -> p.orientation() == Orientation.UNDIRECTED))
            .orElse(false);
    }

}
