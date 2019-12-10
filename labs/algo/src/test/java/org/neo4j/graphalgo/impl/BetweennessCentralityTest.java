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
package org.neo4j.graphalgo.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.HugeGraphFactory;
import org.neo4j.graphalgo.impl.betweenness.MaxDepthBetweennessCentrality;

/**
 *  (A)-->(B)-->(C)-->(D)-->(E)
 *  0.0   3.0   4.0   3.0   0.0
 */
class BetweennessCentralityTest extends AlgoTestBase {

    private Graph graph;

    @BeforeEach
    void setupGraph() {

        String cypher =
                "CREATE (a:Node {name:'a'})\n" +
                "CREATE (b:Node {name:'b'})\n" +
                "CREATE (c:Node {name:'c'})\n" +
                "CREATE (d:Node {name:'d'})\n" +
                "CREATE (e:Node {name:'e'})\n" +
                "CREATE" +
                " (a)-[:TYPE]->(b),\n" +
                " (b)-[:TYPE]->(c),\n" +
                " (c)-[:TYPE]->(d),\n" +
                " (d)-[:TYPE]->(e)";


        db = TestDatabaseCreator.createTestDatabase();

        runQuery(cypher);

        graph = new GraphLoader(db)
                .withAnyRelationshipType()
                .withAnyLabel()
                .load(HugeGraphFactory.class);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
        graph = null;
    }

    private String name(long id) {
        String[] name = {""};
        runQuery("MATCH (n:Node) WHERE id(n) = " + id + " RETURN n.name as name", row -> {
            name[0] = row.getString("name");
        });
        return name[0];
    }

    @Test
    void testMBC() {
        MaxDepthBetweennessCentrality algo = new MaxDepthBetweennessCentrality(graph, 3);
        algo.compute();
        algo.resultStream()
            .forEach(r -> System.out.println(name(r.nodeId) + " -> " + r.centrality));
    }
}
