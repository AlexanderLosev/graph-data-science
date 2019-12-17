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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.GraphLoadProc;
import org.neo4j.graphalgo.ProcTestBase;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.compat.MapUtil;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.impl.nodesim.NodeSimilarityConfigBase;
import org.neo4j.graphalgo.impl.nodesim.NodeSimilarityWriteConfig;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.graphalgo.Projection.NATURAL;
import static org.neo4j.graphalgo.Projection.REVERSE;
import static org.neo4j.graphalgo.TestGraph.Builder.fromGdl;
import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;
import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;

public class NodeSimilarityProcTest extends ProcTestBase {

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

    static Stream<Arguments> allValidProjections() {
        return Stream.of(arguments(NATURAL), arguments(REVERSE));
    }

    @BeforeEach
    void setup() throws Exception {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(DB_CYPHER);
        registerProcedures(NodeSimilarityWriteProc.class, GraphLoadProc.class);
    }

    @AfterEach
    void teardown() {
        db.shutdown();
    }

    @ParameterizedTest(name = "{0} -- {1}")
    @MethodSource("allValidProjections")
    void shouldWriteResults(Projection projection) {
        String query = "CALL gds.algo.nodeSimilarity.write(" +
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
                       "        , writeRelationshipType: 'SIMILAR'" +
                       "        , writeProperty: 'score'" +
                       "    }" +
                       ") YIELD" +
                       " computeMillis" +
                       ", loadMillis" +
                       ", nodesCompared " +
                       ", relationships" +
                       ", writeMillis" +
                       ", writeProperty" +
                       ", writeRelationshipType" +
                       ", similarityDistribution" +
                       ", postProcessingMillis";
        String direction = projection == REVERSE ? INCOMING.name() : OUTGOING.name();

        runQuery(query, MapUtil.map("projection", projection.name(), "direction", direction),
            row -> {
                assertEquals(3, row.getNumber("nodesCompared").longValue());
                assertEquals(6, row.getNumber("relationships").longValue());
                assertEquals("SIMILAR", row.getString("writeRelationshipType"));
                assertEquals("score", row.getString("writeProperty"));
                assertThat("Missing computeMillis", -1L, lessThan(row.getNumber("computeMillis").longValue()));
                assertThat("Missing loadMillis", -1L, lessThan(row.getNumber("loadMillis").longValue()));
                assertThat("Missing writeMillis", -1L, lessThan(row.getNumber("writeMillis").longValue()));

                Map<String, Double> distribution = (Map<String, Double>) row.get("similarityDistribution");
                assertThat("Missing min", -1.0,    lessThan(distribution.get("min")));
                assertThat("Missing max", -1.0,    lessThan(distribution.get("max")));
                assertThat("Missing mean", -1.0  , lessThan(distribution.get("mean")));
                assertThat("Missing stdDev", -1.0, lessThan(distribution.get("stdDev")));
                assertThat("Missing p1", -1.0,     lessThan(distribution.get("p1")));
                assertThat("Missing p5", -1.0,     lessThan(distribution.get("p5")));
                assertThat("Missing p10", -1.0,    lessThan(distribution.get("p10")));
                assertThat("Missing p25", -1.0,    lessThan(distribution.get("p25")));
                assertThat("Missing p50", -1.0,    lessThan(distribution.get("p50")));
                assertThat("Missing p75", -1.0,    lessThan(distribution.get("p75")));
                assertThat("Missing p90", -1.0,    lessThan(distribution.get("p90")));
                assertThat("Missing p95", -1.0,    lessThan(distribution.get("p95")));
                assertThat("Missing p99", -1.0,    lessThan(distribution.get("p99")));
                assertThat("Missing p100", -1.0,   lessThan(distribution.get("p100")));

                assertThat("Missing postProcessingMillis", -1L, equalTo(row.getNumber("postProcessingMillis").longValue()));
            }
        );

        String resultGraphName = "simGraph_" + projection.name();
        String loadQuery = "CALL algo.graph.load($resultGraphName, $label, 'SIMILAR', {nodeProperties: 'id', relationshipProperties: 'score', projection: $projection})";
        runQuery(loadQuery, MapUtil.map("resultGraphName", resultGraphName, "label", projection == REVERSE ? "Item" : "Person", "projection", projection.name()));
        Graph simGraph = GraphCatalog.getUnion(getUsername(), resultGraphName).orElse(null);
        assertNotNull(simGraph);
        assertGraphEquals(projection == REVERSE
                ? fromGdl(
                    String.format(
                        "  (i1 {id: 10})" +
                        ", (i2 {id: 11})" +
                        ", (i3 {id: 12})" +
                        ", (i4 {id: 13})" +
                        ", (i1)-[{w: %f}]->(i2)" +
                        ", (i1)-[{w: %f}]->(i3)" +
                        ", (i2)-[{w: %f}]->(i1)" +
                        ", (i2)-[{w: %f}]->(i3)" +
                        ", (i3)-[{w: %f}]->(i1)" +
                        ", (i3)-[{w: %f}]->(i2)",
                        1 / 1.0,
                        1 / 3.0,
                        1 / 1.0,
                        1 / 3.0,
                        1 / 3.0,
                        1 / 3.0
                    )
                )
                : fromGdl(
                    String.format(
                        "  (a {id: 0})" +
                        ", (b {id: 1})" +
                        ", (c {id: 2})" +
                        ", (d {id: 3})" +
                        ", (a)-[{w: %f}]->(b)" +
                        ", (a)-[{w: %f}]->(c)" +
                        ", (b)-[{w: %f}]->(c)" +
                        ", (b)-[{w: %f}]->(a)" +
                        ", (c)-[{w: %f}]->(a)" +
                        ", (c)-[{w: %f}]->(b)"
                        , 2 / 3.0
                        , 1 / 3.0
                        , 0.0
                        , 2 / 3.0
                        , 1 / 3.0
                        , 0.0
                    )
                ),
            simGraph
        );
    }

