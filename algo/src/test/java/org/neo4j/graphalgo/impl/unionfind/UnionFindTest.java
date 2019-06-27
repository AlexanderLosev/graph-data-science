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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.loader.HugeGraphFactory;
import org.neo4j.graphalgo.core.neo4jview.GraphViewFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.PagedDisjointSetStruct;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mknblch
 */
@RunWith(Parameterized.class)
public class UnionFindTest {

    private static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("TYPE");

    private static final int setsCount = 16;
    private static final int setSize = 10;

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "Heavy"},
                new Object[]{HugeGraphFactory.class, "Huge"},
                new Object[]{GraphViewFactory.class, "Kernel"}
        );
    }

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @BeforeClass
    public static void setupGraph() {
        try (ProgressTimer timer = ProgressTimer.start(l -> System.out.println(
                "creating test graph took " + l + " ms"))) {
            int[] setSizes = new int[setsCount];
            Arrays.fill(setSizes, setSize);
            createTestGraph(setSizes);
        }
    }

    private Graph graph;

    public UnionFindTest(
            Class<? extends GraphFactory> graphImpl,
            String name) {
        graph = new GraphLoader(DB)
                .withExecutorService(Pools.DEFAULT)
                .withAnyLabel()
                .withRelationshipType(RELATIONSHIP_TYPE)
                .load(graphImpl);
    }

    private static void createTestGraph(int... setSizes) {
        DB.executeAndCommit(db -> {
            for (int setSize : setSizes) {
                createLine(db, setSize);
            }
        });
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
    public void testSeq() {
        test(UnionFindAlgo.SEQ);
    }

    @Test
    public void testQueue() {
        test(UnionFindAlgo.QUEUE);
    }

    @Test
    public void testForkJoin() {
        test(UnionFindAlgo.FORK_JOIN);
    }

    @Test
    public void testFJMerge() {
        test(UnionFindAlgo.FJ_MERGE);
    }

    @Test
    public void memRecSeq() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();
        assertEquals(MemoryRange.of(160), algo(UnionFindAlgo.SEQ).memoryEstimation().apply(dimensions0, 1).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(MemoryRange.of(1760), algo(UnionFindAlgo.SEQ).memoryEstimation().apply(dimensions100, 1).memoryUsage());

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(MemoryRange.of(1600244140816L), algo(UnionFindAlgo.SEQ).memoryEstimation().apply(dimensions100B, 1).memoryUsage());
    }

    @Test
    public void memRecQueue() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();

        assertEquals(MemoryRange.of(224), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions0, 1).memoryUsage());
        assertEquals(MemoryRange.of(1008), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions0, 8).memoryUsage());
        assertEquals(MemoryRange.of(7280), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions0, 64).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(MemoryRange.of(1824), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions100, 1).memoryUsage());
        assertEquals(MemoryRange.of(13808), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions100, 8).memoryUsage());
        assertEquals(MemoryRange.of(109680), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions100, 64).memoryUsage());

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(MemoryRange.of(1600244140880L), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions100B, 1).memoryUsage());
        assertEquals(MemoryRange.of(12801953126256L), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions100B, 8).memoryUsage());
        assertEquals(MemoryRange.of(102415625009264L), algo(UnionFindAlgo.QUEUE).memoryEstimation().apply(dimensions100B, 64).memoryUsage());
    }

    @Test
    public void memRecForkJoin() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();

        assertEquals(MemoryRange.of(224), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions0, 1).memoryUsage());
        assertEquals(MemoryRange.of(1008), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions0, 8).memoryUsage());
        assertEquals(MemoryRange.of(7280), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions0, 64).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(MemoryRange.of(1824), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions100, 1).memoryUsage());
        assertEquals(MemoryRange.of(13808), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions100, 8).memoryUsage());
        assertEquals(MemoryRange.of(109680), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions100, 64).memoryUsage());

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(MemoryRange.of(1600244140880L), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions100B, 1).memoryUsage());
        assertEquals(MemoryRange.of(12801953126256L), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions100B, 8).memoryUsage());
        assertEquals(MemoryRange.of(102415625009264L), algo(UnionFindAlgo.FORK_JOIN).memoryEstimation().apply(dimensions100B, 64).memoryUsage());
    }

    @Test
    public void memRecFJMerge() {
        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();

        assertEquals(MemoryRange.of(232), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions0, 1).memoryUsage());
        assertEquals(MemoryRange.of(1016), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions0, 8).memoryUsage());
        assertEquals(MemoryRange.of(7288), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions0, 64).memoryUsage());

        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
        assertEquals(MemoryRange.of(1832), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions100, 1).memoryUsage());
        assertEquals(MemoryRange.of(13816), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions100, 8).memoryUsage());
        assertEquals(MemoryRange.of(109688), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions100, 64).memoryUsage());

        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
        assertEquals(MemoryRange.of(1600244140888L), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions100B, 1).memoryUsage());
        assertEquals(MemoryRange.of(12801953126264L), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions100B, 8).memoryUsage());
        assertEquals(MemoryRange.of(102415625009272L), algo(UnionFindAlgo.FJ_MERGE).memoryEstimation().apply(dimensions100B, 64).memoryUsage());
    }

    private void test(UnionFindAlgo uf) {
        PagedDisjointSetStruct result = run(uf);

        Assert.assertEquals(setsCount, result.getSetCount());
        long[] setRegions = new long[setsCount];
        Arrays.fill(setRegions, -1);

        graph.forEachNode((nodeId) -> {
            long expectedSetRegion = nodeId / setSize;
            final long setId = result.find(nodeId);
            int setRegion = (int) (setId / setSize);
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

    private GraphUnionFindAlgo<?> algo(final UnionFindAlgo uf) {
        return uf.algo(
                Optional.of(graph),
                Pools.DEFAULT,
                AllocationTracker.EMPTY,
                setSize / Pools.DEFAULT_CONCURRENCY,
                Pools.DEFAULT_CONCURRENCY,
                Double.NaN
        );
    }

    private PagedDisjointSetStruct run(final UnionFindAlgo uf) {
        return uf.run(
                graph,
                Pools.DEFAULT,
                AllocationTracker.EMPTY,
                setSize / Pools.DEFAULT_CONCURRENCY,
                Pools.DEFAULT_CONCURRENCY,
                Double.NaN
        );
    }
}
