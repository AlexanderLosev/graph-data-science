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

package org.neo4j.graphalgo.impl.jaccard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestLog;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.mem.MemoryTree;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.NullLog;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.neo4j.graphalgo.TestGraph.Builder.fromGdl;
import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;
import static org.neo4j.graphdb.Direction.BOTH;
import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;

final class NeighborhoodSimilarityTest {

    // TODO: maybe create random graph similar to JaccardProcTest#buildRandomDB
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
        ", (c)-[:LIKES]->(i3)";

    private static final Collection<SimilarityResult> EXPECTED_OUTGOING = new HashSet<>();
    private static final Collection<SimilarityResult> EXPECTED_INCOMING = new HashSet<>();

    private static final Collection<SimilarityResult> EXPECTED_OUTGOING_TOP_1 = new HashSet<>();
    private static final Collection<SimilarityResult> EXPECTED_INCOMING_TOP_1 = new HashSet<>();

    private static final Collection<SimilarityResult> EXPECTED_OUTGOING_TOPK_1 = new HashSet<>();
    private static final Collection<SimilarityResult> EXPECTED_INCOMING_TOPK_1 = new HashSet<>();

    private static final Collection<SimilarityResult> EXPECTED_OUTGOING_SIMILARITY_CUTOFF = new HashSet<>();
    private static final Collection<SimilarityResult> EXPECTED_INCOMING_SIMILARITY_CUTOFF = new HashSet<>();

    private static final Collection<SimilarityResult> EXPECTED_OUTGOING_DEGREE_CUTOFF = new HashSet<>();
    private static final Collection<SimilarityResult> EXPECTED_INCOMING_DEGREE_CUTOFF = new HashSet<>();

    private static final int COMPARED_ITEMS = 3;
    private static final int COMPARED_PERSONS = 3;

