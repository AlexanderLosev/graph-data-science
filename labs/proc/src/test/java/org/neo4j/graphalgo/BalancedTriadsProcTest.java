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
package org.neo4j.graphalgo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.neo4j.internal.kernel.api.exceptions.KernelException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

class BalancedTriadsProcTest extends ProcTestBase {

    @BeforeEach
    void setup() throws KernelException {
        db = TestDatabaseCreator.createTestDatabase();
        String cypher =
                "CREATE (a:Node {name:'a'})\n" + // center node
                "CREATE (b:Node {name:'b'})\n" +
                "CREATE (c:Node {name:'c'})\n" +
                "CREATE (d:Node {name:'d'})\n" +
                "CREATE (e:Node {name:'e'})\n" +
                "CREATE (f:Node {name:'f'})\n" +
                "CREATE (g:Node {name:'g'})\n" +
                "CREATE" +

                " (a)-[:TYPE {w:1.0}]->(b),\n" +
                " (a)-[:TYPE {w:-1.0}]->(c),\n" +
                " (a)-[:TYPE {w:1.0}]->(d),\n" +
                " (a)-[:TYPE {w:-1.0}]->(e),\n" +
                " (a)-[:TYPE {w:1.0}]->(f),\n" +
                " (a)-[:TYPE {w:-1.0}]->(g),\n" +

                " (b)-[:TYPE {w:-1.0}]->(c),\n" +
                " (c)-[:TYPE {w:1.0}]->(d),\n" +
                " (d)-[:TYPE {w:-1.0}]->(e),\n" +
                " (e)-[:TYPE {w:1.0}]->(f),\n" +
                " (f)-[:TYPE {w:-1.0}]->(g),\n" +
                " (g)-[:TYPE {w:1.0}]->(b)";

        db.execute(cypher);
        registerProcedures(BalancedTriadsProc.class);
    }

    @AfterEach
    void teardown() {
        db.shutdown();
    }

    @Test
    void testHuge() {
        db.execute(
                "CALL algo.balancedTriads('Node', 'TYPE', {weightProperty:'w', graph: 'huge'}) YIELD loadMillis, computeMillis, writeMillis, nodeCount, balancedTriadCount, unbalancedTriadCount")
                .accept(row -> {
                    assertEquals(3L, row.getNumber("balancedTriadCount"));
                    assertEquals(3L, row.getNumber("unbalancedTriadCount"));
                    return true;
                });
    }

    @Test
    void testHugeStream() {
        final BalancedTriadsConsumer mock = mock(BalancedTriadsConsumer.class);
        db.execute("CALL algo.balancedTriads.stream('Node', 'TYPE', {weightProperty:'w', graph: 'huge'}) YIELD nodeId, balanced, unbalanced")
                .accept(row -> {
                    final long nodeId = row.getNumber("nodeId").longValue();
                    final double balanced = row.getNumber("balanced").doubleValue();
                    final double unbalanced = row.getNumber("unbalanced").doubleValue();
                    mock.consume(nodeId, balanced, unbalanced);
                    return true;
                });
        verify(mock, times(7)).consume(anyLong(), AdditionalMatchers.eq(1.0, 3.0), AdditionalMatchers.eq(1.0, 3.0));
    }

    interface BalancedTriadsConsumer {
        void consume(long nodeId, double balanced, double unbalanced);
    }
}
