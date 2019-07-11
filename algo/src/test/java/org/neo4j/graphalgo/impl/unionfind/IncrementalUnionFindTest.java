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

import com.carrotsearch.hppc.IntLongHashMap;
import com.carrotsearch.hppc.IntLongMap;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.loader.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.DisjointSetStruct;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * @author mknblch
 */
@RunWith(Parameterized.class)
public class IncrementalUnionFindTest {

    private static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("TYPE");

    private static String COMMUNITY_PROPERTY = "community";

    private static final int SETS_COUNT = 16;
    private static final int SET_SIZE = 10;

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "Heavy"},
                new Object[]{HugeGraphFactory.class, "Huge"}
                // TODO GraphView graph does not support node properties
                // new Object[]{GraphViewFactory.class, "Kernel"}
        );
    }

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @BeforeClass
    public static void setupGraph() {
        try (ProgressTimer timer = ProgressTimer.start(l -> System.out.println(
                "creating test graph took " + l + " ms"))) {
            int[] setSizes = new int[SETS_COUNT];
            Arrays.fill(setSizes, SET_SIZE);
            createTestGraph(setSizes);
        }
    }

    private final Graph graph;
    private final UnionFindAlgorithm.Config config;

    public IncrementalUnionFindTest(Class<? extends GraphFactory> graphImpl, String name) {
        graph = new GraphLoader(DB)
                .withExecutorService(Pools.DEFAULT)
                .withAnyLabel()
                .withOptionalNodeProperties(
                        PropertyMapping.of(COMMUNITY_PROPERTY, COMMUNITY_PROPERTY, -1L)
                )
                .withRelationshipType(RELATIONSHIP_TYPE)
                .load(graphImpl);

        config = new UnionFindAlgorithm.Config(
                graph.nodeProperties(COMMUNITY_PROPERTY),
                Double.NaN
        );
    }

    private static void createTestGraph(int... setSizes) {
        DB.executeAndCommit(db -> {
            for (int i = 0; i < setSizes.length; i = i + 2) {
                long sourceId = createLine(db, setSizes[i]);
                long targetId = createLine(db, setSizes[i + 1]);

                createConnection(db, sourceId, targetId);
            }
        });
    }

    private static Long createLine(GraphDatabaseService db, int setSize) {
        Node temp = db.createNode();
        for (int i = 1; i < setSize; i++) {
            Node t = db.createNode();

            int communityId = (int) t.getId() / setSize * 2 + 1;
            temp.setProperty(COMMUNITY_PROPERTY, communityId);

            temp.createRelationshipTo(t, RELATIONSHIP_TYPE);
            temp = t;
        }

        int communityId = (int) temp.getId() / setSize * 2 + 1;
        temp.setProperty(COMMUNITY_PROPERTY, communityId);
        return temp.getId();
    }

    private static void createConnection(GraphDatabaseService db, long sourceId, long targetId) {
        final Node source = db.getNodeById(sourceId);
        final Node target = db.getNodeById(targetId);

        source.createRelationshipTo(target, RELATIONSHIP_TYPE);
    }

    @Test
    public void testSeq() {
        test(UnionFindAlgorithmType.SEQ, config);
    }

    @Test
    public void testQueue() {
        test(UnionFindAlgorithmType.QUEUE, config);
    }

    @Test
    public void testForkJoin() {
        test(UnionFindAlgorithmType.FORK_JOIN, config);
    }

    @Test
    public void testFJMerge() {
        test(UnionFindAlgorithmType.FJ_MERGE, config);
    }

