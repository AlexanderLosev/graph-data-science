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
package org.neo4j.graphalgo.labelpropagation;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.GraphMutationTest;
import org.neo4j.graphalgo.compat.MapUtil;
import org.neo4j.graphalgo.core.CypherMapWrapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelPropagationMutateProcTest extends LabelPropagationProcTest<LabelPropagationMutateConfig> implements GraphMutationTest<LabelPropagationMutateConfig, LabelPropagation> {

    private static final String WRITE_PROPERTY = "communityId";

    @Override
    public String expectedMutatedGraph() {
        return
            "  (a { communityId: 2 }) " +
            ", (b { communityId: 7 }) " +
            ", (a)-->({ communityId: 2 }) " +
            ", (a)-->({ communityId: 3 }) " +
            ", (a)-->({ communityId: 4 }) " +
            ", (a)-->({ communityId: 5 }) " +
            ", (a)-->({ communityId: 6 }) " +
            ", (b)-->({ communityId: 7 }) " +
            ", (b)-->({ communityId: 8 }) " +
            ", (b)-->({ communityId: 9 }) " +
            ", (b)-->({ communityId: 10 }) " +
            ", (b)-->({ communityId: 11 })";
    }

    @Override
    public String failOnExistingTokenMessage() {
        return String.format(
            "Node property `%s` already exists in the in-memory graph.",
            WRITE_PROPERTY
        );
    }

    @Override
    public Class<? extends AlgoBaseProc<?, LabelPropagation, LabelPropagationMutateConfig>> getProcedureClazz() {
        return LabelPropagationMutateProc.class;
    }

    @Override
    public LabelPropagationMutateConfig createConfig(CypherMapWrapper mapWrapper) {
        return LabelPropagationMutateConfig.of(getUsername(), Optional.empty(), Optional.empty(), mapWrapper);
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        if (!mapWrapper.containsKey("writeProperty")) {
            return mapWrapper.withString("writeProperty", WRITE_PROPERTY);
        }
        return mapWrapper;
    }

    @Test
    void testMutateYields() {
        String query = GdsCypher
            .call()
            .withAnyLabel()
            .withAnyRelationshipType()
            .algo("labelPropagation")
            .mutateMode()
            .addParameter("writeProperty", WRITE_PROPERTY)
            .yields(
                "createMillis",
                "computeMillis",
                "mutateMillis",
                "postProcessingMillis",
                "communityCount",
                "didConverge",
                "communityDistribution",
                "configuration"
            );

        runQueryWithRowConsumer(
            query,
            row -> {
                assertNotEquals(-1L, row.getNumber("createMillis"));
                assertNotEquals(-1L, row.getNumber("computeMillis"));
                assertNotEquals(-1L, row.getNumber("mutateMillis"));
                assertNotEquals(-1L, row.getNumber("postProcessingMillis"));

                assertEquals(10L, row.getNumber("communityCount"));
                assertTrue(row.getBoolean("didConverge"));

                assertEquals(MapUtil.map(
                    "p99", 2L,
                    "min", 1L,
                    "max", 2L,
                    "mean", 1.2D,
                    "p90", 2L,
                    "p50", 1L,
                    "p999", 2L,
                    "p95", 2L,
                    "p75", 1L
                ), row.get("communityDistribution"));
            }
        );
    }

}
