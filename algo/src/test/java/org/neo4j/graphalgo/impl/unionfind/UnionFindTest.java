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
package org.neo4j.graphalgo.impl.unionfind;

import com.carrotsearch.hppc.BitSet;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraph;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.loader.HugeGraphFactory;
import org.neo4j.graphalgo.core.huge.loader.HugeNullWeightMap;
import org.neo4j.graphalgo.core.neo4jview.GraphViewFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.dss.DisjointSetStruct;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class UnionFindTest {

    private static GraphDatabaseAPI DB;
    private static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("TYPE");
    private static final int SETS_COUNT = 16;
    private static final int SET_SIZE = 10;

    static Stream<Arguments> parameters() {
        return Stream.of(
                arguments("Heavy", HeavyGraphFactory.class, UnionFindType.PARALLEL),
                arguments("Heavy", HeavyGraphFactory.class, UnionFindType.FJ_MERGE),
                arguments("Heavy", HeavyGraphFactory.class, UnionFindType.FORK_JOIN),
                arguments("Huge", HugeGraphFactory.class, UnionFindType.PARALLEL),
                arguments("Huge", HugeGraphFactory.class, UnionFindType.FJ_MERGE),
                arguments("Huge", HugeGraphFactory.class, UnionFindType.FORK_JOIN),
                arguments("Kernel", GraphViewFactory.class, UnionFindType.PARALLEL),
                arguments("Kernel", GraphViewFactory.class, UnionFindType.FJ_MERGE),
                arguments("Kernel", GraphViewFactory.class, UnionFindType.FORK_JOIN)
        );
    }

    @BeforeAll
    static void setupGraph() {
        DB = TestDatabaseCreator.createTestDatabase();
        int[] setSizes = new int[SETS_COUNT];
        Arrays.fill(setSizes, SET_SIZE);
        createTestGraph(setSizes);
    }

    private static void createTestGraph(int... setSizes) {
        try (Transaction tx = DB.beginTx()) {
            for (int setSize : setSizes) {
                createLine(DB, setSize);
            }
            tx.success();
        }
    }

    private static void createLine(GraphDatabaseService db, int setSize) {
        Node temp = db.createNode();
        for (int i = 1; i < setSize; i++) {
            Node t = db.createNode();
            temp.createRelationshipTo(t, RELATIONSHIP_TYPE);
            temp = t;
        }
    }

    @Test
    public void memRecParallel() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();

        assertEquals(
                MemoryRange.of(120),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(160),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions0, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(120),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions0, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(160),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions0, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(120),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions0, 64).memoryUsage());
        assertEquals(
                MemoryRange.of(160),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions0, 64).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(
                MemoryRange.of(920),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(1760),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions100, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(920),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions100, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(1760),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions100, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(920),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions100, 64).memoryUsage());
        assertEquals(
                MemoryRange.of(1760),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions100, 64).memoryUsage());

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(
                MemoryRange.of(800_122_070_448L),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions100B, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(1_600_244_140_816L),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions100B, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(800_122_070_448L),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions100B, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(1_600_244_140_816L),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions100B, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(800_122_070_448L),
                UnionFindType.PARALLEL.memoryEstimation().estimate(dimensions100B, 64).memoryUsage());
        assertEquals(
                MemoryRange.of(1_600_244_140_816L),
                UnionFindType.PARALLEL.memoryEstimation(true).estimate(dimensions100B, 64).memoryUsage());
    }

    @Test
    public void memRecForkJoin() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();

        assertEquals(
                MemoryRange.of(168),
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(616),
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions0, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(4200),
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions0, 64).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(
                MemoryRange.of(968),
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(7016),
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions100, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(55400),
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions100, 64).memoryUsage());

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(
                MemoryRange.of(800_122_070_496L).min,
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions100B, 1).memoryUsage().min);
        assertEquals(
                MemoryRange.of(6_400_976_563_240L).min,
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions100B, 8).memoryUsage().min);
        assertEquals(
                MemoryRange.of(51_207_812_505_192L).min,
                UnionFindType.FORK_JOIN.memoryEstimation().estimate(dimensions100B, 64).memoryUsage().min);
    }

    @Test
    public void memRecFJMerge() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();

        assertEquals(
                MemoryRange.of(176),
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(624),
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions0, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(4208),
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions0, 64).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(
                MemoryRange.of(976),
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
        assertEquals(
                MemoryRange.of(7024),
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions100, 8).memoryUsage());
        assertEquals(
                MemoryRange.of(55408).min,
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions100, 64).memoryUsage().min);

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(
                MemoryRange.of(800_122_070_504L).min,
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions100B, 1).memoryUsage().min);
        assertEquals(
                MemoryRange.of(6_400_976_563_248L).min,
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions100B, 8).memoryUsage().min);
        assertEquals(
                MemoryRange.of(51_207_812_505_200L).min,
                UnionFindType.FJ_MERGE.memoryEstimation().estimate(dimensions100B, 64).memoryUsage().min);
    }

    @ParameterizedTest(name = "{0} -- {2}")
    @MethodSource("parameters")
    void test(String graphName, Class<? extends GraphFactory> graphImpl, UnionFindType uf) {
        Graph graph = new GraphLoader(DB)
                .withExecutorService(Pools.DEFAULT)
                .withAnyLabel()
                .withRelationshipType(RELATIONSHIP_TYPE)
                .load(graphImpl);

        UnionFind.Config config = new UnionFind.Config(null, Double.NaN);

        DisjointSetStruct result = run(uf, config, graph);

        Assert.assertEquals(SETS_COUNT, getSetCount(result));
        long[] setRegions = new long[SETS_COUNT];
        Arrays.fill(setRegions, -1);

        graph.forEachNode((nodeId) -> {
            long expectedSetRegion = nodeId / SET_SIZE;
            final long setId = result.setIdOf(nodeId);
            int setRegion = (int) (setId / SET_SIZE);
            assertEquals(
                    "Node " + nodeId + " in unexpected set: " + setId,
                    expectedSetRegion,
                    setRegion);

            long regionSetId = setRegions[setRegion];
            if (regionSetId == -1) {
                setRegions[setRegion] = setId;
            } else {
                assertEquals(
                        "Inconsistent set for node " + nodeId + ", is " + setId + " but should be " + regionSetId,
                        regionSetId,
                        setId);
            }
            return true;
        });
    }

    private DisjointSetStruct run(final UnionFindType uf, UnionFind.Config config, Graph graph) {
        return UnionFindHelper.run(
                uf,
                graph,
                Pools.DEFAULT,
                SET_SIZE / Pools.DEFAULT_CONCURRENCY,
                Pools.DEFAULT_CONCURRENCY,
                config,
                AllocationTracker.EMPTY);
    }

    /**
     * Compute number of sets present.
     */
    static long getSetCount(DisjointSetStruct struct) {
        long capacity = struct.size();
        BitSet sets = new BitSet(capacity);
        for (long i = 0L; i < capacity; i++) {
            long setId = struct.setIdOf(i);
            sets.set(setId);
        }
        return sets.cardinality();
    }
}
