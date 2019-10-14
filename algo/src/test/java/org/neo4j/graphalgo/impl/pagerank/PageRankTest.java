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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestSupport.AllGraphTypesTest;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.CypherGraphFactory;
import org.neo4j.graphalgo.core.utils.BitUtil;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.impl.results.CentralityResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PageRankTest {

    static PageRank.Config DEFAULT_CONFIG = new PageRank.Config(40, 0.85, PageRank.DEFAULT_TOLERANCE);

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

    private static GraphDatabaseAPI DB;

    @BeforeAll
    static void setupGraphDb() {
        DB = TestDatabaseCreator.createTestDatabase();
        DB.execute(DB_CYPHER);
    }

    @AfterAll
    static void shutdownGraphDb() {
        if (DB != null) DB.shutdown();
    }

    @AllGraphTypesTest
    void testOnOutgoingRelationships(Class<? extends GraphFactory> graphImpl) {
        final Label label = Label.label("Label1");
        final Map<Long, Double> expected = new HashMap<>();

        try (Transaction tx = DB.beginTx()) {
            expected.put(DB.findNode(label, "name", "a").getId(), 0.243007);
            expected.put(DB.findNode(label, "name", "b").getId(), 1.9183995);
            expected.put(DB.findNode(label, "name", "c").getId(), 1.7806315);
            expected.put(DB.findNode(label, "name", "d").getId(), 0.21885);
            expected.put(DB.findNode(label, "name", "e").getId(), 0.243007);
            expected.put(DB.findNode(label, "name", "f").getId(), 0.21885);
            expected.put(DB.findNode(label, "name", "g").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "h").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "i").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "j").getId(), 0.15);
        }

        final Graph graph;
        if (graphImpl.isAssignableFrom(CypherGraphFactory.class)) {
            try (Transaction tx = DB.beginTx()) {
                graph = new GraphLoader(DB)
                        .withLabel("MATCH (n:Label1) RETURN id(n) as id")
                        .withRelationshipType(
                                "MATCH (n:Label1)-[:TYPE1]->(m:Label1) RETURN id(n) as source,id(m) as target")
                        .load(graphImpl);
            }
        } else {
            graph = new GraphLoader(DB)
                    .withLabel(label)
                    .withRelationshipType("TYPE1")
                    .withDirection(Direction.OUTGOING)
                    .load(graphImpl);
        }

        final CentralityResult rankResult = PageRankAlgorithmType.NON_WEIGHTED
                .create(graph, DEFAULT_CONFIG, LongStream.empty())
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

    @AllGraphTypesTest
    void testOnIncomingRelationships(Class<? extends GraphFactory> graphImpl) {
        final Label label = Label.label("Label1");
        final Map<Long, Double> expected = new HashMap<>();

        try (Transaction tx = DB.beginTx()) {
            expected.put(DB.findNode(label, "name", "a").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "b").getId(), 0.3386727);
            expected.put(DB.findNode(label, "name", "c").getId(), 0.2219679);
            expected.put(DB.findNode(label, "name", "d").getId(), 0.3494679);
            expected.put(DB.findNode(label, "name", "e").getId(), 2.5463981);
            expected.put(DB.findNode(label, "name", "f").getId(), 2.3858317);
            expected.put(DB.findNode(label, "name", "g").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "h").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "i").getId(), 0.15);
            expected.put(DB.findNode(label, "name", "j").getId(), 0.15);
            tx.close();
        }

        final Graph graph;
        final CentralityResult rankResult;
        if (graphImpl.isAssignableFrom(CypherGraphFactory.class)) {
            try (Transaction tx = DB.beginTx()) {
                graph = new GraphLoader(DB)
                        .withLabel("MATCH (n:Label1) RETURN id(n) as id")
                        .withRelationshipType(
                                "MATCH (n:Label1)<-[:TYPE1]-(m:Label1) RETURN id(n) AS source,id(m) AS target")
                        .load(graphImpl);
            }
            rankResult = PageRankAlgorithmType.NON_WEIGHTED
                    .create(graph, DEFAULT_CONFIG, LongStream.empty())
                    .compute()
                    .result();
        } else {
            graph = new GraphLoader(DB)
                    .withLabel(label)
                    .withRelationshipType("TYPE1")
                    .withDirection(Direction.INCOMING)
                    .load(graphImpl);

            rankResult = PageRankAlgorithmType.NON_WEIGHTED
                    .create(graph, DEFAULT_CONFIG, LongStream.empty())
                    .compute()
                    .result();
        }

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

    @AllGraphTypesTest
    void correctPartitionBoundariesForAllNodes(Class<? extends GraphFactory> graphImpl) {
        final Label label = Label.label("Label1");
        final Graph graph;
        if (graphImpl.isAssignableFrom(CypherGraphFactory.class)) {
            try (Transaction tx = DB.beginTx()) {
                graph = new GraphLoader(DB)
                        .withLabel("MATCH (n:Label1) RETURN id(n) as id")
                        .withRelationshipType(
                                "MATCH (n:Label1)-[:TYPE1]->(m:Label1) RETURN id(n) as source,id(m) as target")
                        .load(graphImpl);
            }
        } else {
            graph = new GraphLoader(DB)
                    .withLabel(label)
                    .withRelationshipType("TYPE1")
                    .withDirection(Direction.OUTGOING)
                    .load(graphImpl);
        }

        // explicitly list all source nodes to prevent the 'we got everything' optimization
        PageRank algorithm = PageRankAlgorithmType.NON_WEIGHTED
                .create(
                        graph,
                        null,
                        1,
                        1,
                        DEFAULT_CONFIG,
                        LongStream.range(0L, graph.nodeCount()),
                        AllocationTracker.EMPTY)
                .compute();
        // should not throw
    }

    @Test
    void shouldComputeMemoryEstimation1Thread() {
        long nodeCount = 100_000L;
        int concurrency = 1;
        assertMemoryEstimation(nodeCount, concurrency);
    }

    @Test
    void shouldComputeMemoryEstimation4Threads() {
        long nodeCount = 100_000L;
        int concurrency = 4;
        assertMemoryEstimation(nodeCount, concurrency);
    }

    @Test
    void shouldComputeMemoryEstimation42Threads() {
        long nodeCount = 100_000L;
        int concurrency = 42;
        assertMemoryEstimation(nodeCount, concurrency);
    }

    private void assertMemoryEstimation(final long nodeCount, final int concurrency) {
        GraphDimensions dimensions = new GraphDimensions.Builder().setNodeCount(nodeCount).build();

        final PageRankFactory pageRank = new PageRankFactory(DEFAULT_CONFIG);

        long partitionSize = BitUtil.ceilDiv(nodeCount, concurrency);
        final MemoryRange actual = pageRank.memoryEstimation().estimate(dimensions, concurrency).memoryUsage();
        final MemoryRange expected = MemoryRange.of(
                104L /* PageRank.class */ +
                32L /* ComputeSteps.class */ +
                BitUtil.align(16 + concurrency * 4, 8) /* scores[] wrapper */ +
                BitUtil.align(16 + concurrency * 8, 8) /* starts[] */ +
                BitUtil.align(16 + concurrency * 8, 8) /* lengths[] */ +
                BitUtil.align(16 + concurrency * 4, 8) /* list of computeSteps */ +
                        /* ComputeStep */
                concurrency * (
                        120L /* NonWeightedComputeStep.class */ +
                        BitUtil.align(16 + concurrency * 4, 8) /* nextScores[] wrapper */ +
                        concurrency * BitUtil.align(16 + partitionSize * 4, 8) /* inner nextScores[][] */ +
                        BitUtil.align(16 + partitionSize * 8, 8) /* pageRank[] */ +
                        BitUtil.align(16 + partitionSize * 8, 8) /* deltas[] */
                )
        );
        assertEquals(expected, actual);
    }
}
