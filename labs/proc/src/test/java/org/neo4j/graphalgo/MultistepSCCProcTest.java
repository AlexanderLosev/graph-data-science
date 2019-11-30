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

import com.carrotsearch.hppc.LongLongScatterMap;
import com.carrotsearch.hppc.cursors.LongLongCursor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Transaction;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
class MultistepSCCProcTest extends ProcTestBase{

    @BeforeEach
    void setup() throws Exception {
        final String cypher =
                "CREATE (a:Node {name:'a'})\n" +
                "CREATE (b:Node {name:'b'})\n" +
                "CREATE (c:Node {name:'c'})\n" +
                "CREATE (d:Node {name:'d'})\n" +
                "CREATE (e:Node {name:'e'})\n" +
                "CREATE (f:Node {name:'f'})\n" +
                "CREATE (g:Node {name:'g'})\n" +
                "CREATE (h:Node {name:'h'})\n" +
                "CREATE (i:Node {name:'i'})\n" +
                "CREATE (x:Node {name:'x'})\n" +
                "CREATE" +
                " (a)-[:TYPE {cost:5}]->(b),\n" +
                " (b)-[:TYPE {cost:5}]->(c),\n" +
                " (c)-[:TYPE {cost:5}]->(a),\n" +

                " (d)-[:TYPE {cost:2}]->(e),\n" +
                " (e)-[:TYPE {cost:2}]->(f),\n" +
                " (f)-[:TYPE {cost:2}]->(d),\n" +

                " (a)-[:TYPE {cost:2}]->(d),\n" +

                " (g)-[:TYPE {cost:3}]->(h),\n" +
                " (h)-[:TYPE {cost:3}]->(i),\n" +
                " (i)-[:TYPE {cost:3}]->(g)";

        db = TestDatabaseCreator.createTestDatabase();
        try (Transaction tx = db.beginTx()) {
            runQuery(cypher);
            tx.success();
        }

        registerProcedures(StronglyConnectedComponentsProc.class);
    }

    @AfterEach
    void shutdownGraph() {
        db.shutdown();
    }

    @Test
    void testWrite() {
        String cypher = "CALL algo.scc.multistep('Node', 'TYPE', {concurrency:4, cutoff:0}) " +
                "YIELD loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize " +
                "RETURN loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize";

        runQuery(cypher, row -> {
            assertTrue(row.getNumber("loadMillis").longValue() > 0L);
            assertTrue(row.getNumber("computeMillis").longValue() > 0L);
            assertTrue(row.getNumber("writeMillis").longValue() > 0L);

            assertEquals(3, row.getNumber("setCount").intValue());
            assertEquals(3, row.getNumber("minSetSize").intValue());
            assertEquals(3, row.getNumber("maxSetSize").intValue());
        });
    }

    @Test
    void testStream() {
        String cypher = "CALL algo.scc.multistep.stream('Node', 'TYPE', {write:true, concurrency:4, cutoff:0}) YIELD nodeId, cluster RETURN nodeId, cluster";
        final LongLongScatterMap testMap = new LongLongScatterMap();
        runQuery(cypher, row -> testMap.addTo(row.getNumber("cluster").longValue(), 1));
        // we expect 3 clusters
        assertEquals(3, testMap.size());
        // with 3 elements each
        testMap.forEach((Consumer<? super LongLongCursor>) cursor -> assertEquals(3, cursor.value));
    }
}
