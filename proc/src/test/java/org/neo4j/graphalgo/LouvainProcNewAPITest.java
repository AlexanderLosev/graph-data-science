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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.TestSupport.AllGraphNamesTest;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.impl.louvain.Louvain;
import org.neo4j.graphalgo.impl.louvain.LouvainFactory;
import org.neo4j.graphalgo.newapi.GraphCatalogProcs;
import org.neo4j.graphalgo.newapi.LouvainConfig;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.graphalgo.CommunityHelper.assertCommunities;
import static org.neo4j.graphalgo.LouvainProc.INCLUDE_INTERMEDIATE_COMMUNITIES_DEFAULT;
import static org.neo4j.graphalgo.LouvainProc.INCLUDE_INTERMEDIATE_COMMUNITIES_KEY;
import static org.neo4j.graphalgo.LouvainProc.INNER_ITERATIONS_DEFAULT;
import static org.neo4j.graphalgo.LouvainProc.INNER_ITERATIONS_KEY;
import static org.neo4j.graphalgo.LouvainProc.LEVELS_DEFAULT;
import static org.neo4j.graphalgo.LouvainProc.LEVELS_KEY;
import static org.neo4j.graphalgo.ThrowableRootCauseMatcher.rootCause;
import static org.neo4j.graphalgo.core.ProcedureConstants.DEPRECATED_RELATIONSHIP_PROPERTY_KEY;
import static org.neo4j.graphalgo.core.ProcedureConstants.GRAPH_IMPL_KEY;
import static org.neo4j.graphalgo.core.ProcedureConstants.SEED_PROPERTY_KEY;
import static org.neo4j.graphalgo.core.ProcedureConstants.TOLERANCE_DEFAULT;
import static org.neo4j.graphalgo.core.ProcedureConstants.TOLERANCE_KEY;

