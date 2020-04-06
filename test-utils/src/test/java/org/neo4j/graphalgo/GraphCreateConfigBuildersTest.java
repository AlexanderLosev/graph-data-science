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

package org.neo4j.graphalgo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphalgo.config.GraphCreateFromCypherConfig;
import org.neo4j.graphalgo.config.GraphCreateFromStoreConfig;
import org.neo4j.graphalgo.config.ImmutableGraphCreateFromCypherConfig;
import org.neo4j.graphalgo.config.ImmutableGraphCreateFromStoreConfig;
import org.neo4j.graphalgo.core.Aggregation;

import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.AbstractProjections.PROJECT_ALL;
import static org.neo4j.graphalgo.config.GraphCreateFromCypherConfig.ALL_NODES_QUERY;
import static org.neo4j.graphalgo.config.GraphCreateFromCypherConfig.ALL_RELATIONSHIPS_QUERY;

class GraphCreateConfigBuildersTest {

    static Stream<Arguments> storeConfigs() {
        return Stream.of(
            Arguments.arguments(
                new StoreConfigBuilder().userName("foo").graphName("bar").build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("foo").graphName("bar")
                    .nodeProjections(NodeProjections.empty())
                    .relationshipProjections(RelationshipProjections.empty())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new StoreConfigBuilder().loadAnyLabel().loadAnyRelationshipType().build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("").graphName("")
                    .nodeProjections(NodeProjections.builder()
                        .putProjection(PROJECT_ALL, NodeProjection.all())
                        .build())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(PROJECT_ALL, RelationshipProjection.all())
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new StoreConfigBuilder().addNodeLabel("Foo").addRelationshipType("BAR").build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("").graphName("")
                    .nodeProjections(NodeProjections.builder()
                        .putProjection(ElementIdentifier.of("Foo"), NodeProjection.of("Foo", PropertyMappings.of()))
                        .build())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(
                            ElementIdentifier.of("BAR"),
                            RelationshipProjection.of("BAR", Orientation.NATURAL, Aggregation.DEFAULT)
                        )
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new StoreConfigBuilder().addNodeProjection(NodeProjection.fromString("Foo")).addRelationshipType("BAR").build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("").graphName("")
                    .nodeProjections(NodeProjections.builder()
                        .putProjection(ElementIdentifier.of("Foo"), NodeProjection.of("Foo", PropertyMappings.of()))
                        .build())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(
                            ElementIdentifier.of("BAR"),
                            RelationshipProjection.of("BAR", Orientation.NATURAL, Aggregation.DEFAULT)
                        )
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new StoreConfigBuilder().addNodeLabel("Foo").addRelationshipType("BAR").globalProjection(Orientation.UNDIRECTED).build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("").graphName("")
                    .nodeProjections(NodeProjections.builder()
                        .putProjection(ElementIdentifier.of("Foo"), NodeProjection.of("Foo", PropertyMappings.of()))
                        .build())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(
                            ElementIdentifier.of("BAR"),
                            RelationshipProjection.of("BAR", Orientation.UNDIRECTED, Aggregation.DEFAULT)
                        )
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new StoreConfigBuilder()
                    .addNodeLabel("Foo")
                    .addRelationshipType("BAR")
                    .addRelationshipProjection(RelationshipProjection.of("BAZ", Orientation.NATURAL))
                    .globalProjection(Orientation.UNDIRECTED)
                    .build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("").graphName("")
                    .nodeProjections(NodeProjections.builder()
                        .putProjection(ElementIdentifier.of("Foo"), NodeProjection.of("Foo", PropertyMappings.of()))
                        .build())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(
                            ElementIdentifier.of("BAR"),
                            RelationshipProjection.of("BAR", Orientation.UNDIRECTED, Aggregation.DEFAULT)
                        )
                        .putProjection(
                            ElementIdentifier.of("BAZ"),
                            RelationshipProjection.of("BAZ", Orientation.NATURAL, Aggregation.DEFAULT)
                        )
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new StoreConfigBuilder()
                    .addNodeLabel("Foo")
                    .addRelationshipType("BAR")
                    .nodeProperties(Collections.singletonList(PropertyMapping.of("nProp", 23.0D)))
                    .relationshipProperties(Collections.singletonList(PropertyMapping.of("rProp", 42.0D)))
                    .build(),
                ImmutableGraphCreateFromStoreConfig.builder().username("").graphName("")
                    .nodeProjections(NodeProjections.builder()
                        .putProjection(
                            ElementIdentifier.of("Foo"),
                            NodeProjection.builder()
                                .label("Foo")
                                .addProperty(PropertyMapping.of("nProp", 23.0D))
                                .build())
                        .build())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(
                            ElementIdentifier.of("BAR"),
                            RelationshipProjection.builder()
                                .type("BAR")
                                .addProperty(PropertyMapping.of("rProp", 42.0D))
                                .build()
                        )
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            )
        );
    }

