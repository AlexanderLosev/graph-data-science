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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.centrality.EigenvectorCentralityProc;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.newapi.GraphCreateProc;
import org.neo4j.graphalgo.newapi.ImmutableGraphCreateConfig;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.graphalgo.QueryRunner.runInTransaction;
import static org.neo4j.graphalgo.TestSupport.AllGraphNamesTest;

class EigenvectorCentralityProcTest extends BaseProcTest {

    private static final Map<Long, Double> expected = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        ClassLoader classLoader = EigenvectorCentralityProcTest.class.getClassLoader();
        File file = new File(classLoader.getResource("got/got-s1-nodes.csv").getFile());

        db = (GraphDatabaseAPI)new TestGraphDatabaseFactory()
                .newImpermanentDatabaseBuilder(new File(UUID.randomUUID().toString()))
                .setConfig(GraphDatabaseSettings.load_csv_file_url_root,file.getParent())
                .newGraphDatabase();

        runQuery("CREATE CONSTRAINT ON (c:Character) " +
                 "ASSERT c.id IS UNIQUE;");

        runQuery("LOAD CSV WITH HEADERS FROM 'file:///got-s1-nodes.csv' AS row " +
                 "MERGE (c:Character {id: row.Id}) " +
                 "SET c.name = row.Label;");

        runQuery("LOAD CSV WITH HEADERS FROM 'file:///got-s1-edges.csv' AS row " +
                 "MATCH (source:Character {id: row.Source}) " +
                 "MATCH (target:Character {id: row.Target}) " +
                 "MERGE (source)-[rel:INTERACTS_SEASON1]->(target) " +
                 "SET rel.weight = toInteger(row.Weight);");

        registerProcedures(EigenvectorCentralityProc.class, GraphCreateProc.class);

