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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.Result;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class LabelPropagationProcTest {

    private static final String DB_CYPHER = "" +
            "CREATE (a:A {id: 0, community: 42}) " +
            "CREATE (b:B {id: 1, community: 42}) " +

            "CREATE (a)-[:X]->(:A {id: 2,  weight: 1.0, score: 1.0, community: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 3,  weight: 2.0, score: 2.0, community: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 4,  weight: 1.0, score: 1.0, community: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 5,  weight: 1.0, score: 1.0, community: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 6,  weight: 8.0, score: 8.0, community: 2}) " +

            "CREATE (b)-[:X]->(:B {id: 7,  weight: 1.0, score: 1.0, community: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 8,  weight: 2.0, score: 2.0, community: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 9,  weight: 1.0, score: 1.0, community: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 10, weight: 1.0, score: 1.0, community: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 11, weight: 8.0, score: 8.0, community: 2})";

    @Parameterized.Parameters(name = "parallel={0}, graph={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{false, "heavy"},
                new Object[]{true, "heavy"},
                new Object[]{false, "huge"},
                new Object[]{true, "huge"}
        );
    }

    @Rule
    public ImpermanentDatabaseRule db = new ImpermanentDatabaseRule();

    @Rule
    public ExpectedException exceptions = ExpectedException.none();

    private final boolean parallel;
    private final String graphImpl;

    public LabelPropagationProcTest(boolean parallel, String graphImpl) {
        this.parallel = parallel;
        this.graphImpl = graphImpl;
    }

    @Before
    public void setup() throws KernelException {
        db.resolveDependency(Procedures.class).registerProcedure(LabelPropagationProc.class);
        db.execute(DB_CYPHER);
    }

    @Test
    public void shouldTakeDifferentSeedProperties() {
        String query = "CALL algo.labelPropagation(null, null, null, {seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: 'lpa'})";

        runQuery(query, parParams(), row -> {
            assertEquals(1, row.getNumber("iterations").intValue());
            assertEquals("weight", row.getString("weightProperty"));
            assertEquals("community", row.getString("seedProperty"));
            assertEquals("lpa", row.getString("writeProperty"));
            assertTrue(row.getBoolean("write"));
        });

        query = "CALL algo.labelPropagation(null, null, null, {partitionProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: 'lpa'})";

        runQuery(query, parParams(), row -> {
            assertEquals(1, row.getNumber("iterations").intValue());
            assertEquals("weight", row.getString("weightProperty"));
            assertEquals("community", row.getString("seedProperty"));
            assertEquals("lpa", row.getString("writeProperty"));
            assertTrue(row.getBoolean("write"));
        });
    }

    @Test
    public void explicitWriteProperty() {
        String query = "CALL algo.labelPropagation(null, null, null, {seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: 'lpa'})";

        runQuery(query, parParams(), row -> {
            assertEquals(1, row.getNumber("iterations").intValue());
            assertEquals("weight", row.getString("weightProperty"));
            assertEquals("community", row.getString("seedProperty"));
            assertEquals("lpa", row.getString("writeProperty"));
            assertTrue(row.getBoolean("write"));
        });
    }

    @Test
    public void shouldTakeParametersFromConfig() {
        String query = "CALL algo.labelPropagation(null, null, null, {iterations: 5, write: false, weightProperty: 'score', seedProperty: $seedProperty})";

        runQuery(query, parParams(), row -> {
            assertTrue(5 >= row.getNumber("iterations").intValue());
            assertTrue(row.getBoolean("didConverge"));
            assertFalse(row.getBoolean("write"));
            assertEquals("score", row.getString("weightProperty"));
            assertEquals("community", row.getString("seedProperty"));
        });
    }

    @Test
    public void shouldRunLabelPropagation() {
        String query = "CALL algo.labelPropagation(null, 'X', 'OUTGOING', {batchSize: $batchSize, concurrency: $concurrency, graph: $graph, seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: $writeProperty})";
        String check = "MATCH (n) WHERE n.id IN [0,1] RETURN n.community AS community";

        runQuery(query, parParams(), row -> {
            assertEquals(12, row.getNumber("nodes").intValue());
            assertTrue(row.getBoolean("write"));

            assertTrue(
                    "load time not set",
                    row.getNumber("loadMillis").intValue() >= 0);
            assertTrue(
                    "compute time not set",
                    row.getNumber("computeMillis").intValue() >= 0);
            assertTrue(
                    "write time not set",
                    row.getNumber("writeMillis").intValue() >= 0);
        });
        runQuery(check, row ->
                assertEquals(2, row.getNumber("community").intValue()));
    }

    @Test
    public void shouldFilterByLabel() {
        String query = "CALL algo.labelPropagation('A', 'X', 'OUTGOING', {batchSize: $batchSize, concurrency: $concurrency, graph: $graph, writeProperty: $writeProperty})";
        String checkA = "MATCH (n) WHERE n.id = 0 RETURN n.community AS community";
        String checkB = "MATCH (n) WHERE n.id = 1 RETURN n.community AS community";

        runQuery(query, parParams());
        runQuery(checkA, row ->
                assertEquals(2, row.getNumber("community").intValue()));
        runQuery(checkB, row ->
                assertEquals(42, row.getNumber("community").intValue()));
    }

    @Test
    public void shouldPropagateIncoming() {
        String query = "CALL algo.labelPropagation('A', 'X', 'INCOMING', {batchSize: $batchSize, concurrency: $concurrency, graph: $graph, seedProperty: $seedProperty, weightProperty: $weightProperty, writeProperty: $writeProperty})";
        String check = "MATCH (n:A) WHERE n.id <> 0 RETURN n.community AS community";

        runQuery(query, parParams());
        runQuery(check, row ->
                assertEquals(42, row.getNumber("community").intValue()));
    }

    @Test
    public void shouldAllowCypherGraph() {
        String query = "CALL algo.labelPropagation(" +
                       "'MATCH (n) RETURN id(n) AS id, n.weight AS weight, n.seed AS value', " +
                       "'MATCH (s)-[r:X]->(t) RETURN id(s) AS source, id(t) AS target, r.weight AS weight', " +
                       "'OUTGOING', " +
                       "{graph: 'cypher', batchSize: $batchSize, concurrency: $concurrency, writeProperty: $writeProperty}" +
                       ")";
        runQuery(query, parParams(), row -> assertEquals(12, row.getNumber("nodes").intValue()));
    }

    @Test
    public void shouldStreamResults() {
        // this one deliberately tests the streaming and non streaming versions against each other to check we get the same results
        // we intentionally start with no labels defined for any nodes (hence seedProperty = {lpa, lpa2})

        runQuery("CALL algo.labelPropagation(null, null, 'OUTGOING', {iterations: 20, writeProperty: 'lpa', weightProperty: $weightProperty})", parParams(), row -> {});

        String query = "CALL algo.labelPropagation.stream(null, null, {iterations: 20, direction: 'OUTGOING', weightProperty: $weightProperty}) " +
                "YIELD nodeId, community " +
                "MATCH (node) WHERE id(node) = nodeId " +
                "RETURN node.id AS id, id(node) AS internalNodeId, node.lpa AS seedProperty, community";

        runQuery(query, parParams(), row -> {
            assertEquals(row.getNumber("seedProperty").intValue(), row.getNumber("community").intValue());
        });
    }

    @Test
    public void testGeneratedAndProvidedLabelsDontConflict() throws KernelException {
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

        String lpa = "CALL algo.labelPropagation.stream('Pet', 'REL', {seedProperty: 'seedId'}) " +
                     "YIELD nodeId, community " +
                     "MATCH (pet:Pet) WHERE id(pet) = nodeId " +
                     "RETURN pet.id as nodeId, community";

        long[] sets = new long[4];
        db.execute(lpa).accept(row -> {
            int nodeId = row.getNumber("nodeId").intValue();
            long setId = row.getNumber("community").longValue();
            sets[nodeId] = setId;
            return true;
        });

        long newLabel = maxId + seededLabel + 1;
        assertArrayEquals("Incorrect result assuming initial labels are neo4j id", new long[]{1, 1, 1, newLabel}, sets);
    }

    private void runQuery(String query, Map<String,  Object> params) {
        runQuery(query, params, row -> {});
    }

    private void runQuery(
            String query,
            Consumer<Result.ResultRow> check) {
        runQuery(query, Collections.emptyMap(), check);
    }

    private void runQuery(
            String query,
            Map<String, Object> params,
            Consumer<Result.ResultRow> check) {
        try (Result result = db.execute(query, params)) {
            result.accept(row -> {
                check.accept(row);
                return true;
            });
        }
    }

    private Map<String, Object> parParams() {
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
