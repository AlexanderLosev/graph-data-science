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

package org.neo4j.graphalgo.beta.k1coloring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.BaseProcTest;
import org.neo4j.graphalgo.GetNodeFunc;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.newapi.GraphCreateProc;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class K1ColoringDocTest extends BaseProcTest {

    private static final String NL = System.lineSeparator();
    public static final String DB_CYPHER =
        "CREATE" +
        "  (alice:User {name: 'Alice'})" +
        ", (bridget:User {name: 'Bridget'})" +
        ", (charles:User {name: 'Charles'})" +
        ", (doug:User {name: 'Doug'})" +
        ", (alice)-[:LINK]->(bridget)" +
        ", (alice)-[:LINK]->(charles)" +
        ", (alice)-[:LINK]->(doug)" +
        ", (bridget)-[:LINK]->(charles)";

    @BeforeEach
    void setupGraph() throws Exception {
        db = TestDatabaseCreator.createTestDatabase(builder ->
            builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, "gds.util.*")
        );

        registerProcedures(K1ColoringWriteProc.class, K1ColoringStreamProc.class);
        registerProcedures(GraphCreateProc.class);
        registerFunctions(GetNodeFunc.class);

        runQuery(DB_CYPHER);
    }

    @AfterEach
    void tearDown() {
        GraphCatalog.removeAllLoadedGraphs();
        db.shutdown();
    }

    @Test
    void shouldStream() {
        String createQuery = "CALL gds.graph.create('myGraph', 'User', 'LINK')";
        String query = "CALL gds.beta.k1coloring.stream('myGraph')" +
                       " YIELD nodeId, color" +
                       " RETURN gds.util.asNode(nodeId).name AS name, color ORDER BY name";

        runQuery(createQuery);
        String actual = runQuery(query, Result::resultAsString);
        String expected =
            "+-------------------+" + NL +
            "| name      | color |" + NL +
            "+-------------------+" + NL +
            "| \"Alice\"   | 2     |" + NL +
            "| \"Bridget\" | 1     |" + NL +
            "| \"Charles\" | 0     |" + NL +
            "| \"Doug\"    | 0     |" + NL +
            "+-------------------+" + NL +
            "4 rows" + NL;

        assertEquals(expected, actual);
    }

    @Test
    void shouldWrite() {
        String createQuery = "CALL gds.graph.create('myGraph', 'User', 'LINK')";
        String query = "CALL gds.beta.k1coloring.write('myGraph', {writeProperty: 'color'})" +
                       " YIELD nodes" +
                       " RETURN nodes";
        String verifyQuery = "MATCH (n:User) RETURN n.name AS name, n.color AS color ORDER BY name";

        runQuery(createQuery);
        String actual = runQuery(query, Result::resultAsString);
        String expected =
            "+-------+" + NL +
            "| nodes |" + NL +
            "+-------+" + NL +
            "| 4     |" + NL +
            "+-------+" + NL +
            "1 row" + NL;

        assertEquals(expected, actual);

        String verify = runQuery(verifyQuery, Result::resultAsString);
        String expectedVerify =
            "+-------------------+" + NL +
            "| name      | color |" + NL +
            "+-------------------+" + NL +
            "| \"Alice\"   | 2     |" + NL +
            "| \"Bridget\" | 1     |" + NL +
            "| \"Charles\" | 0     |" + NL +
            "| \"Doug\"    | 0     |" + NL +
            "+-------------------+" + NL +
            "4 rows" + NL;

        assertEquals(expectedVerify, verify);
    }

    @Test
    void shouldStreamCypher() {
        String query = "CALL gds.beta.k1coloring.stream({" +
                       "  nodeQuery: 'MATCH (n:User) RETURN id(n) AS id'," +
                       "  relationshipQuery: 'MATCH (s:User)-[:LINK]->(t:User) RETURN id(s) AS source, id(t) AS target'" +
                       "})" +
                       " YIELD nodeId, color" +
                       " RETURN gds.util.asNode(nodeId).name AS name, color ORDER BY name";

        String actual = runQuery(query, Result::resultAsString);
        String expected =
            "+-------------------+" + NL +
            "| name      | color |" + NL +
            "+-------------------+" + NL +
            "| \"Alice\"   | 2     |" + NL +
            "| \"Bridget\" | 1     |" + NL +
            "| \"Charles\" | 0     |" + NL +
            "| \"Doug\"    | 0     |" + NL +
            "+-------------------+" + NL +
            "4 rows" + NL;

        assertEquals(expected, actual);
    }

    @Test
    void shouldWriteCypher() {
        String query = "CALL gds.beta.k1coloring.write({" +
                       "  nodeQuery: 'MATCH (n:User) RETURN id(n) AS id'," +
                       "  relationshipQuery: 'MATCH (s:User)-[:LINK]->(t:User) RETURN id(s) AS source, id(t) AS target'," +
                       "  writeProperty: 'color'" +
                       "})" +
                       " YIELD nodes" +
                       " RETURN nodes";
        String verifyQuery = "MATCH (n:User) RETURN n.name AS name, n.color AS color ORDER BY name";

        String actual = runQuery(query, Result::resultAsString);
        String expected =
            "+-------+" + NL +
            "| nodes |" + NL +
            "+-------+" + NL +
            "| 4     |" + NL +
            "+-------+" + NL +
            "1 row" + NL;

        assertEquals(expected, actual);

        String verify = runQuery(verifyQuery, Result::resultAsString);
        String expectedVerify =
            "+-------------------+" + NL +
            "| name      | color |" + NL +
            "+-------------------+" + NL +
            "| \"Alice\"   | 2     |" + NL +
            "| \"Bridget\" | 1     |" + NL +
            "| \"Charles\" | 0     |" + NL +
            "| \"Doug\"    | 0     |" + NL +
            "+-------------------+" + NL +
            "4 rows" + NL;

        assertEquals(expectedVerify, verify);
    }
}