        runInTransaction(db, () -> {
            final Label label = Label.label("Character");
            expected.put(db.findNode(label, "name", "Ned").getId(),     111.68570401574802);
            expected.put(db.findNode(label, "name", "Robert").getId(),  88.09448401574804);
            expected.put(db.findNode(label, "name", "Cersei").getId() , 84.59226401574804);
            expected.put(db.findNode(label, "name", "Catelyn").getId(), 84.51566401574803);
            expected.put(db.findNode(label, "name", "Tyrion").getId(),  82.00291401574802);
            expected.put(db.findNode(label, "name", "Joffrey").getId(), 77.67397401574803);
            expected.put(db.findNode(label, "name", "Robb").getId(),    73.56551401574802);
            expected.put(db.findNode(label, "name", "Arya").getId(),    73.32532401574804);
            expected.put(db.findNode(label, "name", "Petyr").getId(),   72.26733401574802);
            expected.put(db.findNode(label, "name", "Sansa").getId(),   71.56470401574803);
        });
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
        GraphCatalog.removeAllLoadedGraphs();
    }

    @AllGraphNamesTest
    public void testStream(String graphImpl) {
        final Map<Long, Double> actual = new HashMap<>();
        runQuery("CALL  algo.beta.graph.create('eigenvectorTest', " +
                 "'Character'," +
                 "  {" +
                 "      Character:{" +
                 "        type:'INTERACTS_SEASON1'," +
                 "        projection:'UNDIRECTED'," +
                 "        aggregation: 'DEFAULT'" +
                 "      }" +
                 "  }" +
                 ")");

        String query = "CALL algo.eigenvector.stream('eigenvectorTest'," +
                       "    {" +
                       "        direction: 'BOTH'" +
                       "    }" +
                       ") YIELD nodeId, score " +
                       "RETURN nodeId, score " +
                       "ORDER BY score DESC " +
                       "LIMIT 10";
        runQuery(query,
                row -> actual.put(
                        (Long)row.get("nodeId"),
                        (Double) row.get("score"))
        );
        assertMapEquals(expected, actual);
    }

    @Test
    void testWriteBack() {
        runQuery("CALL  algo.beta.graph.create('eigenvectorTest', " +
                 "'Character'," +
                 "  {" +
                 "      Character:{" +
                 "        type:'INTERACTS_SEASON1'," +
                 "        projection:'UNDIRECTED'," +
                 "        aggregation: 'DEFAULT'" +
                 "      }" +
                 "  }" +
                 ")");
        String query = "CALL algo.eigenvector.write('eigenvectorTest',"+
                       "    {" +
                       "        direction: 'BOTH'" +
                       "    }" +
                       ") YIELD writeMillis, write, writeProperty";
        runQuery(query,
                row -> {
                    assertTrue(row.getBoolean("write"));
                    assertEquals("eigenvector", row.getString("writeProperty"));
                    assertTrue(row.getNumber("writeMillis").intValue() >= 0, "write time not set");
        });
        assertResult("eigenvector", expected);
    }

    @Test
    void testWriteBackUnderDifferentProperty() {
        runQuery("CALL  algo.beta.graph.create('eigenvectorTest', " +
                 "'Character'," +
                 "  {" +
                 "      Character:{" +
                 "        type:'INTERACTS_SEASON1'," +
                 "        projection:'UNDIRECTED'," +
                 "        aggregation: 'DEFAULT'" +
                 "      }" +
                 "  }" +
                 ")");
        String query = "CALL algo.eigenvector.write('eigenvectorTest',"+
                       "    {" +
                       "        direction: 'BOTH', writeProperty: 'foobar'" +
                       "    }" +
                       ") YIELD writeMillis, write, writeProperty";
        runQuery(query,
            row -> {
                assertTrue(row.getBoolean("write"));
                assertEquals("foobar", row.getString("writeProperty"));
                assertTrue(row.getNumber("writeMillis").intValue() >= 0, "write time not set");
            });
        assertResult("foobar", expected);
    }

    //TODO: check if .addParameter("direction", "BOTH") is needed
    @AllGraphNamesTest
    public void testParallelWriteBack(String graphImpl) {
        String query = GdsCypher
            .call()
            .implicitCreation(ImmutableGraphCreateConfig.builder()
                .graphName("someCleverGraphName")
                .nodeProjection(NodeProjections.of("Character"))
                .relationshipProjection(RelationshipProjections.builder()
                    .putProjection(
                        ElementIdentifier.of("INTERACTS_SEASON1"),
                        RelationshipProjection
                            .builder()
                            .type("INTERACTS_SEASON1")
                            .projection(Projection.UNDIRECTED)
                            .build()
                    ).build()).build())
            .algo("algo", "eigenvector")
            .writeMode()
            .addParameter("batchSize", 3)
            .addParameter("concurrency", 2)
            .yields("writeMillis", "writeProperty", "iterations");
        runQuery(query,
                row -> assertTrue(row.getNumber("writeMillis").intValue() >= 0, "write time not set")
        );
        assertResult("eigenvector", expected);
    }

    @AllGraphNamesTest
    public void testParallelExecution(String graphImpl) {
        final Map<Long, Double> actual = new HashMap<>();
        String query = GdsCypher
            .call()
            .implicitCreation(ImmutableGraphCreateConfig.builder()
                .graphName("someCleverGraphName")
                .nodeProjection(NodeProjections.of("Character"))
                .relationshipProjection(RelationshipProjections.builder()
                    .putProjection(
                        ElementIdentifier.of("INTERACTS_SEASON1"),
                        RelationshipProjection
                            .builder()
                            .type("INTERACTS_SEASON1")
                            .projection(Projection.UNDIRECTED)
                            .build()
                    ).build()).build())
            .algo("algo", "eigenvector")
            .streamMode()
            .addParameter("batchSize", 3)
            .addParameter("concurrency", 2)
            .yields("nodeId", "score");
        query = query +
                " RETURN nodeId, score " +
                "ORDER BY score DESC " +
                "LIMIT 10";
        runQuery(query,
                row -> {
                    final long nodeId = row.getNumber("nodeId").longValue();
                    actual.put(nodeId, (Double) row.get("score"));
        });
        assertMapEquals(expected, actual);
    }
}
