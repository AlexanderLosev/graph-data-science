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
package org.neo4j.graphalgo.nodesim;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestLog;
import org.neo4j.graphalgo.TestProgressLogger;
import org.neo4j.graphalgo.TestSupport;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.beta.generator.RandomGraphGenerator;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.ImmutableGraphDimensions;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.loading.NativeFactory;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.mem.MemoryTree;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.graphalgo.Orientation.NATURAL;
import static org.neo4j.graphalgo.Orientation.REVERSE;
import static org.neo4j.graphalgo.Orientation.UNDIRECTED;
import static org.neo4j.graphalgo.TestGraph.Builder.fromGdl;
import static org.neo4j.graphalgo.TestLog.INFO;
import static org.neo4j.graphalgo.TestSupport.assertAlgorithmTermination;
import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;
import static org.neo4j.graphalgo.TestSupport.crossArguments;
import static org.neo4j.graphalgo.TestSupport.toArguments;
import static org.neo4j.graphalgo.nodesim.NodeSimilarityBaseConfig.TOP_K_DEFAULT;

final class NodeSimilarityTest extends AlgoTestBase {

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Person {name: 'Alice'})" +
        ", (b:Person {name: 'Bob'})" +
        ", (c:Person {name: 'Charlie'})" +
        ", (d:Person {name: 'Dave'})" +
        ", (i1:Item {name: 'p1'})" +
        ", (i2:Item {name: 'p2'})" +
        ", (i3:Item {name: 'p3'})" +
        ", (i4:Item {name: 'p4'})" +
        ", (a)-[:LIKES]->(i1)" +
        ", (a)-[:LIKES]->(i2)" +
        ", (a)-[:LIKES]->(i3)" +
        ", (b)-[:LIKES]->(i1)" +
        ", (b)-[:LIKES]->(i2)" +
        ", (c)-[:LIKES]->(i3)" +
        ", (d)-[:LIKES]->(i1)" +
        ", (d)-[:LIKES]->(i2)" +
        ", (d)-[:LIKES]->(i3)";

    private static final Collection<String> EXPECTED_OUTGOING = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_TOP_N_1 = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_TOP_N_1 = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_TOP_K_1 = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_TOP_K_1 = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_SIMILARITY_CUTOFF = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_SIMILARITY_CUTOFF = new HashSet<>();

    private static final Collection<String> EXPECTED_OUTGOING_DEGREE_CUTOFF = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING_DEGREE_CUTOFF = new HashSet<>();

    private static final int COMPARED_ITEMS = 3;
    private static final int COMPARED_PERSONS = 4;

    private static ImmutableNodeSimilarityWriteConfig.Builder configBuilder() {
        return ImmutableNodeSimilarityWriteConfig
            .builder()
            .writeProperty("writeProperty")
            .writeRelationshipType("writeRelationshipType")
            .similarityCutoff(0.0);
    }

    static {
        EXPECTED_OUTGOING.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING.add(resultString(1, 2, 0.0));
        EXPECTED_OUTGOING.add(resultString(1, 3, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(2, 3, 1 / 3.0));
        // Add results in reverse direction because topK
        EXPECTED_OUTGOING.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING.add(resultString(3, 0, 1.0));
        EXPECTED_OUTGOING.add(resultString(2, 1, 0.0));
        EXPECTED_OUTGOING.add(resultString(3, 1, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(3, 2, 1 / 3.0));

        EXPECTED_OUTGOING_TOP_N_1.add(resultString(0, 3, 1.0));

        EXPECTED_OUTGOING_TOP_K_1.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING_TOP_K_1.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_TOP_K_1.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING_TOP_K_1.add(resultString(3, 0, 1.0));

        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(1, 3, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(2, 3, 1 / 3.0));
        // Add results in reverse direction because topK
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(3, 0, 1.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(3, 1, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(resultString(3, 2, 1 / 3.0));

        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(0, 3, 1.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(1, 3, 2 / 3.0));
        // Add results in reverse direction because topK
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(3, 0, 1.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(resultString(3, 1, 2 / 3.0));

        EXPECTED_INCOMING.add(resultString(4, 5, 1.0));
        EXPECTED_INCOMING.add(resultString(4, 6, 1 / 2.0));
        EXPECTED_INCOMING.add(resultString(5, 6, 1 / 2.0));
        // Add results in reverse direction because topK
        EXPECTED_INCOMING.add(resultString(5, 4, 1.0));
        EXPECTED_INCOMING.add(resultString(6, 4, 1 / 2.0));
        EXPECTED_INCOMING.add(resultString(6, 5, 1 / 2.0));

        EXPECTED_INCOMING_TOP_N_1.add(resultString(4, 5, 3.0 / 3.0));

        EXPECTED_INCOMING_TOP_K_1.add(resultString(4, 5, 1.0));
        EXPECTED_INCOMING_TOP_K_1.add(resultString(5, 4, 1.0));
        EXPECTED_INCOMING_TOP_K_1.add(resultString(6, 4, 1 / 2.0));

        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(4, 5, 1.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(4, 6, 1 / 2.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(5, 6, 1 / 2.0));
        // Add results in reverse direction because topK
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(5, 4, 1.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(6, 4, 1 / 2.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(resultString(6, 5, 1 / 2.0));

        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(4, 6, 1 / 2.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(5, 6, 1 / 2.0));
        // Add results in reverse direction because topK
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(5, 4, 3.0 / 3.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(6, 4, 1 / 2.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(resultString(6, 5, 1 / 2.0));
    }

    private static String resultString(long node1, long node2, double similarity) {
        return String.format("%d,%d %f%n", node1, node2, similarity);
    }

    private static String resultString(SimilarityResult result) {
        return resultString(result.node1, result.node2, result.similarity);
    }

    private static Stream<Integer> concurrencies() {
        return Stream.of(1, 4);
    }

    static Stream<Arguments> supportedLoadAndComputeDirections() {
        Stream<Arguments> directions = Stream.of(
            arguments(NATURAL),
            arguments(REVERSE)
        );
        return crossArguments(() -> directions, toArguments(NodeSimilarityTest::concurrencies));
    }

    static Stream<Arguments> topKAndConcurrencies() {
        Stream<Integer> topKStream = Stream.of(TOP_K_DEFAULT, 100);
        return TestSupport.crossArguments(
            toArguments(() -> topKStream),
            toArguments(NodeSimilarityTest::concurrencies)
        );
    }

    private final Log log = new TestLog();

    @BeforeEach
    void setup() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(DB_CYPHER);
    }

    @AfterEach
    void teardown() {
        db.shutdown();
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING : EXPECTED_OUTGOING, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeTopNForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).topN(1).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING_TOP_N_1 : EXPECTED_OUTGOING_TOP_N_1, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeNegativeTopNForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).bottomN(1).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Graph similarityGraph = nodeSimilarity.computeToGraph().similarityGraph();

        assertGraphEquals(
            orientation == REVERSE
                ? fromGdl(
                "(i1)-[{w: 0.50000D}]->(i3), (i2), (i4), (a), (b), (c), (d)")
                : fromGdl(
                    "(a), (b)-[{w: 0.00000D}]->(c), (d), (i1), (i2), (i3), (i4)")
            , similarityGraph
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeTopKForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().topK(1).concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING_TOP_K_1 : EXPECTED_OUTGOING_TOP_K_1, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeNegativeTopKForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder()
                .concurrency(concurrency)
                .topK(10)
                .bottomK(1)
                .build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Graph similarityGraph = nodeSimilarity.computeToGraph().similarityGraph();

        assertGraphEquals(
            orientation == REVERSE
                ? fromGdl(
                "(i1)-[{w: 0.50000D}]->(i3), (i2)-[{w: 0.50000D}]->(i3), (i3)-[{w: 0.500000D}]->(i1), (d), (e), (f), (g), (h)")
                : fromGdl(
                    "(a)-[{w: 0.333333D}]->(c), (b)-[{w: 0.00000D}]->(c), (c)-[{w: 0.000000D}]->(b), (d)-[{w: 0.333333D}]->(c), (e), (f), (g), (h)")
            , similarityGraph
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeWithSimilarityCutoffForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).similarityCutoff(0.1).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(
            orientation == REVERSE ? EXPECTED_INCOMING_SIMILARITY_CUTOFF : EXPECTED_OUTGOING_SIMILARITY_CUTOFF,
            result
        );
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeWithDegreeCutoffForSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().degreeCutoff(2).concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(
            orientation == REVERSE ? EXPECTED_INCOMING_DEGREE_CUTOFF : EXPECTED_OUTGOING_DEGREE_CUTOFF,
            result
        );
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @MethodSource("concurrencies")
    void shouldComputeForUndirectedGraphs(int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(UNDIRECTED)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );
        Set<SimilarityResult> result = nodeSimilarity.computeToStream().collect(Collectors.toSet());
        nodeSimilarity.release();
        assertNotEquals(Collections.emptySet(), result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeSimilarityGraphInAllSupportedDirections(Orientation orientation, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        SimilarityGraphResult similarityGraphResult = nodeSimilarity.computeToGraph();
        assertEquals(
            orientation == REVERSE ? COMPARED_ITEMS : COMPARED_PERSONS,
            similarityGraphResult.comparedNodes()
        );
        Graph resultGraph = similarityGraphResult.similarityGraph();
        assertGraphEquals(
            orientation == REVERSE
                ? fromGdl(
                "(a), (b), (c), (d), (e)" +
                ", (f)-[{property: 1.000000D}]->(g)" +
                ", (f)-[{property: 0.500000D}]->(h)" +
                ", (g)-[{property: 0.500000D}]->(h)" +
                // Add results in reverse direction because topK
                ", (g)-[{property: 1.000000D}]->(f)" +
                ", (h)-[{property: 0.500000D}]->(f)" +
                ", (h)-[{property: 0.500000D}]->(g)"
            )
                : fromGdl("  (a)-[{property: 0.666667D}]->(b)" +
                          ", (a)-[{property: 0.333333D}]->(c)" +
                          ", (a)-[{property: 1.000000D}]->(d)" +
                          ", (b)-[{property: 0.000000D}]->(c)" +
                          ", (b)-[{property: 0.666667D}]->(d)" +
                          ", (c)-[{property: 0.333333D}]->(d)" +
                          // Add results in reverse direction because topK
                          "  (b)-[{property: 0.666667D}]->(a)" +
                          ", (c)-[{property: 0.333333D}]->(a)" +
                          ", (d)-[{property: 1.000000D}]->(a)" +
                          ", (c)-[{property: 0.000000D}]->(b)" +
                          ", (d)-[{property: 0.666667D}]->(b)" +
                          ", (d)-[{property: 0.333333D}]->(c)" +
                          ", (e), (f), (g), (h)"),
            resultGraph
        );
        nodeSimilarity.release();
        resultGraph.release();
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldComputeToGraphWithUnusedNodesInInputGraph(Orientation orientation, int concurrency) {
        GraphDatabaseAPI localDb = TestDatabaseCreator.createTestDatabase();
        runQuery(localDb, "UNWIND range(0, 1024) AS unused CREATE (:Unused)");
        runQuery(localDb, DB_CYPHER);

        Graph graph =  new StoreLoaderBuilder()
            .api(localDb)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder()
                .concurrency(concurrency)
                .topK(100)
                .topN(1)
                .build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        SimilarityGraphResult similarityGraphResult = nodeSimilarity.computeToGraph();
        assertEquals(
            orientation == REVERSE ? COMPARED_ITEMS : COMPARED_PERSONS,
            similarityGraphResult.comparedNodes()
        );

        Graph resultGraph = similarityGraphResult.similarityGraph();
        String expected = orientation == REVERSE ? resultString(1029, 1030, 1.00000) : resultString(
            1025,
            1028,
            1.00000
        );

        resultGraph.forEachNode(n -> {
            resultGraph.forEachRelationship(n, -1.0, (s, t, w) -> {
                assertEquals(expected, resultString(s, t, w));
                return true;
            });
            return true;
        });

        nodeSimilarity.release();
        resultGraph.release();
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldIgnoreLoops(Orientation orientation, int concurrency) {
        // Add loops
        runQuery("" +
                 " MATCH (alice {name: 'Alice'})" +
                 " MATCH (thing {name: 'p1'})" +
                 " CREATE (alice)-[:LIKES]->(alice), (thing)-[:LIKES]->(thing)"
        );

        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).topN(1).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING_TOP_N_1 : EXPECTED_OUTGOING_TOP_N_1, result);
    }

    @ParameterizedTest(name = "orientation: {0}, concurrency: {1}")
    @MethodSource("supportedLoadAndComputeDirections")
    void shouldIgnoreParallelEdges(Orientation orientation, int concurrency) {
        // Add parallel edges
        runQuery("" +
                 " MATCH (person {name: 'Alice'})" +
                 " MATCH (thing {name: 'p1'})" +
                 " CREATE (person)-[:LIKES]->(thing)"
        );
        runQuery("" +
                 " MATCH (person {name: 'Dave'})" +
                 " MATCH (thing {name: 'p3'})" +
                 " CREATE (person)-[:LIKES]->(thing)" +
                 " CREATE (person)-[:LIKES]->(thing)" +
                 " CREATE (person)-[:LIKES]->(thing)"
        );

        Graph graph = new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(orientation)
            .globalAggregation(Aggregation.NONE)
            .build()
            .graph(NativeFactory.class);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        Set<String> result = nodeSimilarity
            .computeToStream()
            .map(NodeSimilarityTest::resultString)
            .collect(Collectors.toSet());
        nodeSimilarity.release();

        assertEquals(orientation == REVERSE ? EXPECTED_INCOMING : EXPECTED_OUTGOING, result);
    }

    @Disabled("Unsure how to proceed with direction BOTH")
    @ParameterizedTest(name = "concurrency = {0}")
    @MethodSource("concurrencies")
    void shouldThrowForDirectionBoth(int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(UNDIRECTED)
            .build()
            .graph(NativeFactory.class);

        IllegalArgumentException ex = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new NodeSimilarity(
                graph,
                configBuilder().concurrency(concurrency).build(),
                Pools.DEFAULT,
                progressLogger,
                AllocationTracker.EMPTY
            ).computeToStream()
        );
        assertThat(ex.getMessage(), containsString("Direction BOTH is not supported"));
    }

    @Timeout(value = 10)
    @Test
    void shouldTerminate() {
        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            RandomGraphGenerator.generate(10, 2),
            configBuilder().concurrency(1).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        assertAlgorithmTermination(
            db,
            nodeSimilarity,
            nhs -> nodeSimilarity.computeToStream(),
            100
        );
    }

    @ParameterizedTest(name = "topK = {0}")
    @ValueSource(ints = {TOP_K_DEFAULT, 100})
    void shouldComputeMemrec(int topK) {
        GraphDimensions dimensions = ImmutableGraphDimensions.builder()
            .nodeCount(1_000_000)
            .maxRelCount(5_000_000)
            .build();

        NodeSimilarityWriteConfig config = ImmutableNodeSimilarityWriteConfig
            .builder()
            .similarityCutoff(0.0)
            .topK(topK)
            .writeProperty("writeProperty")
            .writeRelationshipType("writeRelationshipType")
            .build();

        MemoryTree actual = new NodeSimilarityFactory<>().memoryEstimation(config).estimate(dimensions, 1);

        long thisInstance = 56;

        long nodeFilterRangeMin = 125_016L;
        long nodeFilterRangeMax = 125_016L;
        MemoryRange nodeFilterRange = MemoryRange.of(nodeFilterRangeMin, nodeFilterRangeMax);

        long vectorsRangeMin = 56_000_016L;
        long vectorsRangeMax = 56_000_016L;
        MemoryRange vectorsRange = MemoryRange.of(vectorsRangeMin, vectorsRangeMax);

        MemoryEstimations.Builder builder = MemoryEstimations.builder()
            .fixed("this.instance", thisInstance)
            .fixed("node filter", nodeFilterRange)
            .fixed("vectors", vectorsRange);

        long topKMapRangeMin;
        long topKMapRangeMax;
        if (topK == TOP_K_DEFAULT) {
            topKMapRangeMin = 248_000_024L;
            topKMapRangeMax = 248_000_024L;
        } else {
            topKMapRangeMin = 1_688_000_024L;
            topKMapRangeMax = 1_688_000_024L;
        }
        builder.fixed("topK map", MemoryRange.of(topKMapRangeMin, topKMapRangeMax));

        MemoryTree expected = builder.build().estimate(dimensions, 1);

        assertEquals(expected.memoryUsage(), actual.memoryUsage());
    }

    @ParameterizedTest(name = "topK = {0}")
    @ValueSource(ints = {TOP_K_DEFAULT, 100})
    void shouldComputeMemrecWithTop(int topK) {
        GraphDimensions dimensions = ImmutableGraphDimensions.builder()
            .nodeCount(1_000_000)
            .maxRelCount(5_000_000)
            .build();

        NodeSimilarityWriteConfig config = ImmutableNodeSimilarityWriteConfig
            .builder()
            .similarityCutoff(0.0)
            .topN(100)
            .topK(topK)
            .writeProperty("writeProperty")
            .writeRelationshipType("writeRelationshipType")
            .build();

        MemoryTree actual = new NodeSimilarityFactory<>().memoryEstimation(config).estimate(dimensions, 1);

        long thisInstance = 56;

        long nodeFilterRangeMin = 125_016L;
        long nodeFilterRangeMax = 125_016L;
        MemoryRange nodeFilterRange = MemoryRange.of(nodeFilterRangeMin, nodeFilterRangeMax);

        long vectorsRangeMin = 56_000_016L;
        long vectorsRangeMax = 56_000_016L;
        MemoryRange vectorsRange = MemoryRange.of(vectorsRangeMin, vectorsRangeMax);

        long topNListMin = 2_504L;
        long topNListMax = 2_504L;
        MemoryRange topNListRange = MemoryRange.of(topNListMin, topNListMax);

        MemoryEstimations.Builder builder = MemoryEstimations.builder()
            .fixed("this.instance", thisInstance)
            .fixed("node filter", nodeFilterRange)
            .fixed("vectors", vectorsRange)
            .fixed("topNList", topNListRange);

        long topKMapRangeMin;
        long topKMapRangeMax;
        if (topK == TOP_K_DEFAULT) {
            topKMapRangeMin = 248_000_024L;
            topKMapRangeMax = 248_000_024L;
        } else {
            topKMapRangeMin = 1_688_000_024L;
            topKMapRangeMax = 1_688_000_024L;
        }
        builder.fixed("topK map", MemoryRange.of(topKMapRangeMin, topKMapRangeMax));

        MemoryTree expected = builder.build().estimate(dimensions, 1);

        assertEquals(expected.memoryUsage(), actual.memoryUsage());
    }

    @ParameterizedTest(name = "topK = {0}, concurrency = {1}")
    @MethodSource("topKAndConcurrencies")
    void shouldLogMessages(int topK, int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .build()
            .graph(NativeFactory.class);

        TestProgressLogger progressLogger = new TestProgressLogger(graph.relationshipCount(), "NodeSimilarity", concurrency);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().topN(100).topK(topK).concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        nodeSimilarity.computeToGraph();

        assertTrue(progressLogger.hasMessages(INFO));

        assertTrue(progressLogger.containsMessage(TestLog.INFO, "Start :: NodeSimilarity#prepare"));
        assertTrue(progressLogger.containsMessage(TestLog.INFO, "Finish :: NodeSimilarity#prepare"));
        assertTrue(progressLogger.containsMessage(TestLog.INFO, "NodeSimilarity#computeToStream"));

        if (concurrency > 1) {
            assertTrue(progressLogger.containsMessage(TestLog.INFO, "Start :: NodeSimilarity#computeTopKMapParallel"));
            assertTrue(progressLogger.containsMessage(TestLog.INFO, "Finish :: NodeSimilarity#computeTopKMapParallel"));
        } else {
            assertTrue(progressLogger.containsMessage(TestLog.INFO, "Start :: NodeSimilarity#computeTopKMap"));
            assertTrue(progressLogger.containsMessage(TestLog.INFO, "Finish :: NodeSimilarity#computeTopKMap"));
        }

        assertTrue(progressLogger.containsMessage(TestLog.INFO, "Start :: NodeSimilarity#computeTopN(TopKMap)"));
        assertTrue(progressLogger.containsMessage(TestLog.INFO, "Finish :: NodeSimilarity#computeTopN(TopKMap)"));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1,2})
    void shouldLogProgress(int concurrency) {
        Graph graph =  new StoreLoaderBuilder()
            .api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .globalOrientation(NATURAL)
            .build()
            .graph(NativeFactory.class);

        TestProgressLogger progressLogger = new TestProgressLogger(graph.relationshipCount(), "NodeSimilarity", concurrency);

        NodeSimilarity nodeSimilarity = new NodeSimilarity(
            graph,
            configBuilder().degreeCutoff(0).concurrency(concurrency).build(),
            Pools.DEFAULT,
            progressLogger,
            AllocationTracker.EMPTY
        );

        long comparisons = nodeSimilarity.computeToStream().count();

        List<AtomicLong> progresses = progressLogger.getProgresses();

        // Should log progress for prepare and actual comparisons
        assertEquals(2, progresses.size());

        assertEquals(graph.relationshipCount(), progresses.get(0).get());
        assertEquals(concurrency == 1 ? comparisons / 2 : comparisons, progresses.get(1).get());
    }
}

