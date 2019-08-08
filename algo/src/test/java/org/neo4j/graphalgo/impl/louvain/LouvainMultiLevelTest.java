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
package org.neo4j.graphalgo.impl.louvain;

import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.rules.ErrorCollector;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestProgressLogger;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.loader.HugeGraphFactory;
import org.neo4j.graphalgo.core.neo4jview.GraphViewFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * (a)-(b)--(g)-(h)
 * \  /     \ /
 * (c)     (i)           (ABC)-(GHI)
 * \      /         =>    \   /
 * (d)-(e)                (DEF)
 * \  /
 * (f)
 *
 * @author mknblch
 */
public class LouvainMultiLevelTest {

    private static final String COMPLEX_CYPHER = "CREATE " +
                    "  (a:Node {name: 'a'})" +
                    ", (b:Node {name: 'b'})" +
                    ", (c:Node {name: 'c'})" +
                    ", (d:Node {name: 'd'})" +
                    ", (e:Node {name: 'e'})" +
                    ", (f:Node {name: 'f'})" +
                    ", (g:Node {name: 'g'})" +
                    ", (h:Node {name: 'h'})" +
                    ", (i:Node {name: 'i'})" +

                    ", (a)-[:TYPE {weight: 1.0}]->(b)" +
                    ", (a)-[:TYPE {weight: 1.0}]->(c)" +
                    ", (b)-[:TYPE {weight: 1.0}]->(c)" +

                    ", (g)-[:TYPE {weight: 1.0}]->(h)" +
                    ", (g)-[:TYPE {weight: 1.0}]->(i)" +
                    ", (h)-[:TYPE {weight: 1.0}]->(i)" +

                    ", (e)-[:TYPE {weight: 1.0}]->(d)" +
                    ", (e)-[:TYPE {weight: 1.0}]->(f)" +
                    ", (d)-[:TYPE {weight: 1.0}]->(f)" +

                    ", (a)-[:TYPE {weight: 1.0}]->(g)" +
                    ", (c)-[:TYPE {weight: 1.0}]->(e)" +
                    ", (f)-[:TYPE {weight: 1.0}]->(i)";

    private static final Louvain.Config DEFAULT_CONFIG = new Louvain.Config(10, 10, false);

    static Stream<Class<? extends GraphFactory>> parameters() {
        return Stream.of(
                HeavyGraphFactory.class,
                HugeGraphFactory.class,
                GraphViewFactory.class
        );
    }

    private GraphDatabaseAPI DB;

    @BeforeEach
    void setup() {
        DB = TestDatabaseCreator.createTestDatabase();
    }

    @AfterEach
    void teardown() {
        if (null != DB) {
            DB.shutdown();
            DB = null;
        }
    }

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    private Graph setup(Class<? extends GraphFactory> graphImpl, String cypher) {
        DB.execute(cypher);
        return new GraphLoader(DB)
                .withAnyRelationshipType()
                .withAnyLabel()
                .withoutNodeProperties()
                .withOptionalRelationshipWeightsFromProperty("weight", 1.0)
                .undirected()
                .load(graphImpl);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testComplex(Class<? extends GraphFactory> graphImpl) {
        Graph graph = setup(graphImpl, COMPLEX_CYPHER);
        final Louvain algorithm = new Louvain(graph, DEFAULT_CONFIG, Pools.DEFAULT, 1, AllocationTracker.EMPTY)
                .withProgressLogger(TestProgressLogger.INSTANCE)
                .withTerminationFlag(TerminationFlag.RUNNING_TRUE)
                .compute();
        final HugeLongArray[] dendogram = algorithm.getDendrogram();
        for (int i = 1; i <= dendogram.length; i++) {
            if (null == dendogram[i - 1]) {
                break;
            }
            System.out.println("level " + i + ": " + dendogram[i - 1]);
        }

        assertArrayEquals(new long[]{0, 0, 0, 1, 1, 1, 2, 2, 2}, dendogram[0].toArray());
        assertArrayEquals(new long[]{0, 0, 0, 1, 1, 1, 2, 2, 2}, algorithm.getCommunityIds().toArray());
        assertEquals(0.53, algorithm.getFinalModularity(), 0.01);
        assertArrayEquals(new double[]{0.53}, algorithm.getModularities(), 0.01);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testComplexRNL(Class<? extends GraphFactory> graphImpl) {
        Graph graph = setup(graphImpl, COMPLEX_CYPHER);
        final Louvain algorithm = new Louvain(graph, DEFAULT_CONFIG, Pools.DEFAULT, 1, AllocationTracker.EMPTY)
                .withProgressLogger(TestProgressLogger.INSTANCE)
                .withTerminationFlag(TerminationFlag.RUNNING_TRUE)
                .compute(10, 10, true);
        final HugeLongArray[] dendogram = algorithm.getDendrogram();
        for (int i = 1; i <= dendogram.length; i++) {
            if (null == dendogram[i - 1]) {
                break;
            }
            System.out.println("level " + i + ": " + dendogram[i - 1]);
        }

    }
}
