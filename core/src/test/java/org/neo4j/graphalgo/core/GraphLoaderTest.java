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
package org.neo4j.graphalgo.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.PropertyMappings;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestGraphLoader;
import org.neo4j.graphalgo.TestSupport.AllGraphTypesTest;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphStoreFactory;
import org.neo4j.graphalgo.compat.GraphDbApi;
import org.neo4j.graphalgo.core.loading.NativeFactory;
import org.neo4j.graphalgo.core.utils.TerminationFlag;

import static org.neo4j.graphalgo.QueryRunner.runQuery;
import static org.neo4j.graphalgo.TestGraph.Builder.fromGdl;
import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;
import static org.neo4j.graphalgo.TestSupport.assertTransactionTermination;

class GraphLoaderTest {

    public static final String DB_CYPHER =
        "CREATE" +
        "  (n1:Node1 {prop1: 1})" +
        ", (n2:Node2 {prop2: 2})" +
        ", (n3:Node3 {prop3: 3})" +
        ", (n1)-[:REL1 {prop1: 1}]->(n2)" +
        ", (n1)-[:REL2 {prop2: 2}]->(n3)" +
        ", (n2)-[:REL1 {prop3: 3, weight: 42}]->(n3)" +
        ", (n2)-[:REL3 {prop4: 4, weight: 1337}]->(n3)";

    private GraphDbApi db;

    @BeforeEach
    void setup() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(db, DB_CYPHER);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @AllGraphTypesTest
    void testAnyLabel(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db).withDefaultAggregation(Aggregation.SINGLE).graph(graphStoreFactory);
        assertGraphEquals(graph, fromGdl("(a)-->(b), (a)-->(c), (b)-->(c)"));
    }

    @AllGraphTypesTest
    void testWithLabel(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db).withLabels("Node1").graph(graphStoreFactory);
        assertGraphEquals(graph, fromGdl("()"));
    }

    @AllGraphTypesTest
    void testWithMultipleLabels(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db).withLabels("Node1", "Node2").graph(graphStoreFactory);
        assertGraphEquals(graph, fromGdl("(a)-->(b)"));
    }

    @AllGraphTypesTest
    void testWithMultipleLabelsAndProperties(Class<? extends GraphStoreFactory> graphStoreFactory) {
        PropertyMappings properties = PropertyMappings.of(PropertyMapping.of("prop1", 42.0));
        PropertyMappings multipleProperties = PropertyMappings.of(
            PropertyMapping.of("prop1", 42.0),
            PropertyMapping.of("prop2", 42.0)
        );

        Graph graph = TestGraphLoader.from(db)
            .withLabels("Node1", "Node2")
            .withNodeProperties(properties)
            .graph(graphStoreFactory);
        assertGraphEquals(fromGdl("(a {prop1: 1.0})-->(b {prop1: 42.0})"), graph);

        graph = TestGraphLoader.from(db)
            .withLabels("Node1", "Node2")
            .withNodeProperties(multipleProperties)
            .graph(graphStoreFactory);
        assertGraphEquals(fromGdl("(a {prop1: 1.0, prop2: 42.0})-->(b {prop1: 42.0, prop2: 2.0})"), graph);
    }

    @AllGraphTypesTest
    void testAnyRelation(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db).withDefaultAggregation(Aggregation.SINGLE).graph(graphStoreFactory);
        assertGraphEquals(graph, fromGdl("(a)-->(b), (a)-->(c), (b)-->(c)"));
    }

    @AllGraphTypesTest
    void testWithBothWeightedRelationship(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db)
            .withRelationshipTypes("REL3")
            .withRelationshipProperties(PropertyMapping.of("weight", 1.0))
            .graph(graphStoreFactory);

        assertGraphEquals(graph, fromGdl("(), ()-[{w:1337}]->()"));
    }

    @AllGraphTypesTest
    void testWithOutgoingRelationship(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db)
            .withRelationshipTypes("REL3")
            .graph(graphStoreFactory);
        assertGraphEquals(graph, fromGdl("(), ()-->()"));
    }

    @AllGraphTypesTest
    void testWithNodeProperties(Class<? extends GraphStoreFactory> graphStoreFactory) {
        PropertyMappings nodePropertyMappings = PropertyMappings.of(
            PropertyMapping.of("prop1", "prop1", 0D),
            PropertyMapping.of("prop2", "prop2", 0D),
            PropertyMapping.of("prop3", "prop3", 0D)
        );

        Graph graph = TestGraphLoader
            .from(db)
            .withNodeProperties(nodePropertyMappings)
            .withDefaultAggregation(Aggregation.SINGLE)
            .graph(graphStoreFactory);

        assertGraphEquals(graph, fromGdl("(a {prop1: 1, prop2: 0, prop3: 0})" +
                                         "(b {prop1: 0, prop2: 2, prop3: 0})" +
                                         "(c {prop1: 0, prop2: 0, prop3: 3})" +
                                         "(a)-->(b), (a)-->(c), (b)-->(c)"));
    }

    @AllGraphTypesTest
    void testWithRelationshipProperty(Class<? extends GraphStoreFactory> graphStoreFactory) {
        Graph graph = TestGraphLoader.from(db)
            .withRelationshipProperties(PropertyMapping.of("weight","prop1", 1337.42))
            .withDefaultAggregation(Aggregation.SINGLE)
            .graph(graphStoreFactory);
        assertGraphEquals(graph, fromGdl("(a)-[{w: 1}]->(b), (a)-[{w: 1337.42D}]->(c), (b)-[{w: 1337.42D}]->(c)"));
    }

    @Test
    void stopsImportingWhenTransactionHasBeenTerminated() {
        TerminationFlag terminationFlag = () -> false;
        assertTransactionTermination(
            () -> new StoreLoaderBuilder()
                .api(db)
                .loadAnyLabel()
                .loadAnyRelationshipType()
                .terminationFlag(terminationFlag)
                .build()
                .load(NativeFactory.class)
        );
    }
}
