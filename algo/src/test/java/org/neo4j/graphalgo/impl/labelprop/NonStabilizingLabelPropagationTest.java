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
package org.neo4j.graphalgo.impl.labelprop;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyCypherGraphFactory;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.loader.HugeGraphFactory;
import org.neo4j.graphalgo.core.neo4jview.GraphViewFactory;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonStabilizingLabelPropagationTest {

    private static final String GRAPH =
            "CREATE " +
            " (a {community:1})" +
            ",(b {community:1})" +
            ",(c {community:1})" +
            ",(d {community:2})" +
            ",(e {community:2})" +
            ",(f {community:2})" +
            ",(g {community:3})" +
            ",(h {community:4})" +
            "CREATE " +
            " (g)-[:R]->(a)" +
            ",(a)-[:R]->(d)" +
            ",(d)-[:R]->(b)" +
            ",(b)-[:R]->(e)" +
            ",(e)-[:R]->(c)" +
            ",(c)-[:R]->(f)" +
            ",(f)-[:R]->(h)";

    static GraphDatabaseAPI DB;

    @BeforeAll
    public static void setupGraph() {
        DB = TestDatabaseCreator.createTestDatabase();
        DB.execute(GRAPH).close();
    }

    public Graph setup(Class<? extends GraphFactory> graphImpl) {
        GraphLoader graphLoader = new GraphLoader(DB, Pools.DEFAULT)
                .undirected()
                .withDefaultConcurrency();

        if (graphImpl == HeavyCypherGraphFactory.class) {
            graphLoader
                    .withLabel("MATCH (u) RETURN id(u) as id")
                    .withRelationshipType("MATCH (u1)-[rel]-(u2) \n" +
                                          "RETURN id(u1) AS source, id(u2) AS target")
                    .withName("cypher");
        } else {
            graphLoader
                    .withAnyLabel()
                    .withRelationshipType("R")
                    .withName(graphImpl.getSimpleName());
        }
        return graphLoader.load(graphImpl);
    }

    // According to "Near linear time algorithm to detect community structures in large-scale networks"[1], for a graph of this shape
    // LabelPropagation will not converge unless the iteration is random. However, we don't seem to be affected by this.
    // [1]: https://arxiv.org/pdf/0709.2938.pdf, page 5
    @ParameterizedTest
    @ValueSource(classes = {HeavyCypherGraphFactory.class, GraphViewFactory.class, HeavyGraphFactory.class, HugeGraphFactory.class})
    public void testLabelPropagationDoesStabilize(Class<? extends GraphFactory> graphImpl) {
        Graph graph = setup(graphImpl);
        LabelPropagation labelPropagation = new LabelPropagation(graph, ParallelUtil.DEFAULT_BATCH_SIZE, Pools.DEFAULT_CONCURRENCY, Pools.DEFAULT, AllocationTracker.EMPTY);
        LabelPropagation compute = labelPropagation.compute(Direction.OUTGOING, 10);
        compute.labels();
        assertTrue(compute.didConverge(), "Should converge");
    }

}