class LouvainProcNewAPITest extends ProcTestBase implements
    ProcTestBaseExtensions,
    WriteConfigTests<LouvainConfig>,
    BaseProcWriteTests<LouvainProcNewAPI, Louvain, LouvainConfig> {

    private static final List<List<Long>> RESULT = Arrays.asList(
        Arrays.asList(0L, 1L, 2L, 3L, 4L, 5L, 14L),
        Arrays.asList(6L, 7L, 8L),
        Arrays.asList(9L, 10L, 11L, 12L, 13L)
    );

    @BeforeEach
    void setupGraph() throws KernelException {

        db = TestDatabaseCreator.createTestDatabase();

        @Language("Cypher") String cypher =
            "CREATE" +
            "  (a:Node {seed: 1})" +        // 0
            ", (b:Node {seed: 1})" +        // 1
            ", (c:Node {seed: 1})" +        // 2
            ", (d:Node {seed: 1})" +        // 3
            ", (e:Node {seed: 1})" +        // 4
            ", (f:Node {seed: 1})" +        // 5
            ", (g:Node {seed: 2})" +        // 6
            ", (h:Node {seed: 2})" +        // 7
            ", (i:Node {seed: 2})" +        // 8
            ", (j:Node {seed: 42})" +       // 9
            ", (k:Node {seed: 42})" +       // 10
            ", (l:Node {seed: 42})" +       // 11
            ", (m:Node {seed: 42})" +       // 12
            ", (n:Node {seed: 42})" +       // 13
            ", (x:Node {seed: 1})" +        // 14

            ", (a)-[:TYPE {weight: 1.0}]->(b)" +
            ", (a)-[:TYPE {weight: 1.0}]->(d)" +
            ", (a)-[:TYPE {weight: 1.0}]->(f)" +
            ", (b)-[:TYPE {weight: 1.0}]->(d)" +
            ", (b)-[:TYPE {weight: 1.0}]->(x)" +
            ", (b)-[:TYPE {weight: 1.0}]->(g)" +
            ", (b)-[:TYPE {weight: 1.0}]->(e)" +
            ", (c)-[:TYPE {weight: 1.0}]->(x)" +
            ", (c)-[:TYPE {weight: 1.0}]->(f)" +
            ", (d)-[:TYPE {weight: 1.0}]->(k)" +
            ", (e)-[:TYPE {weight: 1.0}]->(x)" +
            ", (e)-[:TYPE {weight: 0.01}]->(f)" +
            ", (e)-[:TYPE {weight: 1.0}]->(h)" +
            ", (f)-[:TYPE {weight: 1.0}]->(g)" +
            ", (g)-[:TYPE {weight: 1.0}]->(h)" +
            ", (h)-[:TYPE {weight: 1.0}]->(i)" +
            ", (h)-[:TYPE {weight: 1.0}]->(j)" +
            ", (i)-[:TYPE {weight: 1.0}]->(k)" +
            ", (j)-[:TYPE {weight: 1.0}]->(k)" +
            ", (j)-[:TYPE {weight: 1.0}]->(m)" +
            ", (j)-[:TYPE {weight: 1.0}]->(n)" +
            ", (k)-[:TYPE {weight: 1.0}]->(m)" +
            ", (k)-[:TYPE {weight: 1.0}]->(l)" +
            ", (l)-[:TYPE {weight: 1.0}]->(n)" +
            ", (m)-[:TYPE {weight: 1.0}]->(n)";

        registerProcedures(LouvainProcNewAPI.class, GraphLoadProc.class, GraphCatalogProcs.class);
        runQuery(cypher);
        runQuery("CALL algo.beta.graph.create(" +
                 "    'myGraph'," +
                 "    {" +
                 "      Node: {" +
                 "        label: 'Node'," +
                 "        properties: ['seed']" +
                 "      }" +
                 "    }," +
                 "    {" +
                 "      TYPE: {" +
                 "        type: 'TYPE'," +
                 "        projection: 'UNDIRECTED'" +
                 "      }" +
                 "    }" +
                 ")");
    }

    @AfterEach
    void clearCommunities() {
        db.shutdown();
        GraphCatalog.removeAllLoadedGraphs();
    }

    static Stream<Arguments> graphVariations() {
        return Stream.of(
            arguments("'myGraph', {", "explicit graph"),
            arguments(
                "{" +
                "  nodeProjection: ['Node']," +
                "  relationshipProjection: {" +
                "    TYPE: {" +
                "      type: 'TYPE'," +
                "      projection: 'UNDIRECTED'" +
                "    }" +
                "  }," +
                "  nodeProperties: ['seed'],",
                "implicit graph"
            )
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("graphVariations")
    void testWrite(String graphSnippet, String testCaseName) {
        String writeProperty = "myFancyCommunity";
        String query = "CALL gds.algo.louvain.write(" +
                       graphSnippet +
                       "    writeProperty: $writeProp" +
                       "}) YIELD communityCount, modularity, modularities, ranLevels, includeIntermediateCommunities, createMillis, computeMillis, writeMillis, postProcessingMillis, communityDistribution";

        runQuery(query, MapUtil.map("writeProp", writeProperty),
            row -> {
                long communityCount = row.getNumber("communityCount").longValue();
                double modularity = row.getNumber("modularity").doubleValue();
                List<Double> modularities = (List<Double>) row.get("modularities");
                long levels = row.getNumber("ranLevels").longValue();
                boolean includeIntermediate = row.getBoolean("includeIntermediateCommunities");
                long createMillis = row.getNumber("createMillis").longValue();
                long computeMillis = row.getNumber("computeMillis").longValue();
                long writeMillis = row.getNumber("writeMillis").longValue();

                assertEquals(3, communityCount, "wrong community count");
                assertEquals(2, modularities.size(), "invalud modularities");
                assertEquals(2, levels, "invalid level count");
                assertFalse(includeIntermediate, "invalid level count");
                assertTrue(modularity > 0, "wrong modularity value");
                assertTrue(createMillis >= 0, "invalid loadTime");
                assertTrue(writeMillis >= 0, "invalid writeTime");
                assertTrue(computeMillis >= 0, "invalid computeTime");
            }
        );
        assertWriteResult(RESULT, writeProperty);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("graphVariations")
    void testWriteIntermediateCommunities(String graphSnippet, String testCaseName) {
        String writeProperty = "myFancyCommunity";
        String query = "CALL gds.algo.louvain.write(" +
                       graphSnippet +
                       "    writeProperty: $writeProp," +
                       "    includeIntermediateCommunities: true" +
                       "}) YIELD includeIntermediateCommunities";

        runQuery(query, MapUtil.map("writeProp", writeProperty),
            row -> {
                assertTrue(row.getBoolean(INCLUDE_INTERMEDIATE_COMMUNITIES_KEY));
            }
        );

        runQuery(String.format("MATCH (n) RETURN n.%s as %s", writeProperty, writeProperty), row -> {
            Object maybeList = row.get(writeProperty);
            assertTrue(maybeList instanceof long[]);
            long[] communities = (long[]) maybeList;
            assertEquals(2, communities.length);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "writeProperty: null,",
        ""
    })
    void testWriteRequiresWritePropertyToBeSet(String writePropertyParameter) {
        String query = "CALL gds.algo.louvain.write({" +
                       writePropertyParameter +
                       "    nodeProjection: ['Node']," +
                       "    relationshipProjection: {" +
                       "      TYPE: {" +
                       "        type: 'TYPE'," +
                       "        projection: 'UNDIRECTED'" +
                       "      }" +
                       "    }" +
                       "}) YIELD includeIntermediateCommunities";

        QueryExecutionException exception = assertThrows(
            QueryExecutionException.class,
            () -> runQuery(query)
        );

        assertThat(exception, rootCause(
            IllegalArgumentException.class,
            "writeProperty must be set in write mode"
        ));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("graphVariations")
    void testStream(String graphSnippet, String testCaseName) {
        String query = "CALL gds.algo.louvain.stream(" +
                       graphSnippet.replaceAll(",$", "") +
                       "}) YIELD nodeId, communityId, communityIds";

        List<Long> actualCommunities = new ArrayList<>();
        runQuery(query, row -> {
            int id = row.getNumber("nodeId").intValue();
            long community = row.getNumber("communityId").longValue();
            assertNull(row.get("communityIds"));
            actualCommunities.add(id, community);
        });
        assertCommunities(actualCommunities, RESULT);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("graphVariations")
    void testStreamCommunities(String graphSnippet, String testCaseName) {
        String query = "CALL gds.algo.louvain.stream(" +
                       graphSnippet +
                       "    includeIntermediateCommunities: true" +
                       "}) YIELD nodeId, communityId, communityIds";

        runQuery(query, row -> {
            Object maybeList = row.get("communityIds");
            assertTrue(maybeList instanceof List);
            List<Long> communities = (List<Long>) maybeList;
            assertEquals(2, communities.size());
            assertEquals(communities.get(1), row.getNumber("communityId").longValue());
        });
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("graphVariations")
    void testWriteWithSeeding(String graphSnippet, String testCaseName) {
        String writeProperty = "myFancyWriteProperty";
        String query = "CALL gds.algo.louvain.write(" +
                       graphSnippet +
                       "    writeProperty: '" + writeProperty + "'," +
                       "    seedProperty: 'seed'" +
                       "}) YIELD communityCount, ranLevels";

        runQuery(
            query,
            row -> {
                assertEquals(3, row.getNumber("communityCount").longValue(), "wrong community count");
                assertEquals(1, row.getNumber("ranLevels").longValue(), "wrong number of levels");
            }
        );
        assertWriteResult(RESULT, writeProperty);
    }

    @Test
    void testCreateConfigWithDefaults() {
        LouvainConfig louvainConfig = LouvainConfig.of(
            "",
            Optional.empty(),
            Optional.empty(),
            CypherMapWrapper.empty()
        );
        assertEquals(null, louvainConfig.writeProperty());
        assertEquals(false, louvainConfig.includeIntermediateCommunities());
        assertEquals(10, louvainConfig.maxLevels());
        assertEquals(10, louvainConfig.maxIterations());
        assertEquals(0.0001D, louvainConfig.tolerance());
        assertEquals(null, louvainConfig.seedProperty());
        assertEquals(null, louvainConfig.weightProperty());
    }

    @Test
    void testCreateConfig() {
        LouvainConfig louvainConfig = LouvainConfig.of(
            "",
            Optional.empty(),
            Optional.empty(),
            CypherMapWrapper.create(MapUtil.map(
                "writeProperty", "writeProperty"
            ))
        );
        assertEquals("writeProperty", louvainConfig.writeProperty());
        assertEquals(false, louvainConfig.includeIntermediateCommunities());
        assertEquals(10, louvainConfig.maxLevels());
        assertEquals(10, louvainConfig.maxIterations());
        assertEquals(0.0001D, louvainConfig.tolerance());
        assertEquals(null, louvainConfig.seedProperty());
        assertEquals(null, louvainConfig.weightProperty());
    }

    @Override
    public LouvainConfig createConfig(CypherMapWrapper mapWrapper) {
        return LouvainConfig.of(
            "",
            Optional.empty(),
            Optional.empty(),
            mapWrapper
        );
    }

    @Override
    public GraphDatabaseAPI graphDb() {
        return db;
    }

    @Override
    public LouvainProcNewAPI createProcedure() {
        return new LouvainProcNewAPI();
    }

    @AllGraphNamesTest
    void testDefaults(String graphImpl) {
        getAlgorithmFactory(
            LouvainProc.class,
            db,
            "", "",
            MapUtil.map(GRAPH_IMPL_KEY, graphImpl),
            (LouvainFactory factory) -> {
                assertEquals(INCLUDE_INTERMEDIATE_COMMUNITIES_DEFAULT, factory.config.includeIntermediateCommunities);
                assertEquals(LEVELS_DEFAULT, factory.config.maxLevel);
                assertEquals(INNER_ITERATIONS_DEFAULT, factory.config.maxInnerIterations);
                assertEquals(TOLERANCE_DEFAULT, factory.config.tolerance);
                assertFalse(factory.config.maybeSeedPropertyKey.isPresent());
            }
        );
    }

    @AllGraphNamesTest
    void testOverwritingDefaults(String graphImpl) {
        Map<String, Object> config = MapUtil.map(
            GRAPH_IMPL_KEY, graphImpl,
            INCLUDE_INTERMEDIATE_COMMUNITIES_KEY, true,
            LEVELS_KEY, 42,
            INNER_ITERATIONS_KEY, 42,
            TOLERANCE_KEY, 0.42,
            SEED_PROPERTY_KEY, "foobar"
        );

        getAlgorithmFactory(
            LouvainProc.class,
            db,
            "", "",
            config,
            (LouvainFactory factory) -> {
                assertTrue(factory.config.includeIntermediateCommunities);
                assertEquals(42, factory.config.maxLevel);
                assertEquals(42, factory.config.maxInnerIterations);
                assertEquals(0.42, factory.config.tolerance);
                assertEquals("foobar", factory.config.maybeSeedPropertyKey.orElse("not_set"));
            }
        );
    }

    @AllGraphNamesTest
    void testGraphLoaderDefaults(String graphImpl) {
        getGraphSetup(
            LouvainProc.class,
            db,
            "", "",
            MapUtil.map(
                GRAPH_IMPL_KEY, graphImpl
            ),
            setup -> {
                assertTrue(setup.loadAnyLabel());
                assertEquals(Direction.BOTH, setup.direction());
                assertFalse(setup.nodePropertyMappings().head().isPresent());
                assertFalse(setup.relationshipPropertyMappings().head().isPresent());
            }
        );
    }

    @AllGraphNamesTest
    void testGraphLoaderWithSeeding(String graphImpl) {
        getGraphSetup(
            LouvainProc.class,
            db,
            "", "",
            MapUtil.map(
                GRAPH_IMPL_KEY, graphImpl,
                SEED_PROPERTY_KEY, "foobar"
            ),
            setup -> {
                PropertyMapping propertyMapping = setup.nodePropertyMappings().head().get();
                assertEquals("foobar", propertyMapping.neoPropertyKey());
                assertEquals("foobar", propertyMapping.propertyKey());
            }
        );
    }

    @AllGraphNamesTest
    void testGraphLoaderWithWeight(String graphImpl) {
        getGraphSetup(
            LouvainProc.class,
            db,
            "", "",
            MapUtil.map(
                GRAPH_IMPL_KEY, graphImpl,
                DEPRECATED_RELATIONSHIP_PROPERTY_KEY, "foobar"
            ),
            setup -> {
                PropertyMapping propertyMapping = setup.relationshipPropertyMappings().head().get();
                assertEquals("foobar", propertyMapping.neoPropertyKey());
                assertEquals("foobar", propertyMapping.propertyKey());
            }
        );
    }

    private void assertWriteResult(List<List<Long>> expectedCommunities, String writeProperty) {
        List<Long> actualCommunities = new ArrayList<>();
        runQuery(String.format("MATCH (n) RETURN id(n) as id, n.%s as community", writeProperty), (row) -> {
            long community = row.getNumber("community").longValue();
            int id = row.getNumber("id").intValue();
            actualCommunities.add(id, community);
        });

        assertCommunities(actualCommunities, expectedCommunities);
    }
}
