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
package org.neo4j.graphalgo.impl.pagerank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestSupport;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.CypherGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.impl.results.CentralityResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.QueryRunner.runInTransaction;

final class PersonalizedPageRankTest extends AlgoTestBase {

    static PageRank.Config DEFAULT_CONFIG = new PageRank.Config(40, 0.85, PageRank.DEFAULT_TOLERANCE);

    private static final String DB_CYPHER =
            "CREATE" +
            "  (iphone:Product {name: 'iPhone5'})" +
            ", (kindle:Product {name: 'Kindle Fire'})" +
            ", (fitbit:Product {name: 'Fitbit Flex Wireless'})" +
            ", (potter:Product {name: 'Harry Potter'})" +
            ", (hobbit:Product {name: 'Hobbit'})" +

            ", (todd:Person {name: 'Todd'})" +
            ", (mary:Person {name: 'Mary'})" +
            ", (jill:Person {name: 'Jill'})" +
            ", (john:Person {name: 'John'})" +

            ",  (john)-[:PURCHASED]->(iphone)" +
            ",  (john)-[:PURCHASED]->(kindle)" +
            ",  (mary)-[:PURCHASED]->(iphone)" +
            ",  (mary)-[:PURCHASED]->(kindle)" +
            ",  (mary)-[:PURCHASED]->(fitbit)" +
            ",  (jill)-[:PURCHASED]->(iphone)" +
            ",  (jill)-[:PURCHASED]->(kindle)" +
            ",  (jill)-[:PURCHASED]->(fitbit)" +
            ",  (todd)-[:PURCHASED]->(fitbit)" +
            ",  (todd)-[:PURCHASED]->(potter)" +
            ",  (todd)-[:PURCHASED]->(hobbit)";

    @BeforeEach
    void setupGraphDb() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(DB_CYPHER);
    }

    @AfterEach
    void shutdownGraphDb() {
        if (db != null) db.shutdown();
    }

    @TestSupport.AllGraphTypesTest
    void test(Class<? extends GraphFactory> graphFactory) {
        Label personLabel = Label.label("Person");
        Label productLabel = Label.label("Product");
        final Map<Long, Double> expected = new HashMap<>();

        runInTransaction(db, () -> {
            expected.put(db.findNode(personLabel, "name", "John").getId(), 0.24851499999999993);
            expected.put(db.findNode(personLabel, "name", "Jill").getId(), 0.12135449999999998);
            expected.put(db.findNode(personLabel, "name", "Mary").getId(), 0.12135449999999998);
            expected.put(db.findNode(personLabel, "name", "Todd").getId(), 0.043511499999999995);

            expected.put(db.findNode(productLabel, "name", "Kindle Fire").getId(), 0.17415649999999996);
            expected.put(db.findNode(productLabel, "name", "iPhone5").getId(), 0.17415649999999996);
            expected.put(db.findNode(productLabel, "name", "Fitbit Flex Wireless").getId(), 0.08085200000000001);
            expected.put(db.findNode(productLabel, "name", "Harry Potter").getId(), 0.01224);
            expected.put(db.findNode(productLabel, "name", "Hobbit").getId(), 0.01224);
        });

        final Graph graph;
        if (graphFactory.isAssignableFrom(CypherGraphFactory.class)) {
            graph = runInTransaction(db, () -> new GraphLoader(db)
                .withLabel("MATCH (n) RETURN id(n) as id")
                .withRelationshipType("MATCH (n)-[:PURCHASED]-(m) RETURN id(n) as source,id(m) as target")
                .load(graphFactory)
            );
        } else {
            graph = new GraphLoader(db)
                    .withDirection(Direction.BOTH)
                    .withRelationshipType("PURCHASED")
                    .undirected()
                    .load(graphFactory);
        }

        LongStream sourceNodeIds = runInTransaction(db, () -> {
            Node node = db.findNode(personLabel, "name", "John");
            return LongStream.of(node.getId());
        });

        final CentralityResult rankResult = PageRankAlgorithmType.NON_WEIGHTED
                .create(graph, Pools.DEFAULT, 2, 1, DEFAULT_CONFIG, sourceNodeIds)
                .compute()
                .result();

        IntStream.range(0, expected.size()).forEach(i -> {
            final long nodeId = graph.toOriginalNodeId(i);
            assertEquals(
                    expected.get(nodeId),
                    rankResult.score(i),
                    1e-2,
                    "Node#" + nodeId
            );
        });
    }
}
