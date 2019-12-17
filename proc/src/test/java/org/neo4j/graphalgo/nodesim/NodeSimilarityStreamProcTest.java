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

package org.neo4j.graphalgo.nodesim;

import org.apache.commons.compress.utils.Sets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphalgo.ProcTestBase;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.QueryRunner;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestSupport;
import org.neo4j.graphalgo.compat.MapUtil;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.graphalgo.Projection.NATURAL;
import static org.neo4j.graphalgo.Projection.REVERSE;
import static org.neo4j.graphalgo.TestSupport.crossArguments;
import static org.neo4j.graphalgo.TestSupport.toArguments;
import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;

class NodeSimilarityStreamProcTest extends ProcTestBase {

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Person {id: 0,  name: 'Alice'})" +
        ", (b:Person {id: 1,  name: 'Bob'})" +
        ", (c:Person {id: 2,  name: 'Charlie'})" +
        ", (d:Person {id: 3,  name: 'Dave'})" +
        ", (i1:Item  {id: 10, name: 'p1'})" +
        ", (i2:Item  {id: 11, name: 'p2'})" +
        ", (i3:Item  {id: 12, name: 'p3'})" +
        ", (i4:Item  {id: 13, name: 'p4'})" +
        ", (a)-[:LIKES]->(i1)" +
        ", (a)-[:LIKES]->(i2)" +
        ", (a)-[:LIKES]->(i3)" +
        ", (b)-[:LIKES]->(i1)" +
        ", (b)-[:LIKES]->(i2)" +
        ", (c)-[:LIKES]->(i3)";

    private static final Collection<String> EXPECTED_OUTGOING = new HashSet<>();
    private static final Collection<String> EXPECTED_INCOMING = new HashSet<>();
    private static final Collection<String> EXPECTED_TOP_OUTGOING = new HashSet<>();
    private static final Collection<String> EXPECTED_TOP_INCOMING = new HashSet<>();

    private static String resultString(long node1, long node2, double similarity) {
        return String.format("%d,%d %f%n", node1, node2, similarity);
    }

    static Stream<Arguments> allGraphNamesWithIncomingOutgoing() {
        return crossArguments(toArguments(TestSupport::allGraphNames), toArguments(() -> Stream.of(INCOMING, OUTGOING)));
    }

    static Stream<Arguments> allValidProjections() {
        return Stream.of(arguments(NATURAL), arguments(REVERSE));
    }

    static {
        EXPECTED_OUTGOING.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(0, 2, 1 / 3.0));
        EXPECTED_OUTGOING.add(resultString(1, 2, 0.0));
        // With mandatory topK, expect results in both directions
        EXPECTED_OUTGOING.add(resultString(1, 0, 2 / 3.0));
        EXPECTED_OUTGOING.add(resultString(2, 0, 1 / 3.0));
        EXPECTED_OUTGOING.add(resultString(2, 1, 0.0));

        EXPECTED_TOP_OUTGOING.add(resultString(0, 1, 2 / 3.0));
        EXPECTED_TOP_OUTGOING.add(resultString(1, 0, 2 / 3.0));

        EXPECTED_INCOMING.add(resultString(4, 5, 3.0 / 3.0));
        EXPECTED_INCOMING.add(resultString(4, 6, 1 / 3.0));
        EXPECTED_INCOMING.add(resultString(5, 6, 1 / 3.0));
        // With mandatory topK, expect results in both directions
        EXPECTED_INCOMING.add(resultString(5, 4, 3.0 / 3.0));
        EXPECTED_INCOMING.add(resultString(6, 4, 1 / 3.0));
        EXPECTED_INCOMING.add(resultString(6, 5, 1 / 3.0));

        EXPECTED_TOP_INCOMING.add(resultString(4, 5, 3.0 / 3.0));
        EXPECTED_TOP_INCOMING.add(resultString(5, 4, 3.0 / 3.0));
    }

    @BeforeEach
    void setup() throws Exception {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(DB_CYPHER);
        registerProcedures(NodeSimilarityStreamProc.class);
    }

    @AfterEach
    void teardown() {
        db.shutdown();
    }

    @Test
    void shouldDealWithAnyIdSpace() throws Exception {
        int idOffset = 100;
        GraphDatabaseAPI localDb = TestDatabaseCreator.createTestDatabase();
        registerProcedures(localDb, NodeSimilarityStreamProc.class);
        QueryRunner.runQuery(localDb, "MATCH (n) DETACH DELETE n");
        QueryRunner.runQuery(localDb, String.format("UNWIND range(1, %d) AS i CREATE (:IncrementIdSpace)", idOffset));
        QueryRunner.runQuery(localDb, DB_CYPHER);
        QueryRunner.runQuery(localDb, "MATCH (n:IncrementIdSpace) DELETE n");

        HashSet<String> expected = Sets.newHashSet(
            resultString(idOffset + 0, idOffset + 1, 2 / 3.0),
            resultString(idOffset + 0, idOffset + 2, 1 / 3.0),
            resultString(idOffset + 1, idOffset + 2, 0.0),
            resultString(idOffset + 1, idOffset + 0, 2 / 3.0),
            resultString(idOffset + 2, idOffset + 0, 1 / 3.0),
            resultString(idOffset + 2, idOffset + 1, 0.0)
        );

        String query = "CALL gds.algo.nodeSimilarity.stream(" +
                       "    {" +
                       "        nodeProjection: '' " +
                       "        , relationshipProjection: {" +
                       "            LIKES: {" +
                       "                type: 'LIKES'" +
                       "                , projection: 'natural'" +
                       "            }" +
                       "        }" +
                       "        , similarityCutoff: 0.0" +
                       "        , direction: 'OUTGOING'" +
                       "    }" +
                       ") YIELD node1, node2, similarity";

        Collection<String> result = new HashSet<>();
        runQuery(localDb, query, row -> {
                long node1 = row.getNumber("node1").longValue();
                long node2 = row.getNumber("node2").longValue();
                double similarity = row.getNumber("similarity").doubleValue();
                result.add(resultString(node1, node2, similarity));
            }
        );

        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{0} -- {1}")
    @MethodSource("allValidProjections")
    void shouldStreamResults(Projection projection) {
        Direction direction = projection == REVERSE ? INCOMING : OUTGOING;
        String query = "CALL gds.algo.nodeSimilarity.stream(" +
                       "    {" +
                       "        nodeProjection: '' " +
                       "        , relationshipProjection: {" +
                       "            LIKES: {" +
                       "                type: 'LIKES'" +
                       "                , projection: $projection" +
                       "            }" +
                       "        }" +
                       "        , direction: $direction" +
                       "        , similarityCutoff: 0.0" +
                       "    }" +
                       ") YIELD node1, node2, similarity";

        Collection<String> result = new HashSet<>();
        runQuery(query, MapUtil.map("projection", projection.name(), "direction", direction.name()),
            row -> {
                long node1 = row.getNumber("node1").longValue();
                long node2 = row.getNumber("node2").longValue();
                double similarity = row.getNumber("similarity").doubleValue();
                result.add(resultString(node1, node2, similarity));
            });

        assertEquals(
            direction == INCOMING
                ? EXPECTED_INCOMING
                : EXPECTED_OUTGOING,
            result
        );
    }

    @ParameterizedTest(name = "{0} -- {1}")
    @MethodSource("allValidProjections")
    void shouldStreamTopResults(Projection projection) {
        Direction direction = projection == REVERSE ? INCOMING : OUTGOING;
        int topN = 2;
        String query = "CALL gds.algo.nodeSimilarity.stream(" +
                       "    {" +
                       "        nodeProjection: ''" +
                       "        , relationshipProjection: {" +
                       "            LIKES: {" +
                       "                type: 'LIKES'" +
                       "                , projection: $projection" +
                       "            }" +
                       "        }" +
                       "        , direction: $direction" +
                       "        , topN: $topN" +
                       "    }" +
                       ") YIELD node1, node2, similarity";

        Collection<String> result = new HashSet<>();
        runQuery(query, MapUtil.map("projection", projection.name(), "direction", direction.name(), "topN", topN),
            row -> {
                long node1 = row.getNumber("node1").longValue();
                long node2 = row.getNumber("node2").longValue();
                double similarity = row.getNumber("similarity").doubleValue();
                result.add(resultString(node1, node2, similarity));
            });

        assertEquals(
            direction == INCOMING
                ? EXPECTED_TOP_INCOMING
                : EXPECTED_TOP_OUTGOING,
            result
        );
    }

    @ParameterizedTest(name = "{0} -- {1}")
    @MethodSource("allValidProjections")
    void shouldIgnoreParallelEdges(Projection projection) {
        Direction direction = projection == REVERSE ? INCOMING : OUTGOING;
        // Add parallel edges
        runQuery("" +
                 " MATCH (person {name: 'Alice'})" +
                 " MATCH (thing {name: 'p1'})" +
                 " CREATE (person)-[:LIKES]->(thing)"
        );
        runQuery("" +
                 " MATCH (person {name: 'Charlie'})" +
                 " MATCH (thing {name: 'p3'})" +
                 " CREATE (person)-[:LIKES]->(thing)" +
                 " CREATE (person)-[:LIKES]->(thing)" +
                 " CREATE (person)-[:LIKES]->(thing)"
        );

        String query = "CALL gds.algo.nodeSimilarity.stream(" +
                       "    {" +
                       "        nodeProjection: ''" +
                       "        , relationshipProjection: {" +
                       "            LIKES: {" +
                       "                type: 'LIKES'" +
                       "                , projection: $projection" +
                       "            }" +
                       "        }" +
                       "        , direction: $direction" +
                       "        , similarityCutoff: 0.0" +
                       "    }" +
                       ") YIELD node1, node2, similarity";

        Collection<String> result = new HashSet<>();
        runQuery(query, MapUtil.map("projection", projection.name(), "direction", direction.name()),
            row -> {
                long node1 = row.getNumber("node1").longValue();
                long node2 = row.getNumber("node2").longValue();
                double similarity = row.getNumber("similarity").doubleValue();
                result.add(resultString(node1, node2, similarity));
            });

        assertEquals(
            direction == INCOMING
                ? EXPECTED_INCOMING
                : EXPECTED_OUTGOING,
            result
        );
    }
}