//    @Test
//    public void memRecSeq() {
//        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();
//        assertEquals(
//                MemoryRange.of(160),
//                UnionFindAlgorithmType.SEQ.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
//
//        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
//        assertEquals(
//                MemoryRange.of(1760),
//                UnionFindAlgorithmType.SEQ.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
//
//        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
//        assertEquals(
//                MemoryRange.of(1600244140816L),
//                UnionFindAlgorithmType.SEQ.memoryEstimation().estimate(dimensions100B, 1).memoryUsage());
//    }
//
//    @Test
//    public void memRecQueue() {
//        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();
//
//        assertEquals(
//                MemoryRange.of(224),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(1008),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions0, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(7280),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions0, 64).memoryUsage());
//
//        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
//        assertEquals(
//                MemoryRange.of(1824),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(13808),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions100, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(109680),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions100, 64).memoryUsage());
//
//        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
//        assertEquals(
//                MemoryRange.of(1600244140880L),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions100B, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(12801953126256L),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions100B, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(102415625009264L),
//                UnionFindAlgorithmType.QUEUE.memoryEstimation().estimate(dimensions100B, 64).memoryUsage());
//    }
//
//    @Test
//    public void memRecForkJoin() {
//        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();
//
//        assertEquals(
//                MemoryRange.of(224),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(1008),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions0, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(7280),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions0, 64).memoryUsage());
//
//        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
//        assertEquals(
//                MemoryRange.of(1824),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(13808),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions100, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(109680),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions100, 64).memoryUsage());
//
//        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
//        assertEquals(
//                MemoryRange.of(1600244140880L),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions100B, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(12801953126256L),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions100B, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(102415625009264L),
//                UnionFindAlgorithmType.FORK_JOIN.memoryEstimation().estimate(dimensions100B, 64).memoryUsage());
//    }
//
//    @Test
//    public void memRecFJMerge() {
//        GraphDimensions dimensions0 = new GraphDimensions.Builder().setNodeCount(0).build();
//
//        assertEquals(
//                MemoryRange.of(232),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions0, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(1016),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions0, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(7288),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions0, 64).memoryUsage());
//
//        GraphDimensions dimensions100 = new GraphDimensions.Builder().setNodeCount(100).build();
//        assertEquals(
//                MemoryRange.of(1832),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions100, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(13816),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions100, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(109688),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions100, 64).memoryUsage());
//
//        GraphDimensions dimensions100B = new GraphDimensions.Builder().setNodeCount(100_000_000_000L).build();
//        assertEquals(
//                MemoryRange.of(1600244140888L),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions100B, 1).memoryUsage());
//        assertEquals(
//                MemoryRange.of(12801953126264L),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions100B, 8).memoryUsage());
//        assertEquals(
//                MemoryRange.of(102415625009272L),
//                UnionFindAlgorithmType.FJ_MERGE.memoryEstimation().estimate(dimensions100B, 64).memoryUsage());
//    }


    private void test(
            UnionFindAlgorithmType uf,
            UnionFindAlgorithm.Config config) {
        DisjointSetStruct result = run(uf, config);

        Assert.assertEquals(SETS_COUNT / 2, result.getSetCount());
        IntLongMap setRegions = new IntLongHashMap();

        graph.forEachNode((nodeId) -> {
            int expectedSetRegion = ((int) nodeId / (2 * SET_SIZE) * 2) * 2 + 1;
            final long setId = result.setIdOf(nodeId);
            int setRegion = (int) setId;
            assertEquals(
                    "Node " + nodeId + " in unexpected set: " + setId,
                    expectedSetRegion,
                    setRegion);

            long regionSetId = setRegions.getOrDefault(setRegion, -1);
            if (regionSetId == -1) {
                setRegions.put(setRegion, setId);
            } else {
                assertEquals(
                        "Inconsistent set for node " + nodeId + ", is " + setId + " but should be " + regionSetId,
                        regionSetId,
                        setId);
            }
            return true;
        });
    }

    private DisjointSetStruct run(final UnionFindAlgorithmType uf, UnionFindAlgorithm.Config config) {
        return UnionFindHelper.run(
                uf,
                graph,
                Pools.DEFAULT,
                SET_SIZE / Pools.DEFAULT_CONCURRENCY,
                Pools.DEFAULT_CONCURRENCY,
                config,
                AllocationTracker.EMPTY);
    }
}
