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
package org.neo4j.graphalgo.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.loading.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.impl.triangle.BalancedTriads;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class BalancedTriadsTest extends AlgoTestBase {

    private static final String DB_CYPHER =
        "CREATE " +
        "  (a:Node {name: 'a'})" + // center node
        ", (b:Node {name: 'b'})" +
        ", (c:Node {name: 'c'})" +
        ", (d:Node {name: 'd'})" +
        ", (e:Node {name: 'e'})" +
        ", (f:Node {name: 'f'})" +
        ", (g:Node {name: 'g'})" +

        ", (a)-[:TYPE {w: 1.0}]->(b)" +
        ", (a)-[:TYPE {w: -1.0}]->(c)" +
        ", (a)-[:TYPE {w: 1.0}]->(d)" +
        ", (a)-[:TYPE {w: -1.0}]->(e)" +
        ", (a)-[:TYPE {w: 1.0}]->(f)" +
        ", (a)-[:TYPE {w: -1.0}]->(g)" +

        ", (b)-[:TYPE {w: -1.0}]->(c)" +
        ", (c)-[:TYPE {w: 1.0}]->(d)" +
        ", (d)-[:TYPE {w: -1.0}]->(e)" +
        ", (e)-[:TYPE {w: 1.0}]->(f)" +
        ", (f)-[:TYPE {w: -1.0}]->(g)" +
        ", (g)-[:TYPE {w: 1.0}]->(b)";

    @Mock
    final BalancedTriadTestConsumer consumer = mock(BalancedTriadTestConsumer.class);

    interface BalancedTriadTestConsumer {
        void accept(long node, long balanced, long unbalanced);
    }

    private static Graph graph;

    @BeforeEach
    void setupGraphDb() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(DB_CYPHER);
        graph = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node")
            .addRelationshipType("TYPE")
            .globalProjection(Projection.UNDIRECTED)
            .addRelationshipProperty(PropertyMapping.of("w", 0.0))
            .build()
            .graph(HugeGraphFactory.class);
    }

    @BeforeEach
    void shutdown() {
        db.shutdown();
    }

    @Test
    void testStream() {
        BalancedTriads algo = new BalancedTriads(graph, Pools.DEFAULT, 4, AllocationTracker.EMPTY);
        algo.compute();
        algo.computeStream().forEach(r -> consumer.accept(r.nodeId, r.balanced, r.unbalanced));
        verify(consumer, times(1)).accept(eq(0L), eq(3L), eq(3L));
        verify(consumer, times(6)).accept(anyLong(), eq(1L), eq(1L));
    }
}
