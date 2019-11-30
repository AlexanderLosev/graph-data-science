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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.internal.kernel.api.exceptions.KernelException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LouvainDocTest extends ProcTestBase {

    @BeforeEach
    void setupGraph() throws KernelException {
        db = TestDatabaseCreator.createTestDatabase(builder ->
            builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, "algo.*")
        );

        final String cypher =
            "CREATE" +
            "  (nAlice:User {name: 'Alice', seed: 42})" +
            ", (nBridget:User {name: 'Bridget', seed: 42}) " +
            ", (nCharles:User {name: 'Charles', seed: 42})" +
            ", (nDoug:User {name: 'Doug'})" +
            ", (nMark:User {name: 'Mark'})" +
            ", (nMichael:User {name: 'Michael'})" +
            ", (nAlice)-[:LINK {weight: 1}]->(nBridget)" +
            ", (nAlice)-[:LINK {weight: 1}]->(nCharles)" +
            ", (nCharles)-[:LINK {weight: 1}]->(nBridget)" +
            ", (nAlice)-[:LINK {weight: 5}]->(nDoug)" +
            ", (nMark)-[:LINK {weight: 1}]->(nDoug)" +
            ", (nMark)-[:LINK {weight: 1}]->(nMichael)" +
            ", (nMichael)-[:LINK {weight: 1}]->(nMark)";


        registerProcedures(LouvainProc.class, GraphLoadProc.class);
        registerFunctions(GetNodeFunc.class);
        runQuery(cypher);
    }

    @AfterEach
    void clearCommunities() {
        db.shutdown();
        GraphCatalog.removeAllLoadedGraphs();
    }

    @Test
    void streamUnweighted() {
        String query =
            "CALL algo.beta.louvain.stream('User', 'LINK', {" +
            "   graph: 'huge'," +
            "   direction: 'BOTH'" +
            "}) YIELD nodeId, community, communities " +
            "RETURN algo.asNode(nodeId).name as name, community, communities" +
            "";

        String expected =
            "+-------------------------------------+\n" +
            "| name      | community | communities |\n" +
            "+-------------------------------------+\n" +
            "| \"Alice\"   | 2         | <null>      |\n" +
            "| \"Bridget\" | 2         | <null>      |\n" +
            "| \"Charles\" | 2         | <null>      |\n" +
            "| \"Doug\"    | 5         | <null>      |\n" +
            "| \"Mark\"    | 5         | <null>      |\n" +
            "| \"Michael\" | 5         | <null>      |\n" +
            "+-------------------------------------+\n" +
            "6 rows\n";

        String actual = runQueryAndReturn(query).resultAsString();

        assertEquals(expected, actual);
    }

    @Test
    void writeUnweighted() {
        String query =
            "CALL algo.beta.louvain('User', 'LINK', {" +
            "    graph: 'huge'," +
            "    direction: 'BOTH'," +
            "    writeProperty: 'community'" +
            "}) YIELD communityCount, modularity, modularities";

        String expected =
            "+------------------------------------------------------------+\n" +
            "| communityCount | modularity         | modularities         |\n" +
            "+------------------------------------------------------------+\n" +
            "| 2              | 0.3571428571428571 | [0.3571428571428571] |\n" +
            "+------------------------------------------------------------+\n" +
            "1 row\n";

        String actual = runQueryAndReturn(query).resultAsString();

        assertEquals(expected, actual);
    }

    @Test
    void streamWeighted() {
        String query =
            "CALL algo.beta.louvain.stream('User', 'LINK', {" +
            "  graph: 'huge'," +
            "  direction: 'BOTH'," +
            "  weightProperty: 'weight'" +
            "}) YIELD nodeId, community, communities " +
            "RETURN algo.asNode(nodeId).name as name, community, communities " +
            "ORDER BY name ASC";

        String expected =
            "+-------------------------------------+\n" +
            "| name      | community | communities |\n" +
            "+-------------------------------------+\n" +
            "| \"Alice\"   | 3         | <null>      |\n" +
            "| \"Bridget\" | 2         | <null>      |\n" +
            "| \"Charles\" | 2         | <null>      |\n" +
            "| \"Doug\"    | 3         | <null>      |\n" +
            "| \"Mark\"    | 5         | <null>      |\n" +
            "| \"Michael\" | 5         | <null>      |\n" +
            "+-------------------------------------+\n" +
            "6 rows\n";

        String actual = runQueryAndReturn(query).resultAsString();

        assertEquals(expected, actual);
    }

    @Test
    void streamSeeded() {
        String query =
            "CALL algo.beta.louvain.stream('User', 'LINK', {" +
            "  graph: 'huge'," +
            "  direction: 'BOTH'," +
            "  seedProperty: 'seed'" +
            "}) YIELD nodeId, community, communities " +
            "RETURN algo.asNode(nodeId).name as name, community, communities " +
            "ORDER BY name ASC";

        String expected =
            "+-------------------------------------+\n" +
            "| name      | community | communities |\n" +
            "+-------------------------------------+\n" +
            "| \"Alice\"   | 42        | <null>      |\n" +
            "| \"Bridget\" | 42        | <null>      |\n" +
            "| \"Charles\" | 42        | <null>      |\n" +
            "| \"Doug\"    | 47        | <null>      |\n" +
            "| \"Mark\"    | 47        | <null>      |\n" +
            "| \"Michael\" | 47        | <null>      |\n" +
            "+-------------------------------------+\n" +
            "6 rows\n";

        String actual = runQueryAndReturn(query).resultAsString();

        assertEquals(expected, actual);
    }

    @Test
    void streamIntermidiateCommunities() {
        runQuery("MATCH (n) DETACH DELETE n");
        runQuery(
            "CREATE" +
            "  (a:Node {name: 'a'})" +        // 0
            ", (b:Node {name: 'b'})" +        // 1
            ", (c:Node {name: 'c'})" +        // 2
            ", (d:Node {name: 'd'})" +        // 3
            ", (e:Node {name: 'e'})" +        // 4
            ", (f:Node {name: 'f'})" +        // 5
            ", (g:Node {name: 'g'})" +        // 6
            ", (h:Node {name: 'h'})" +        // 7
            ", (i:Node {name: 'i'})" +        // 8
            ", (j:Node {name: 'j'})" +       // 9
            ", (k:Node {name: 'k'})" +       // 10
            ", (l:Node {name: 'l'})" +       // 11
            ", (m:Node {name: 'm'})" +       // 12
            ", (n:Node {name: 'n'})" +       // 13
            ", (x:Node {name: 'x'})" +        // 14

            ", (a)-[:TYPE]->(b)" +
            ", (a)-[:TYPE]->(d)" +
            ", (a)-[:TYPE]->(f)" +
            ", (b)-[:TYPE]->(d)" +
            ", (b)-[:TYPE]->(x)" +
            ", (b)-[:TYPE]->(g)" +
            ", (b)-[:TYPE]->(e)" +
            ", (c)-[:TYPE]->(x)" +
            ", (c)-[:TYPE]->(f)" +
            ", (d)-[:TYPE]->(k)" +
            ", (e)-[:TYPE]->(x)" +
            ", (e)-[:TYPE]->(f)" +
            ", (e)-[:TYPE]->(h)" +
            ", (f)-[:TYPE]->(g)" +
            ", (g)-[:TYPE]->(h)" +
            ", (h)-[:TYPE]->(i)" +
            ", (h)-[:TYPE]->(j)" +
            ", (i)-[:TYPE]->(k)" +
            ", (j)-[:TYPE]->(k)" +
            ", (j)-[:TYPE]->(m)" +
            ", (j)-[:TYPE]->(n)" +
            ", (k)-[:TYPE]->(m)" +
            ", (k)-[:TYPE]->(l)" +
            ", (l)-[:TYPE]->(n)" +
            ", (m)-[:TYPE]->(n)"
        );

        String query =
            "CALL algo.beta.louvain.stream('', '', {" +
            "  graph: 'huge'," +
            "  undirected: true," +
            "  includeIntermediateCommunities: true" +
            "}) YIELD nodeId, community, communities " +
            "RETURN algo.asNode(nodeId).name as name, community, communities " +
            "ORDER BY name ASC";

        String expected =
            "+--------------------------------+\n" +
            "| name | community | communities |\n" +
            "+--------------------------------+\n" +
            "| \"a\"  | 14        | [3,14]      |\n" +
            "| \"b\"  | 14        | [3,14]      |\n" +
            "| \"c\"  | 14        | [14,14]     |\n" +
            "| \"d\"  | 14        | [3,14]      |\n" +
            "| \"e\"  | 14        | [14,14]     |\n" +
            "| \"f\"  | 14        | [14,14]     |\n" +
            "| \"g\"  | 7         | [7,7]       |\n" +
            "| \"h\"  | 7         | [7,7]       |\n" +
            "| \"i\"  | 7         | [7,7]       |\n" +
            "| \"j\"  | 12        | [12,12]     |\n" +
            "| \"k\"  | 12        | [12,12]     |\n" +
            "| \"l\"  | 12        | [12,12]     |\n" +
            "| \"m\"  | 12        | [12,12]     |\n" +
            "| \"n\"  | 12        | [12,12]     |\n" +
            "| \"x\"  | 14        | [14,14]     |\n" +
            "+--------------------------------+\n" +
            "15 rows\n";

        String actual = runQueryAndReturn(query).resultAsString();

        assertEquals(expected, actual);
    }

}
