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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphdb.Result;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Deprecated
public class LabelPropagationDeprecatedProcTest extends ProcTestBase {

    private static final String DB_CYPHER = "CREATE" +
            " (a:A {id: 0, community: 42}) " +
            ",(b:B {id: 1, community: 42}) " +

            ",(a)-[:X]->(:A {id: 2,  weight: 1.0, score: 1.0, community: 1}) " +
            ",(a)-[:X]->(:A {id: 3,  weight: 2.0, score: 2.0, community: 1}) " +
            ",(a)-[:X]->(:A {id: 4,  weight: 1.0, score: 1.0, community: 1}) " +
            ",(a)-[:X]->(:A {id: 5,  weight: 1.0, score: 1.0, community: 1}) " +
            ",(a)-[:X]->(:A {id: 6,  weight: 8.0, score: 8.0, community: 2}) " +

            ",(b)-[:X]->(:B {id: 7,  weight: 1.0, score: 1.0, community: 1}) " +
            ",(b)-[:X]->(:B {id: 8,  weight: 2.0, score: 2.0, community: 1}) " +
            ",(b)-[:X]->(:B {id: 9,  weight: 1.0, score: 1.0, community: 1}) " +
            ",(b)-[:X]->(:B {id: 10, weight: 1.0, score: 1.0, community: 1}) " +
            ",(b)-[:X]->(:B {id: 11, weight: 8.0, score: 8.0, community: 2})";

    static Stream<Arguments> parameters() {
        return Stream.of(
                arguments(false, "huge"),
                arguments(true,  "huge")
        );
    }