    @ParameterizedTest
    @MethodSource("storeConfigs")
    void testStoreConfigBuilder(GraphCreateFromStoreConfig actual, GraphCreateFromStoreConfig expected) {
        assertEquals(expected, actual);
    }

    static Stream<Arguments> cypherConfigs() {
        return Stream.of(
            Arguments.arguments(
                new CypherConfigBuilder()
                    .loadAnyLabel()
                    .loadAnyRelationshipType()
                    .build(),
                ImmutableGraphCreateFromCypherConfig.builder().username("").graphName("")
                    .nodeQuery(ALL_NODES_QUERY)
                    .relationshipQuery(ALL_RELATIONSHIPS_QUERY)
                    .nodeProjections(NodeProjections.empty())
                    .relationshipProjections(RelationshipProjections.empty())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new CypherConfigBuilder().userName("foo").graphName("bar")
                    .loadAnyLabel()
                    .loadAnyRelationshipType()
                    .build(),
                ImmutableGraphCreateFromCypherConfig.builder().username("foo").graphName("bar")
                    .nodeQuery(ALL_NODES_QUERY)
                    .relationshipQuery(ALL_RELATIONSHIPS_QUERY)
                    .nodeProjections(NodeProjections.empty())
                    .relationshipProjections(RelationshipProjections.empty())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new CypherConfigBuilder().userName("foo").graphName("bar")
                    .nodeQuery("MATCH (n:Foo) RETURN id(n) AS id")
                    .relationshipQuery("MATCH (a)-->(b) RETURN id(a) AS source, id(b) AS target")
                    .loadAnyRelationshipType()
                    .build(),
                ImmutableGraphCreateFromCypherConfig.builder().username("foo").graphName("bar")
                    .nodeQuery("MATCH (n:Foo) RETURN id(n) AS id")
                    .relationshipQuery("MATCH (a)-->(b) RETURN id(a) AS source, id(b) AS target")
                    .nodeProjections(NodeProjections.empty())
                    .relationshipProjections(RelationshipProjections.empty())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of())
                    .build()
            ),
            Arguments.arguments(
                new CypherConfigBuilder().userName("foo").graphName("bar")
                    .nodeQuery("MATCH (n:Foo) RETURN id(n) AS id, COALESCE(n.nProp, 23.D) AS nProp")
                    .relationshipQuery("MATCH (a)-->(b) RETURN id(a) AS source, id(b) AS target")
                    .addRelationshipProperty(PropertyMapping.of("rProp", 42.0D))
                    .loadAnyRelationshipType()
                    .build(),
                ImmutableGraphCreateFromCypherConfig.builder().username("foo").graphName("bar")
                    .nodeQuery("MATCH (n:Foo) RETURN id(n) AS id, COALESCE(n.nProp, 23.D) AS nProp")
                    .relationshipQuery("MATCH (a)-->(b) RETURN id(a) AS source, id(b) AS target")
                    .nodeProjections(NodeProjections.empty())
                    .relationshipProjections(RelationshipProjections.empty())
                    .relationshipProperties(PropertyMappings.of(PropertyMapping.of("rProp", 42.0D)))
                    .build()
            ),
            Arguments.arguments(
                new CypherConfigBuilder()
                    .loadAnyLabel()
                    .loadAnyRelationshipType()
                    .addRelationshipProperty(PropertyMapping.of("foo", 42.0D))
                    .globalAggregation(Aggregation.MAX)
                    .build(),
                ImmutableGraphCreateFromCypherConfig.builder().username("").graphName("")
                    .nodeQuery(ALL_NODES_QUERY)
                    .relationshipQuery(ALL_RELATIONSHIPS_QUERY)
                    .nodeProjections(NodeProjections.empty())
                    .relationshipProjections(RelationshipProjections.builder()
                        .putProjection(PROJECT_ALL, RelationshipProjection.of("*", Orientation.NATURAL, Aggregation.MAX))
                        .build())
                    .nodeProperties(PropertyMappings.of())
                    .relationshipProperties(PropertyMappings.of(PropertyMapping.of("foo", 42.0D, Aggregation.MAX)))
                    .build()
            )
        );
    }

    @ParameterizedTest
    @MethodSource("cypherConfigs")
    void testCypherConfigBuilder(GraphCreateFromCypherConfig actual, GraphCreateFromCypherConfig expected) {
        assertEquals(expected, actual);
    }

}
