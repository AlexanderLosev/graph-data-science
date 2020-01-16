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
package org.neo4j.graphalgo;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.newapi.AlgoBaseConfig;
import org.neo4j.graphalgo.results.MemoryEstimateResult;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public interface MemoryEstimateTest<CONFIG extends AlgoBaseConfig, RESULT> extends AlgoBaseProcTest<CONFIG, RESULT> {

    @Test
    default void testEstimateExistsForEveryProcedure() {
        applyOnProcedure(proc -> {
            getProcedureMethods(proc)
                .filter(procMethod -> !getProcedureMethodName(procMethod).endsWith(".estimate"))
                .forEach(noneEstimateMethod -> {
                    String procName = getProcedureMethodName(noneEstimateMethod);
                    boolean estimateProcExists = getProcedureMethods(proc)
                        .map(this::getProcedureMethodName)
                        .anyMatch(otherProcName -> otherProcName.equals(procName + ".estimate"));
                    assertTrue(estimateProcExists, String.format("Could not find estimate method for %s", procName));
                });
        });
    }

    @Test
    default void testMemoryEstimate() {
        applyOnProcedure(proc -> {
            getProcedureMethods(proc)
                .filter(procMethod -> getProcedureMethodName(procMethod).endsWith(".estimate"))
                .forEach(estimateMethod -> {
                    Map<String, Object> config = createMinimalConfig(CypherMapWrapper.empty()).toMap();
                    try {
                        Stream<MemoryEstimateResult> result = (Stream) estimateMethod.invoke(proc, config, Collections.emptyMap());
                        result.forEach(row -> {
                            assertTrue(row.nodeCount > 0);
                            assertTrue(row.bytesMin > 0);
                            assertTrue(row.bytesMax >= row.bytesMin);
                            assertNotNull(row.mapView);
                            assertFalse(row.treeView.isEmpty());
                        });
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        fail(e);
                    }
                });
        });
    }

    @Test
    default void testMemoryEstimateOnExplicitDimensions() {
        applyOnProcedure(proc -> {
            getProcedureMethods(proc)
                .filter(procMethod -> getProcedureMethodName(procMethod).endsWith(".estimate"))
                .forEach(estimateMethod -> {
                    Map<String, Object> config = createMinimalConfig(CypherMapWrapper.empty()
                        .withNumber("nodeCount", 10000)
                        .withNumber("relationshipCount", 10000)).toMap();
                    try {
                        Stream<MemoryEstimateResult> result = (Stream) estimateMethod.invoke(proc, config, Collections.emptyMap());
                        result.forEach(row -> {
                            assertEquals(10000, row.nodeCount);
                            assertEquals(10000, row.relationshipCount);
                            assertTrue(row.bytesMin > 0);
                            assertTrue(row.bytesMax >= row.bytesMin);
                            assertNotNull(row.mapView);
                            assertFalse(row.treeView.isEmpty());
                        });
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        fail(e);
                    }
                });
        });
    }
}