    static {
        EXPECTED_OUTGOING.add(new SimilarityResult(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING.add(new SimilarityResult(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING.add(new SimilarityResult(1, 2, 0.0));

        EXPECTED_OUTGOING_TOP_1.add(new SimilarityResult(0, 1, 2 / 3.0));

        EXPECTED_OUTGOING_TOPK_1.add(new SimilarityResult(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_TOPK_1.add(new SimilarityResult(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING_TOPK_1.add(new SimilarityResult(2, 0, 1 / 3.0));

        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(new SimilarityResult(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_SIMILARITY_CUTOFF.add(new SimilarityResult(0, 2, 1 / 3.0));

        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(new SimilarityResult(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(new SimilarityResult(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING_DEGREE_CUTOFF.add(new SimilarityResult(1, 2, 0.0));

        EXPECTED_INCOMING.add(new SimilarityResult(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING.add(new SimilarityResult(4, 6, 1 / 3.0));
        EXPECTED_INCOMING.add(new SimilarityResult(5, 6, 1 / 3.0));

        EXPECTED_INCOMING_TOP_1.add(new SimilarityResult(4, 5, 3.0 / 3.0));

        EXPECTED_INCOMING_TOPK_1.add(new SimilarityResult(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING_TOPK_1.add(new SimilarityResult(5, 4, 3.0 / 3.0));
        EXPECTED_INCOMING_TOPK_1.add(new SimilarityResult(6, 4, 1 / 3.0));

        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(new SimilarityResult(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(new SimilarityResult(4, 6, 1 / 3.0));
        EXPECTED_INCOMING_SIMILARITY_CUTOFF.add(new SimilarityResult(5, 6, 1 / 3.0));

        EXPECTED_INCOMING_DEGREE_CUTOFF.add(new SimilarityResult(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(new SimilarityResult(4, 6, 1 / 3.0));
        EXPECTED_INCOMING_DEGREE_CUTOFF.add(new SimilarityResult(5, 6, 1 / 3.0));
    }

    private GraphDatabaseAPI db;

    @BeforeEach
    void setup() {
        db = TestDatabaseCreator.createTestDatabase();
        db.execute(DB_CYPHER);
    }

    @AfterEach
    void teardown() {
        db.shutdown();
    }

    @ParameterizedTest
    @CsvSource({
        "OUTGOING, OUTGOING",
        "BOTH, OUTGOING",
        "INCOMING, INCOMING",
        "BOTH, INCOMING"
    })
    void shouldComputeForSupportedDirections(Direction loadDirection, Direction algoDirection) {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(loadDirection)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            NeighborhoodSimilarity.Config.DEFAULT,
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance()
        );

        Set<SimilarityResult> result = neighborhoodSimilarity.computeToStream(algoDirection).collect(Collectors.toSet());
        neighborhoodSimilarity.release();

        assertEquals(algoDirection == INCOMING ? EXPECTED_INCOMING : EXPECTED_OUTGOING, result);
    }

    @ParameterizedTest
    @CsvSource({
        "OUTGOING, OUTGOING",
        "BOTH, OUTGOING",
        "INCOMING, INCOMING",
        "BOTH, INCOMING"
    })
    void shouldComputeTopForSupportedDirections(Direction loadDirection, Direction algoDirection) {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(loadDirection)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            new NeighborhoodSimilarity.Config(0.0, 0, 1, 0, Pools.DEFAULT_CONCURRENCY, ParallelUtil.DEFAULT_BATCH_SIZE),
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance());

        Set<SimilarityResult> result = neighborhoodSimilarity.computeToStream(algoDirection).collect(Collectors.toSet());
        neighborhoodSimilarity.release();

        assertEquals(algoDirection == INCOMING ? EXPECTED_INCOMING_TOP_1 : EXPECTED_OUTGOING_TOP_1, result);
    }

    @ParameterizedTest
    @CsvSource({
        "OUTGOING, OUTGOING",
        "BOTH, OUTGOING",
        "INCOMING, INCOMING",
        "BOTH, INCOMING"
    })
    void shouldComputeTopKForSupportedDirections(Direction loadDirection, Direction algoDirection) {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(loadDirection)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            new NeighborhoodSimilarity.Config(0.0, 0, 0, 1, Pools.DEFAULT_CONCURRENCY, ParallelUtil.DEFAULT_BATCH_SIZE),
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance());

        Set<SimilarityResult> result = neighborhoodSimilarity.computeToStream(algoDirection).collect(Collectors.toSet());
        neighborhoodSimilarity.release();

        assertEquals(algoDirection == INCOMING ? EXPECTED_INCOMING_TOPK_1 : EXPECTED_OUTGOING_TOPK_1, result);
    }

    @ParameterizedTest
    @CsvSource({
        "OUTGOING, OUTGOING",
        "BOTH, OUTGOING",
        "INCOMING, INCOMING",
        "BOTH, INCOMING"
    })
    void shouldComputeWithSimilarityCutoffForSupportedDirections(Direction loadDirection, Direction algoDirection) {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(loadDirection)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            new NeighborhoodSimilarity.Config(0.1, 0, 0, 0, Pools.DEFAULT_CONCURRENCY, ParallelUtil.DEFAULT_BATCH_SIZE),
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance());

        Set<SimilarityResult> result = neighborhoodSimilarity.computeToStream(algoDirection).collect(Collectors.toSet());
        neighborhoodSimilarity.release();

        assertEquals(algoDirection == INCOMING ? EXPECTED_INCOMING_SIMILARITY_CUTOFF : EXPECTED_OUTGOING_SIMILARITY_CUTOFF, result);
    }

    @ParameterizedTest
    @CsvSource({
        "OUTGOING, OUTGOING",
        "BOTH, OUTGOING",
        "INCOMING, INCOMING",
        "BOTH, INCOMING"
    })
    void shouldComputeWithDegreeCutoffForSupportedDirections(Direction loadDirection, Direction algoDirection) {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(loadDirection)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            new NeighborhoodSimilarity.Config(0.0, 1, 0, 0, Pools.DEFAULT_CONCURRENCY, ParallelUtil.DEFAULT_BATCH_SIZE),
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance());

        Set<SimilarityResult> result = neighborhoodSimilarity.computeToStream(algoDirection).collect(Collectors.toSet());
        neighborhoodSimilarity.release();

        assertEquals(algoDirection == INCOMING ? EXPECTED_INCOMING_DEGREE_CUTOFF : EXPECTED_OUTGOING_DEGREE_CUTOFF, result);
    }

    @Test
    void shouldComputeForUndirectedGraphs() {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .undirected()
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            NeighborhoodSimilarity.Config.DEFAULT,
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance());
        Set<SimilarityResult> result = neighborhoodSimilarity.computeToStream(OUTGOING).collect(Collectors.toSet());
        neighborhoodSimilarity.release();
        assertNotEquals(Collections.emptySet(), result);
    }

    @ParameterizedTest
    @CsvSource({
        "OUTGOING, OUTGOING",
        "BOTH, OUTGOING",
        "INCOMING, INCOMING",
        "BOTH, INCOMING"
    })
    void shouldComputeSimilarityGraphInAllSupportedDirections(Direction loadDirection, Direction algoDirection) {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(loadDirection)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            NeighborhoodSimilarity.Config.DEFAULT,
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            NullLog.getInstance());

        SimilarityGraphResult similarityGraphResult = neighborhoodSimilarity.computeToGraph(algoDirection);
        assertEquals(algoDirection == INCOMING ? COMPARED_ITEMS : COMPARED_PERSONS, similarityGraphResult.comparedNodes());
        Graph resultGraph = similarityGraphResult.similarityGraph();
        assertGraphEquals(
            algoDirection == INCOMING
                ? fromGdl("(a), (b), (c), (d), (e), (f)-[{property: 1.000000D}]->(g), (f)-[{property: 0.333333D}]->(h), (g)-[{property: 0.333333D}]->(h)")
                : fromGdl("(a)-[{property: 0.666667D}]->(b), (a)-[{property: 0.333333D}]->(c), (b)-[{property: 0.00000D}]->(c), (d), (e), (f), (g), (h)"),
            resultGraph
        );
        neighborhoodSimilarity.release();
        resultGraph.release();
    }

    @Test
    void shouldThrowForDirectionBoth() {
        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .undirected()
            .load(HugeGraphFactory.class);

        IllegalArgumentException ex = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new NeighborhoodSimilarity(
                graph,
                NeighborhoodSimilarity.Config.DEFAULT,
                Pools.DEFAULT,
                AllocationTracker.EMPTY,
                NullLog.getInstance()).computeToStream(BOTH)
        );
        assertThat(ex.getMessage(), containsString("Direction BOTH is not supported"));
    }

    @ParameterizedTest(name = "topk = {0}")
    @ValueSource(ints = {0, 100})
    void shouldLogProgress(int topk) {
        TestLog log = new TestLog();

        Graph graph = new GraphLoader(db)
            .withAnyLabel()
            .withAnyRelationshipType()
            .withDirection(OUTGOING)
            .load(HugeGraphFactory.class);

        NeighborhoodSimilarity neighborhoodSimilarity = new NeighborhoodSimilarity(
            graph,
            new NeighborhoodSimilarity.Config(0.0, 0, 100, topk, Pools.DEFAULT_CONCURRENCY, ParallelUtil.DEFAULT_BATCH_SIZE),
            Pools.DEFAULT,
            AllocationTracker.EMPTY,
            log
        ).withProgressLogger(log);

        neighborhoodSimilarity.computeToGraph(OUTGOING);

        assertFalse(log.getLogMessages().isEmpty());
        assertThat(log.getLogMessages().get(0), containsString(NeighborhoodSimilarity.class.getSimpleName()));
    }

    @Test
    void shouldComputeMemrec() {
        GraphDimensions dimensions = new GraphDimensions.Builder()
            .setNodeCount(1_000_000)
            .setMaxRelCount(5_000_000)
            .build();

        NeighborhoodSimilarity.Config config = new NeighborhoodSimilarity.Config(
            0.0,
            0,
            0,
            100,
            Pools.DEFAULT_CONCURRENCY,
            ParallelUtil.DEFAULT_BATCH_SIZE
        );

        NeighborhoodSimilarityFactory factory = new NeighborhoodSimilarityFactory(
            config,
            true
        );

        MemoryTree actual = factory.memoryEstimation().estimate(dimensions, 1);

        long graphRangeMin = 113516712L;
        long graphRangeMax = 212614704L;
        MemoryRange graphRange = MemoryRange.of(graphRangeMin, graphRangeMax);

        long topKMapRangeMin = 1104000016L;
        long topKMapRangeMax = 5072000016L;
        MemoryRange topKRange = MemoryRange.of(topKMapRangeMin, topKMapRangeMax);

        long vectorsRangeMin = 56000016L;
        long vectorsRangeMax = 56000016L;
        MemoryRange vectorsRange = MemoryRange.of(vectorsRangeMin, vectorsRangeMax);

        long nodeFilterRangeMin = 125016L;
        long nodeFilterRangeMax = 125016L;
        MemoryRange nodeFilterRange = MemoryRange.of(nodeFilterRangeMin, nodeFilterRangeMax);

        long thisInstance = 64;

        MemoryTree expected = MemoryEstimations.builder()
            .fixed("", graphRange)
            .fixed("", topKRange)
            .fixed("", vectorsRange)
            .fixed("", nodeFilterRange)
            .fixed("", thisInstance)
            .build().estimate(dimensions, 1);

        assertEquals(expected.memoryUsage(), actual.memoryUsage());
    }

    @Test
    void shouldComputeMemrecWithTop() {
        GraphDimensions dimensions = new GraphDimensions.Builder()
            .setNodeCount(1_000_000)
            .setMaxRelCount(5_000_000)
            .build();

        NeighborhoodSimilarity.Config config = new NeighborhoodSimilarity.Config(
            0.0,
            0,
            100,
            100,
            Pools.DEFAULT_CONCURRENCY,
            ParallelUtil.DEFAULT_BATCH_SIZE
        );

        NeighborhoodSimilarityFactory factory = new NeighborhoodSimilarityFactory(
            config,
            true
        );

        MemoryTree actual = factory.memoryEstimation().estimate(dimensions, 1);

        long graphRangeMin = 8651112L;
        long graphRangeMax = 8651112L;
        MemoryRange graphRange = MemoryRange.of(graphRangeMin, graphRangeMax);

        long topKMapRangeMin = 1104000016L;
        long topKMapRangeMax = 5072000016L;
        MemoryRange topKRange = MemoryRange.of(topKMapRangeMin, topKMapRangeMax);

        long vectorsRangeMin = 56000016L;
        long vectorsRangeMax = 56000016L;
        MemoryRange vectorsRange = MemoryRange.of(vectorsRangeMin, vectorsRangeMax);

        long nodeFilterRangeMin = 125016L;
        long nodeFilterRangeMax = 125016L;
        MemoryRange nodeFilterRange = MemoryRange.of(nodeFilterRangeMin, nodeFilterRangeMax);

        long thisInstance = 64;

        MemoryTree expected = MemoryEstimations.builder()
            .fixed("", graphRange)
            .fixed("", topKRange)
            .fixed("", vectorsRange)
            .fixed("", nodeFilterRange)
            .fixed("", thisInstance)
            .build().estimate(dimensions, 1);

        assertEquals(expected.memoryUsage(), actual.memoryUsage());
    }
}