    @ParameterizedTest(name = "parameter: {0}, value: {1}")
    @CsvSource(value = {"topN, -2", "bottomN, -2", "topK, -2", "bottomK, -2", "topK, 0", "bottomK, 0"})
    void shouldThrowForInvalidTopsAndBottoms(String parameter, long value) {
        String message = String.format("Invalid value for %s: must be a positive integer", parameter);
        CypherMapWrapper input = baseUserInput().withNumber(parameter, value);

        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> config(input)
        );
        assertThat(illegalArgumentException.getMessage(), containsString(message));
    }

    @ParameterizedTest
    @CsvSource(value = {"topK, bottomK", "topN, bottomN"})
    void shouldThrowForInvalidTopAndBottomCombination(String top, String bottom) {
        CypherMapWrapper input = baseUserInput().withNumber(top, 1).withNumber(bottom, 1);

        String expectedMessage = String.format("Invalid parameter combination: %s combined with %s", top, bottom);

        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> config(input)
        );
        assertThat(illegalArgumentException.getMessage(), is(expectedMessage));
    }

    @Test
    void shouldThrowIfDegreeCutoffSetToZero() {
        CypherMapWrapper input = baseUserInput().withNumber("degreeCutoff", 0);

        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> config(input)
        );
        assertThat(illegalArgumentException.getMessage(), is("Must set degree cutoff to 1 or greater"));
    }

    @Test
    void shouldCreateValidDefaultAlgoConfig() {
        CypherMapWrapper input = baseUserInput();
        NodeSimilarityConfigBase config = config(input);

        assertEquals(10, config.topK());
        assertEquals(0, config.topN());
        assertEquals(1, config.degreeCutoff());
        assertEquals(1E-42, config.similarityCutoff());
        assertEquals(Pools.DEFAULT_CONCURRENCY, config.concurrency());
    }

    @ParameterizedTest(name = "top or bottom: {0}")
    @ValueSource(strings = {"top", "bottom"})
    void shouldCreateValidCustomAlgoConfig(String parameter) {
        CypherMapWrapper input = baseUserInput()
            .withNumber(parameter + "K", 100)
            .withNumber(parameter + "N", 1000)
            .withNumber("degreeCutoff", 42)
            .withNumber("similarityCutoff", 0.23)
            .withNumber("concurrency", 1);

        NodeSimilarityConfigBase config = config(input);

        assertEquals(parameter.equals("top") ? 100 : -100, config.normalizedK());
        assertEquals(parameter.equals("top") ? 1000 : -1000, config.normalizedN());
        assertEquals(42, config.degreeCutoff());
        assertEquals(0.23, config.similarityCutoff());
        assertEquals(1, config.concurrency());
    }

    @ParameterizedTest(name = "missing parameter: {0}")
    @ValueSource(strings = {"writeProperty", "writeRelationshipType"})
    void shouldFailIfConfigIsMissingWriteParameters(String parameter) {
        CypherMapWrapper input = baseUserInput()
            .withoutEntry(parameter);

        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> config(input)
        );
        assertThat(illegalArgumentException.getMessage(), is(String.format("No value specified for the mandatory configuration parameter `%s`", parameter)));
    }

    private CypherMapWrapper baseUserInput() {
        return CypherMapWrapper.create(MapUtil.map("writeProperty", "foo", "writeRelationshipType", "bar"));
    }

    private NodeSimilarityConfigBase config(CypherMapWrapper input) {
        return NodeSimilarityWriteConfig.of(getUsername(), Optional.empty(), Optional.empty(), input);
    }
}
