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
package org.neo4j.graphalgo.wcc;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphalgo.AlgoBaseProcTest;
import org.neo4j.graphalgo.BaseProcTest;
import org.neo4j.graphalgo.MemoryEstimateTest;
import org.neo4j.graphalgo.RelationshipWeightConfigTest;
import org.neo4j.graphalgo.SeedConfigTest;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.catalog.GraphCreateProc;
import org.neo4j.graphalgo.compat.MapUtil;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphStoreCatalog;
import org.neo4j.graphalgo.core.utils.paged.dss.DisjointSetStruct;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class WccBaseProcTest<CONFIG extends WccBaseConfig> extends BaseProcTest implements
    AlgoBaseProcTest<CONFIG, DisjointSetStruct>,
    SeedConfigTest<CONFIG, DisjointSetStruct>,
    RelationshipWeightConfigTest<CONFIG, DisjointSetStruct>,
    MemoryEstimateTest<CONFIG, DisjointSetStruct> {

    @Override
    public GraphDatabaseAPI graphDb() {
        return db;
    }

    @BeforeEach
    void setupGraph() throws Exception {

        db = TestDatabaseCreator.createTestDatabase();

        @Language("Cypher") String cypher =
            "CREATE" +
            " (nA:Label {nodeId: 0, seedId: 42})" +
            ",(nB:Label {nodeId: 1, seedId: 42})" +
            ",(nC:Label {nodeId: 2, seedId: 42})" +
            ",(nD:Label {nodeId: 3, seedId: 42})" +
            ",(nE {nodeId: 4})" +
            ",(nF {nodeId: 5})" +
            ",(nG {nodeId: 6})" +
            ",(nH {nodeId: 7})" +
            ",(nI {nodeId: 8})" +
            ",(nJ {nodeId: 9})" +
            // {A, B, C, D}
            ",(nA)-[:TYPE]->(nB)" +
            ",(nB)-[:TYPE]->(nC)" +
            ",(nC)-[:TYPE]->(nD)" +
            ",(nD)-[:TYPE {cost:4.2}]->(nE)" + // threshold UF should split here
            // {E, F, G}
            ",(nE)-[:TYPE]->(nF)" +
            ",(nF)-[:TYPE]->(nG)" +
            // {H, I}
            ",(nH)-[:TYPE]->(nI)";

        registerProcedures(WccStreamProc.class, WccWriteProc.class, GraphCreateProc.class);
        runQuery(cypher);
    }

    @AfterEach
    void clearCommunities() {
        db.shutdown();
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Override
    public void assertResultEquals(DisjointSetStruct result1, DisjointSetStruct result2) {
        assertEquals(result1.size(), result2.size(), "DSS sizes are supposed to be equal.");
        long nodeCount = result1.size();
        for (long i = 0; i < nodeCount; i++) {
            assertEquals(result1.setIdOf(i), result2.setIdOf(i), String.format("Node %d has different set ids", i));
        }
    }

    @Test
    void testThreshold() {
        CypherMapWrapper config = createMinimalConfig(CypherMapWrapper.create(MapUtil.map(
            "threshold", 3.14,
            "relationshipWeightProperty", "threshold"
        )));

        applyOnProcedure(proc -> {
            CONFIG wccConfig = proc.newConfig(Optional.of("myGraph"), config);
            assertEquals(3.14, wccConfig.threshold());
        });
    }

    @Test
    void testIntegerThreshold() {
        CypherMapWrapper config = createMinimalConfig(CypherMapWrapper.create(MapUtil.map(
            "threshold", 3,
            "relationshipWeightProperty", "threshold"
        )));

        applyOnProcedure(proc -> {
            CONFIG wccConfig = proc.newConfig(Optional.of("myGraph"), config);
            assertEquals(3, wccConfig.threshold());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConsecutiveIds(boolean consecutiveIds) {
        CypherMapWrapper config = createMinimalConfig(CypherMapWrapper.create(MapUtil.map(
            "consecutiveIds", consecutiveIds
        )));

        applyOnProcedure(proc -> {
            CONFIG wccConfig = proc.newConfig(Optional.of("myGraph"), config);
            assertEquals(consecutiveIds, wccConfig.consecutiveIds());
        });
    }

    @Test
    void testFailSeedingAndConsecutiveIds() {
        CypherMapWrapper config = createMinimalConfig(CypherMapWrapper.create(anonymousGraphConfigMap(
            "consecutiveIds", true,
            "seedProperty", "seed"
        )));

        applyOnProcedure(proc -> {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> proc.newConfig(Optional.empty(), config)
            );

            assertTrue(exception
                .getMessage()
                .contains("Seeding and the `consecutiveIds` option cannot be used at the same time.")
            );
        });
    }

    @Test
    void testFailThresholdWithoutRelationshipWeight() {
        CypherMapWrapper config = createMinimalConfig(CypherMapWrapper.create(anonymousGraphConfigMap(
            "threshold", 3.14
        )));

        applyOnProcedure(proc -> {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> proc.newConfig(Optional.empty(), config)
            );

            assertTrue(exception
                .getMessage()
                .contains("Specifying a threshold requires `relationshipWeightProperty` to be set")
            );
        });
    }
}
