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
package org.neo4j.graphalgo.pagerank;

import org.junit.jupiter.api.BeforeEach;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.CypherLoaderBuilder;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestSupport.AllGraphTypesTest;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphStoreFactory;
import org.neo4j.graphalgo.core.loading.CypherFactory;
import org.neo4j.graphalgo.core.utils.BatchingProgressLogger;
import org.neo4j.graphalgo.result.CentralityResult;
import org.neo4j.graphdb.Label;
import org.neo4j.logging.NullLog;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.compat.GraphDatabaseApiProxy.applyInTransaction;
import static org.neo4j.graphalgo.compat.GraphDatabaseApiProxy.runInTransaction;

final class ArticleRankTest extends AlgoTestBase {

    private static final PageRankBaseConfig DEFAULT_CONFIG = ImmutablePageRankStreamConfig
        .builder()
        .maxIterations(40)
        .build();

    private static final String DB_CYPHER =
            "CREATE" +
            "  (_:Label0 {name: '_'})" +
            ", (a:Label1 {name: 'a'})" +
            ", (b:Label1 {name: 'b'})" +
            ", (c:Label1 {name: 'c'})" +
            ", (d:Label1 {name: 'd'})" +
            ", (e:Label1 {name: 'e'})" +
            ", (f:Label1 {name: 'f'})" +
            ", (g:Label1 {name: 'g'})" +
            ", (h:Label1 {name: 'h'})" +
            ", (i:Label1 {name: 'i'})" +
            ", (j:Label1 {name: 'j'})" +
            ", (k:Label2 {name: 'k'})" +
            ", (l:Label2 {name: 'l'})" +
            ", (m:Label2 {name: 'm'})" +
            ", (n:Label2 {name: 'n'})" +
            ", (o:Label2 {name: 'o'})" +
            ", (p:Label2 {name: 'p'})" +
            ", (q:Label2 {name: 'q'})" +
            ", (r:Label2 {name: 'r'})" +
            ", (s:Label2 {name: 's'})" +
            ", (t:Label2 {name: 't'})" +

            ", (b)-[:TYPE1]->(c)" +
            ", (c)-[:TYPE1]->(b)" +

            ", (d)-[:TYPE1]->(a)" +
            ", (d)-[:TYPE1]->(b)" +

            ", (e)-[:TYPE1]->(b)" +
            ", (e)-[:TYPE1]->(d)" +
            ", (e)-[:TYPE1]->(f)" +

            ", (f)-[:TYPE1]->(b)" +
            ", (f)-[:TYPE1]->(e)" +

            ", (g)-[:TYPE2]->(b)" +
            ", (g)-[:TYPE2]->(e)" +
            ", (h)-[:TYPE2]->(b)" +
            ", (h)-[:TYPE2]->(e)" +
            ", (i)-[:TYPE2]->(b)" +
            ", (i)-[:TYPE2]->(e)" +
            ", (j)-[:TYPE2]->(e)" +
            ", (k)-[:TYPE2]->(e)";

    @BeforeEach
    void setupGraphDb() {
        runQuery(DB_CYPHER);
    }

    @AllGraphTypesTest
    void test(Class<? extends GraphStoreFactory> factoryType) {
        final Label label = Label.label("Label1");
        final Map<Long, Double> expected = new HashMap<>();

        runInTransaction(db, tx -> {
            expected.put(tx.findNode(label, "name", "a").getId(), 0.2071625);
            expected.put(tx.findNode(label, "name", "b").getId(), 0.4706795);
            expected.put(tx.findNode(label, "name", "c").getId(), 0.3605195);
            expected.put(tx.findNode(label, "name", "d").getId(), 0.195118);
            expected.put(tx.findNode(label, "name", "e").getId(), 0.2071625);
            expected.put(tx.findNode(label, "name", "f").getId(), 0.195118);
            expected.put(tx.findNode(label, "name", "g").getId(), 0.15);
            expected.put(tx.findNode(label, "name", "h").getId(), 0.15);
            expected.put(tx.findNode(label, "name", "i").getId(), 0.15);
            expected.put(tx.findNode(label, "name", "j").getId(), 0.15);
        });

        final Graph graph;
        if (factoryType.isAssignableFrom(CypherFactory.class)) {
            graph = applyInTransaction(db, tx -> new CypherLoaderBuilder()
                .api(db)
                .nodeQuery("MATCH (n:Label1) RETURN id(n) as id")
                .relationshipQuery("MATCH (n:Label1)-[:TYPE1]->(m:Label1) RETURN id(n) as source,id(m) as target")
                .build()
                .graph(factoryType));
        } else {
            graph = new StoreLoaderBuilder()
                .api(db)
                .addNodeLabel(label.name())
                .addRelationshipType("TYPE1")
                .build()
                .graph(factoryType);
        }

        final CentralityResult rankResult = LabsPageRankAlgorithmType.ARTICLE_RANK
            .create(
                graph,
                DEFAULT_CONFIG,
                LongStream.empty(),
                new BatchingProgressLogger(NullLog.getInstance(), 0, "PageRank")
            ).compute()
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
