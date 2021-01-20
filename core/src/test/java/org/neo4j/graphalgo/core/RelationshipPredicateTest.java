/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.compat.GraphDbApi;
import org.neo4j.graphalgo.core.loading.NativeFactory;
import org.neo4j.graphdb.Label;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.graphalgo.QueryRunner.runQuery;
import static org.neo4j.graphalgo.compat.GraphDatabaseApiProxy.findNode;
import static org.neo4j.graphalgo.compat.GraphDatabaseApiProxy.runInTransaction;

/**
 *
 *      (A)-->(B)-->(C)
 *       ^           |
 *       °-----------°
 */
class RelationshipPredicateTest {

    private static final String DB_CYPHER =
            "CREATE" +
            "  (a:Node {name: 'a'})" +
            ", (b:Node {name: 'b'})" +
            ", (c:Node {name: 'c'})" +
            ", (a)-[:TYPE]->(b)" +
            ", (b)-[:TYPE]->(c)" +
            ", (c)-[:TYPE]->(a)";

    private static final Label LABEL = Label.label("Node");

    private static long nodeA;
    private static long nodeB;
    private static long nodeC;

    private GraphDbApi db;

    @BeforeEach
    void setupGraph() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(db, DB_CYPHER);
        runInTransaction(db,tx -> {
            nodeA = findNode(db, tx, LABEL, "name", "a").getId();
            nodeB = findNode(db, tx, LABEL, "name", "b").getId();
            nodeC = findNode(db, tx, LABEL, "name", "c").getId();
        });
    }

    @AfterEach
    void shutdown() {
        db.shutdown();
    }

    @Test
    void testOutgoing() {

        final Graph graph = new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
                .build()
                .graph(NativeFactory.class);

        // A -> B
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeA),
                graph.toMappedNodeId(nodeB)
        ));

        // B -> A
        assertFalse(graph.exists(
                graph.toMappedNodeId(nodeB),
                graph.toMappedNodeId(nodeA)
        ));

        // B -> C
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeB),
                graph.toMappedNodeId(nodeC)
        ));

        // C -> B
        assertFalse(graph.exists(
                graph.toMappedNodeId(nodeC),
                graph.toMappedNodeId(nodeB)
        ));

        // C -> A
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeC),
                graph.toMappedNodeId(nodeA)
        ));

        // A -> C
        assertFalse(graph.exists(
                graph.toMappedNodeId(nodeA),
                graph.toMappedNodeId(nodeC)
        ));
    }


    @Test
    void testIncoming() {

        final Graph graph = loader()
                .globalOrientation(Orientation.REVERSE)
                .build()
                .load(NativeFactory.class);

        // B <- A
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeB),
                graph.toMappedNodeId(nodeA)
        ));

        // A <- B
        assertFalse(graph.exists(
                graph.toMappedNodeId(nodeA),
                graph.toMappedNodeId(nodeB)
        ));

        // C <- B
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeC),
                graph.toMappedNodeId(nodeB)
        ));

        // B <- C
        assertFalse(graph.exists(
                graph.toMappedNodeId(nodeB),
                graph.toMappedNodeId(nodeC)
        ));


        // A <- C
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeA),
                graph.toMappedNodeId(nodeC)
        ));

        // C <- A
        assertFalse(graph.exists(
                graph.toMappedNodeId(nodeC),
                graph.toMappedNodeId(nodeA)
        ));
    }


    @Test
    void testBoth() {

        final Graph graph = loader()
                .globalOrientation(Orientation.UNDIRECTED)
                .build()
                .graph(NativeFactory.class);

        // A -> B
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeA),
                graph.toMappedNodeId(nodeB)
        ));

        // B -> A
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeB),
                graph.toMappedNodeId(nodeA)
        ));


        // B -> C
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeB),
                graph.toMappedNodeId(nodeC)
        ));

        // C -> B
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeC),
                graph.toMappedNodeId(nodeB)
        ));

        // C -> A
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeC),
                graph.toMappedNodeId(nodeA)
        ));

        // A -> C
        assertTrue(graph.exists(
                graph.toMappedNodeId(nodeA),
                graph.toMappedNodeId(nodeC)
        ));
    }

    private StoreLoaderBuilder loader() {
        return new StoreLoaderBuilder()
                .api(db)
                .loadAnyLabel()
                .loadAnyRelationshipType();
    }
}
