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
package org.neo4j.graphalgo.impl.betweenness;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.neo4j.graphalgo.HeavyHugeTester;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

/**
 * (B)-->(C)-->(D)
 *  ↑     |     |
 *  |     |     |
 *  |     ↓     ↓
 * (A)-->(F)<--(E)
 *
 */
public class BetweennessCentralityTest3 extends HeavyHugeTester {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private static GraphDatabaseAPI db;
    private static Graph graph;

    interface TestConsumer {
        void accept(String name, double centrality);
    }

    @Mock
    private TestConsumer testConsumer;

    @BeforeClass
    public static void setupGraph() {
        final String cypher =
                "CREATE (a:Node {name:'a'})\n" +
                        "CREATE (b:Node {name:'b'})\n" +
                        "CREATE (c:Node {name:'c'})\n" +
                        "CREATE (d:Node {name:'d'})\n" +
                        "CREATE (e:Node {name:'e'})\n" +
                        "CREATE (f:Node {name:'f'})\n" +
                        "CREATE" +
                        " (a)-[:TYPE]->(b),\n" +
                        " (a)-[:TYPE]->(f),\n" +
                        " (b)-[:TYPE]->(c),\n" +
                        " (c)-[:TYPE]->(d),\n" +
                        " (c)-[:TYPE]->(f),\n" +
                        " (e)-[:TYPE]->(f),\n" +
                        " (d)-[:TYPE]->(e)";

        db = TestDatabaseCreator.createTestDatabase();

        try (Transaction tx = db.beginTx()) {
            db.execute(cypher);
            tx.success();
        }
    }

    @AfterClass
    public static void tearDown() {
        if (db != null) db.shutdown();
        graph = null;
    }

    public BetweennessCentralityTest3(final Class<? extends GraphFactory> graphImpl, String name) {
        super(graphImpl);
        graph = new GraphLoader(db)
                .withAnyRelationshipType()
                .withAnyLabel()
                .withoutNodeProperties()
                .load(graphImpl);
    }

    private String name(long id) {
        String[] name = {""};
        db.execute("MATCH (n:Node) WHERE id(n) = " + id + " RETURN n.name as name")
                .accept(row -> {
                    name[0] = row.getString("name");
                    return false;
                });
        return name[0];
    }

    @Test
    public void testBetweennessCentralityOutgoing() {
        Map<String, Double> actual = new BetweennessCentrality(graph)
                .withDirection(Direction.OUTGOING)
                .compute()
                .resultStream()
                .collect(Collectors.toMap(r -> name(r.nodeId), r -> r.centrality));

        Map<String, Double> expected = new HashMap<>();
        expected.put("a", 0.0);
        expected.put("b", 3.0);
        expected.put("c", 5.0);
        expected.put("d", 3.0);
        expected.put("e", 1.0);
        expected.put("f", 0.0);

        assertThat(actual.keySet(), equalTo(expected.keySet()));
        expected.forEach((key, value) -> assertThat(actual.get(key), closeTo(value, 1e-14)));
    }

    @Test
    public void testBetweennessCentralityIncoming() {
        Map<String, Double> actual = new BetweennessCentrality(graph)
                .withDirection(Direction.INCOMING)
                .compute()
                .resultStream()
                .collect(Collectors.toMap(r -> name(r.nodeId), r -> r.centrality));

        Map<String, Double> expected = new HashMap<>();
        expected.put("a", 0.0);
        expected.put("b", 3.0);
        expected.put("c", 5.0);
        expected.put("d", 3.0);
        expected.put("e", 1.0);
        expected.put("f", 0.0);

        assertThat(actual.keySet(), equalTo(expected.keySet()));
        expected.forEach((key, value) -> assertThat(actual.get(key), closeTo(value, 1e-14)));
    }

    @Test
    public void testBetweennessCentralityBoth() {
        Map<String, Double> actual = new BetweennessCentrality(graph)
                .withDirection(Direction.BOTH)
                .compute()
                .resultStream()
                .collect(Collectors.toMap(r -> name(r.nodeId), r -> r.centrality));

        Map<String, Double> expected = new HashMap<>();
        expected.put("a", 5.0/6.0);
        expected.put("b", 5.0/6.0);
        expected.put("c", 10.0/3.0);
        expected.put("d", 5.0/6.0);
        expected.put("e", 5.0/6.0);
        expected.put("f", 10.0/3.0);

        assertThat(actual.keySet(), equalTo(expected.keySet()));
        expected.forEach((key, value) -> assertThat(actual.get(key), closeTo(value, 1e-14)));
    }

}
