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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphalgo.compat.MapUtil;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.newapi.BaseAlgoConfig;
import org.neo4j.graphalgo.newapi.WeightConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public interface WeightConfigTest <CONFIG extends WeightConfig & BaseAlgoConfig, RESULT> extends BaseAlgoProcTests<CONFIG, RESULT> {
    @Test
    default void testDefaultWeightPropertyIsNull() {
        CypherMapWrapper mapWrapper = CypherMapWrapper.empty();
        CONFIG config = createConfig(createMinimallyValidConfig(mapWrapper));
        assertNull(config.weightProperty());
    }

    @Test
    default void testWeightPropertyFromConfig() {
        CypherMapWrapper mapWrapper = CypherMapWrapper.create(MapUtil.map("weightProperty", "weight"));
        CONFIG config = createConfig(createMinimallyValidConfig(mapWrapper));
        assertEquals("weight", config.weightProperty());
    }

    @ParameterizedTest
    @MethodSource("emptyStringPropertyValues")
    default void testEmptyWeightPropertyValues(String weightPropertyParameter) {
        CypherMapWrapper mapWrapper = CypherMapWrapper.create(MapUtil.map("weightProperty", weightPropertyParameter));
        CONFIG config = createConfig(createMinimallyValidConfig(mapWrapper));
        assertNull(config.weightProperty());
    }

    @Test
    default void testValidateWeightToNodeProperties() {
        List<String> nodeProperties = Arrays.asList("a", "b", "c");
        Map<String, Object> config = MapUtil.map(
            "weightProperty", "foo",
            "writeProperty", "bar",
            "nodeProjection", MapUtil.map(
                "A", MapUtil.map(
                    "properties", nodeProperties
                )
            )
        );
        applyOnProcedure(proc -> {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> proc.compute(config, Collections.emptyMap())
            );
            assertThat(e.getMessage(), containsString("foo"));
            assertThat(e.getMessage(), containsString("[a, b, c]"));
        });
    }
}
