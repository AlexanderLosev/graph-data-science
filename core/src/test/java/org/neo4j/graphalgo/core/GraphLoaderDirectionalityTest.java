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
import org.neo4j.graphalgo.CypherLoaderBuilder;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestSupport.AllGraphTypesTest;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.loading.CypherGraphFactory;
import org.neo4j.graphalgo.newapi.GraphCreateFromCypherConfig;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.GraphHelper.assertRelationships;
import static org.neo4j.graphalgo.QueryRunner.runInTransaction;
import static org.neo4j.graphalgo.QueryRunner.runQuery;

class GraphLoaderDirectionalityTest {

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node)" +
        ", (b:Node)" +
        ", (c:Node)" +
        ", (d:Node) " +
        ", (a)-[:REL]->(a)" +
        ", (b)-[:REL]->(b)" +
        ", (a)-[:REL]->(b)" +
        ", (b)-[:REL]->(c)" +
        ", (b)-[:REL]->(d)";

    private static final String RELATIONSHIP_QUERY_BOTH = "MATCH (n)--(m) RETURN id(n) AS source, id(m) AS target";
    private static final String RELATIONSHIP_QUERY_OUTGOING = "MATCH (n)-->(m) RETURN id(n) AS source, id(m) AS target";
    private static final String RELATIONSHIP_QUERY_INCOMING = "MATCH (n)<--(m) RETURN id(n) AS source, id(m) AS target";
    private static final String RELATIONSHIP_QUERY_UNDIRECTED =
        "MATCH (n)-->(m)" +
        "RETURN id(n) AS source, id(m) AS target " +
        "UNION ALL " +
        "MATCH (n)<--(m) " +
        "RETURN id(n) AS source, id(m) AS target";

    private GraphDatabaseAPI db;

    @BeforeEach
    void setUp() {
        db = TestDatabaseCreator.createTestDatabase();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @AllGraphTypesTest
    void loadUndirected(Class<? extends GraphFactory> graphImpl) {
        Graph graph = loadDirectedGraph(graphImpl, RELATIONSHIP_QUERY_BOTH, Projection.UNDIRECTED);
        assertEquals(4L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 1);
        assertRelationships(graph, 1, 0, 1, 2, 3);
        assertRelationships(graph, 2, 1);
        assertRelationships(graph, 3, 1);
    }

    @AllGraphTypesTest
    void loadOutgoing(Class<? extends GraphFactory> graphImpl) {
        Graph graph = loadDirectedGraph(graphImpl, RELATIONSHIP_QUERY_OUTGOING, Projection.NATURAL);

        assertEquals(4L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 1);
        assertRelationships(graph, 1, 1, 2, 3);
        assertRelationships(graph, 2);
        assertRelationships(graph, 3);
    }

    @AllGraphTypesTest
    void loadIncoming(Class<? extends GraphFactory> graphImpl) {
        Graph graph = loadDirectedGraph(graphImpl, RELATIONSHIP_QUERY_INCOMING, Projection.REVERSE);

        assertEquals(4L, graph.nodeCount());
        if (graphImpl == CypherGraphFactory.class) {
            assertRelationships(graph, 0, 0);
            assertRelationships(graph, 1, 0, 1);
            assertRelationships(graph, 2, 1);
            assertRelationships(graph, 3, 1);
        } else {
            assertRelationships(graph, 0, 0);
            assertRelationships(graph, 1, 0, 1);
            assertRelationships(graph, 2, 1);
            assertRelationships(graph, 3, 1);
        }
    }

    @AllGraphTypesTest
    void loadOutgoingWithoutAggregation(Class<? extends GraphFactory> graphImpl) {
        Graph graph = loadDirectedGraph(graphImpl, RELATIONSHIP_QUERY_OUTGOING, Projection.NATURAL);

        assertEquals(4L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 1);
        assertRelationships(graph, 1, 1, 2, 3);
        assertRelationships(graph, 2);
        assertRelationships(graph, 3);
    }

    @AllGraphTypesTest
    void loadUndirectedWithAggregation(Class<? extends GraphFactory> graphImpl) {
        Graph graph = loadUndirectedGraph(graphImpl, Aggregation.SINGLE);

        assertEquals(4L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 1);
        assertRelationships(graph, 1, 0, 1, 2, 3);
        assertRelationships(graph, 2, 1);
        assertRelationships(graph, 3, 1);
    }

    @AllGraphTypesTest
    void loadUndirectedWithoutAggregation(Class<? extends GraphFactory> graphImpl) {
        Graph graph = loadUndirectedGraph(graphImpl, Aggregation.NONE);

        assertEquals(4L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 0, 1);
        assertRelationships(graph, 1, 0, 1, 1, 2, 3);
        assertRelationships(graph, 2, 1);
        assertRelationships(graph, 3, 1);
    }

    @AllGraphTypesTest
    void loadUndirectedNodeWithSelfReference(Class<? extends GraphFactory> graphImpl) {
        runUndirectedNodeWithSelfReference(
            graphImpl,
            "CREATE" +
            "  (a:Node)" +
            ", (b:Node) " +
            ", (a)-[:REL]->(a)" +
            ", (a)-[:REL]->(b)"
        );
    }

    @AllGraphTypesTest
    void loadUndirectedNodeWithSelfReference2(Class<? extends GraphFactory> graphImpl) {
        runUndirectedNodeWithSelfReference(
            graphImpl,
            "CREATE" +
            "  (a:Node)" +
            ", (b:Node) " +
            ", (a)-[:REL]->(b)" +
            ", (a)-[:REL]->(a)"
        );
    }

    @AllGraphTypesTest
    void loadUndirectedNodesWithMultipleSelfReferences(Class<? extends GraphFactory> graphImpl) {
        runUndirectedNodesWithMultipleSelfReferences(
            graphImpl,
            "CREATE" +
            "  (a:Node)" +
            ", (b:Node)" +
            ", (c:Node)" +
            ", (d:Node) " +
            ", (a)-[:REL]->(a)" +
            ", (b)-[:REL]->(b)" +
            ", (a)-[:REL]->(b)" +
            ", (b)-[:REL]->(c)" +
            ", (b)-[:REL]->(d)"
        );
    }

    @AllGraphTypesTest
    void loadUndirectedNodesWithMultipleSelfReferences2(Class<? extends GraphFactory> graphImpl) {
        runUndirectedNodesWithMultipleSelfReferences(
            graphImpl,
            "CREATE" +
            "  (a:Node)" +
            ", (b:Node)" +
            ", (c:Node)" +
            ", (d:Node) " +
            ", (a)-[:REL]->(b)" +
            ", (a)-[:REL]->(a)" +
            ", (b)-[:REL]->(c)" +
            ", (b)-[:REL]->(d)" +
            ", (b)-[:REL]->(b)"
        );
    }

    private void runUndirectedNodeWithSelfReference(Class<? extends GraphFactory> graphImpl, String cypher) {
        Graph graph = loadGraph(
            cypher,
            graphImpl,
            RELATIONSHIP_QUERY_UNDIRECTED,
            Projection.UNDIRECTED,
            Aggregation.SINGLE
        );

        assertEquals(2L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 1);
        assertRelationships(graph, 1, 0);
    }

    private void runUndirectedNodesWithMultipleSelfReferences(Class<? extends GraphFactory> graphImpl, String cypher) {
        Graph graph = loadGraph(
            cypher,
            graphImpl,
            RELATIONSHIP_QUERY_UNDIRECTED,
            Projection.UNDIRECTED,
            Aggregation.SINGLE
        );

        assertEquals(4L, graph.nodeCount());
        assertRelationships(graph, 0, 0, 1);
        assertRelationships(graph, 1, 0, 1, 2, 3);
        assertRelationships(graph, 2, 1);
        assertRelationships(graph, 3, 1);
    }

    private Graph loadDirectedGraph(
        Class<? extends GraphFactory> graphImpl,
        String relationshipQuery,
        Projection projection
    ) {
        return loadGraph(DB_CYPHER, graphImpl, relationshipQuery, projection, Aggregation.SINGLE);
    }

    private Graph loadUndirectedGraph(
        Class<? extends GraphFactory> graphImpl,
        Aggregation aggregation
    ) {
        return loadGraph(DB_CYPHER, graphImpl,
            GraphLoaderDirectionalityTest.RELATIONSHIP_QUERY_UNDIRECTED, Projection.UNDIRECTED, aggregation
        );
    }

    private Graph loadGraph(
        String dbQuery,
        Class<? extends GraphFactory> graphImpl,
        String relationshipQuery,
        Projection projection,
        Aggregation aggregation
    ) {
        runQuery(db, dbQuery);

        ModernGraphLoader graphLoader;

        if (graphImpl == CypherGraphFactory.class) {
            graphLoader = new CypherLoaderBuilder()
                .api(db)
                .nodeQuery(GraphCreateFromCypherConfig.ALL_NODES_QUERY)
                .relationshipQuery(relationshipQuery)
                .globalAggregation(aggregation)
                .build();
        } else {
            graphLoader = new StoreLoaderBuilder()
                .api(db)
                .loadAnyLabel()
                .loadAnyRelationshipType()
                .globalProjection(projection)
                .globalAggregation(aggregation)
                .build();
        }
        return runInTransaction(
            db,
            () -> graphLoader.load(graphImpl)
        );
    }

}
