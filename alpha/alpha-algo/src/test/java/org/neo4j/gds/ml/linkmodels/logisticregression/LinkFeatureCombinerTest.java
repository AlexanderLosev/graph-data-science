/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LinkFeatureCombinerTest {

    @ParameterizedTest
    @MethodSource("l2InputArrays")
    void shouldCombineArraysUsingL2(double[] sourceArray, double[] targetArray, double[] expectedCombined) {
        var combined = LinkFeatureCombiner.L2.combine(sourceArray, targetArray);
        assertThat(combined).containsExactly(expectedCombined);
    }

    @ParameterizedTest
    @MethodSource("hadamardInputArrays")
    void shouldCombineArraysUsingHADAMARD(double[] sourceArray, double[] targetArray, double[] expectedCombined) {
        var combined = LinkFeatureCombiner.HADAMARD.combine(sourceArray, targetArray);
        assertThat(combined).containsExactly(expectedCombined);
    }

    static Stream<Arguments> l2InputArrays() {
        return Stream.of(
            Arguments.of(
                new double[]{5, 3.2, -4.2},                                 // sourceArray
                new double[]{-4.3, 7.2, 6.2},                               // targetArray
                new double[]{(5 + 4.3) * (5 + 4.3), 4 * 4, (10.4) * (10.4)} // expectation
            ),
            Arguments.of(
                new double[]{5.0, 0.0, -4.2},
                new double[]{5.0, 1.0, -4.2},
                new double[]{0.0, 1.0, 0.0}
            )
        );
    }
    static Stream<Arguments> hadamardInputArrays() {
        return Stream.of(
            Arguments.of(
                new double[]{5, 3.2, -4.2},                                 // sourceArray
                new double[]{-4.3, 7.2, 6.2},                               // targetArray
                new double[]{-5 * 4.3, 3.2 * 7.2, -4.2 * 6.2} // expectation
            ),
            Arguments.of(
                new double[]{5.0, 0.0, -4.2},
                new double[]{5.0, 1.0, -4.2},
                new double[]{25, 0.0, 4.2 * 4.2}
            )
        );
    }
}