    @BeforeEach
    public void setup() throws KernelException {
        DB = TestDatabaseCreator.createTestDatabase();

        DB.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(LabelPropagationProc.class);

        DB.execute(DB_CYPHER);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldTakeDifferentSeedProperties(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    null, null, null, {" +
                       "        seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: 'lpa'" +
                       "    }" +
                       ")";

        runQuery(query, parParams(parallel, graphImpl),
                row -> {
                    assertEquals(1, row.getNumber("iterations").intValue());
                    assertEquals("weight", row.getString("weightProperty"));
                    assertEquals("community", row.getString("seedProperty"));
                    assertEquals("lpa", row.getString("writeProperty"));
                    assertTrue(row.getBoolean("write"));
                }
        );

        query = "CALL algo.labelPropagation(" +
                "   null, null, null, {" +
                "       partitionProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: 'lpa'" +
                "   }" +
                ")";

        runQuery(query, parParams(parallel, graphImpl),
                row -> {
                    assertEquals(1, row.getNumber("iterations").intValue());
                    assertEquals("weight", row.getString("weightProperty"));
                    assertEquals("community", row.getString("seedProperty"));
                    assertEquals("lpa", row.getString("writeProperty"));
                    assertTrue(row.getBoolean("write"));
                }
        );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void explicitWriteProperty(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    null, null, null, {" +
                       "        seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: 'lpa'" +
                       "    }" +
                       ")";

        runQuery(query, parParams(parallel, graphImpl),
                row -> {
                    assertEquals(1, row.getNumber("iterations").intValue());
                    assertEquals("weight", row.getString("weightProperty"));
                    assertEquals("community", row.getString("seedProperty"));
                    assertEquals("lpa", row.getString("writeProperty"));
                    assertTrue(row.getBoolean("write"));
                }
        );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldTakeParametersFromConfig(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    null, null, null, {" +
                       "        iterations: 5, write: false, weightProperty: 'score', seedProperty: $seedProperty" +
                       "    }" +
                       ")";

        runQuery(query, parParams(parallel, graphImpl),
                row -> {
                    assertTrue(5 >= row.getNumber("iterations").intValue());
                    assertTrue(row.getBoolean("didConverge"));
                    assertFalse(row.getBoolean("write"));
                    assertEquals("score", row.getString("weightProperty"));
                    assertEquals("community", row.getString("seedProperty"));
                }
        );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldRunLabelPropagation(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    null, 'X', 'OUTGOING', {" +
                       "        batchSize: $batchSize, concurrency: $concurrency, graph: $graph, seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: $writeProperty" +
                       "    }" +
                       ")";
        String check = "MATCH (n) WHERE n.id IN [0,1] RETURN n.community AS community";

        runQuery(query, parParams(parallel, graphImpl),
                row -> {
                    assertEquals(12, row.getNumber("nodes").intValue());
                    assertTrue(row.getBoolean("write"));

                    assertTrue(
                            row.getNumber("loadMillis").intValue() >= 0,
                            "load time not set");
                    assertTrue(
                            row.getNumber("computeMillis").intValue() >= 0,
                            "compute time not set");
                    assertTrue(
                            row.getNumber("writeMillis").intValue() >= 0,
                            "write time not set");
                }
        );
        runQuery(check, row -> assertEquals(2, row.getNumber("community").intValue()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldFilterByLabel(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    'A', 'X', 'OUTGOING', {" +
                       "        batchSize: $batchSize, concurrency: $concurrency, graph: $graph, writeProperty: $writeProperty" +
                       "    }" +
                       ")";
        String checkA = "MATCH (n) WHERE n.id = 0 RETURN n.community AS community";
        String checkB = "MATCH (n) WHERE n.id = 1 RETURN n.community AS community";

        runQuery(query, parParams(parallel, graphImpl));
        runQuery(checkA, row -> assertEquals(2, row.getNumber("community").intValue()));
        runQuery(checkB, row -> assertEquals(42, row.getNumber("community").intValue()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldPropagateIncoming(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    'A', 'X', 'INCOMING', {" +
                       "        batchSize: $batchSize, concurrency: $concurrency, graph: $graph, seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: $writeProperty" +
                       "    }" +
                       ")";
        String check = "MATCH (n:A) WHERE n.id <> 0 RETURN n.community AS community";

        runQuery(query, parParams(parallel, graphImpl));
        runQuery(check, row -> assertEquals(42, row.getNumber("community").intValue()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldAllowCypherGraph(boolean parallel, String graphImpl) {
        String query = "CALL algo.labelPropagation(" +
                       "    'MATCH (n) RETURN id(n) AS id, n.weight AS weight, n.seed AS value', " +
                       "    'MATCH (s)-[r:X]->(t) RETURN id(s) AS source, id(t) AS target, r.weight AS weight', " +
                       "    'OUTGOING', {" +
                       "        graph: 'cypher', batchSize: $batchSize, concurrency: $concurrency, writeProperty: $writeProperty" +
                       "    }" +
                       ")";
        runQuery(query, parParams(parallel, graphImpl), row -> assertEquals(12, row.getNumber("nodes").intValue()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testGeneratedAndProvidedLabelsDontConflict(boolean parallel, String graphImpl) throws KernelException {
        GraphDatabaseAPI db = TestDatabaseCreator.createTestDatabase();
        db.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(LabelPropagationProc.class);

        int seededLabel = 1;
        // When
        String query = "CREATE " +
                       " (a:Pet {id: 0,type: 'cat',   seedId: $seed}) " +
                       ",(b:Pet {id: 1,type: 'okapi', seedId: $seed}) " +
                       ",(c:Pet {id: 2,type: 'koala'}) " +
                       ",(d:Pet {id: 3,type: 'python'}) " +
                       ",(a)<-[:REL]-(c) " +
                       ",(b)<-[:REL]-(c) " +
                       "RETURN id(d) AS maxId";

        // (c) will get seed 1
        // (d) will get seed id(d) + 1
        Result initResult = db.execute(query, Collections.singletonMap("seed", seededLabel));
        long maxId = Iterators.single(initResult.<Number>columnAs("maxId")).longValue();

        String lpa = "CALL algo.labelPropagation.stream(" +
                     "  'Pet', 'REL', {" +
                     "      seedProperty: 'seedId'" +
                     "  }" +
                     ") YIELD nodeId, label " +
                     "MATCH (pet:Pet) WHERE id(pet) = nodeId " +
                     "RETURN pet.id as nodeId, label AS community";

        long[] sets = new long[4];
        db.execute(lpa).accept(row -> {
            int nodeId = row.getNumber("nodeId").intValue();
            long setId = row.getNumber("community").longValue();
            sets[nodeId] = setId;
            return true;
        });

        long newLabel = maxId + seededLabel + 1;
        assertArrayEquals(new long[]{1, 1, 1, newLabel}, sets, "Incorrect result assuming initial labels are neo4j id");
    }

    private Map<String, Object> parParams(boolean parallel, String graphImpl) {
        return MapUtil.map(
                "batchSize", parallel ? 1 : 100,
                "concurrency", parallel ? 8: 1,
                "graph", graphImpl,
                "seedProperty", "community",
                "weightProperty", "weight",
                "writeProperty", "community"
        );
    }
}
