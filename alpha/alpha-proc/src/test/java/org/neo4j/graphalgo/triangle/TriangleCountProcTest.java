/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.triangle;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.utils.paged.PagedAtomicIntegerArray;
import org.neo4j.graphalgo.impl.triangle.IntersectingTriangleCount;
import org.neo4j.graphalgo.impl.triangle.TriangleCountConfig;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TriangleCountProcTest extends TriangleBaseProcTest<IntersectingTriangleCount, PagedAtomicIntegerArray, TriangleCountConfig> {

    @Override
    TriangleBaseProc<IntersectingTriangleCount, PagedAtomicIntegerArray, TriangleCountConfig> newInstance() {
        return new TriangleCountProc();
    }

    @Override
    TriangleCountConfig newConfig() {
        return TriangleCountConfig.of(
            getUsername(),
            Optional.empty(),
            Optional.empty(),
            CypherMapWrapper.empty()
        );
    }

    @Test
    void testStreaming() {
        TriangleCountConsumer mock = mock(TriangleCountConsumer.class);

        String query = GdsCypher.call()
            .loadEverything(Orientation.UNDIRECTED)
            .algo("gds", "alpha", "triangleCount")
            .streamMode()
            .yields();

        runQueryWithRowConsumer(query, row -> {
            long nodeId = row.getNumber("nodeId").longValue();
            long triangles = row.getNumber("triangles").longValue();
            double coefficient = row.getNumber("coefficient").doubleValue();
            mock.consume(nodeId, triangles, coefficient);
        });
        verify(mock, times(5)).consume(anyLong(), eq(1L), AdditionalMatchers.eq(1.0, 0.1));
        verify(mock, times(4)).consume(anyLong(), eq(1L), AdditionalMatchers.eq(0.333, 0.1));
    }

    @Test
    void testWriting() {
        String query = GdsCypher.call()
            .loadEverything(Orientation.UNDIRECTED)
            .algo("gds", "alpha", "triangleCount")
            .writeMode()
            .yields();

        runQueryWithRowConsumer(query, row -> {
            long createMillis = row.getNumber("createMillis").longValue();
            long computeMillis = row.getNumber("computeMillis").longValue();
            long writeMillis = row.getNumber("writeMillis").longValue();
            long nodeCount = row.getNumber("nodeCount").longValue();
            long triangleCount = row.getNumber("triangleCount").longValue();
            assertNotEquals(-1, createMillis);
            assertNotEquals(-1, computeMillis);
            assertNotEquals(-1, writeMillis);
            assertEquals(3, triangleCount);
            assertEquals(9, nodeCount);
        });

        final String request = "MATCH (n) WHERE exists(n.triangles) RETURN n.triangles as t";
        runQueryWithRowConsumer(request, row -> {
            final int triangles = row.getNumber("t").intValue();
            assertEquals(1, triangles);
        });
    }

    @Test
    void testWritingWithClusterCoefficientProperty() {
        String query = GdsCypher.call()
            .loadEverything(Orientation.UNDIRECTED)
            .algo("gds", "alpha", "triangleCount")
            .writeMode()
            .addParameter("clusteringCoefficientProperty", "c")
            .yields();

        runQueryWithRowConsumer(query, row -> {
            long createMillis = row.getNumber("createMillis").longValue();
            long computeMillis = row.getNumber("computeMillis").longValue();
            long writeMillis = row.getNumber("writeMillis").longValue();
            long nodeCount = row.getNumber("nodeCount").longValue();
            long triangles = row.getNumber("triangleCount").longValue();
            double coefficient = row.getNumber("averageClusteringCoefficient").doubleValue();
            long p100 = row.getNumber("p100").longValue();

            assertNotEquals(-1, createMillis);
            assertNotEquals(-1, computeMillis);
            assertNotEquals(-1, writeMillis);
            assertEquals(9, nodeCount);
            assertEquals(3, triangles);
            assertEquals(0.704, coefficient, 0.1);
            assertEquals(9, nodeCount);
            assertEquals(9, p100);
        });
    }

    interface TriangleCountConsumer {
        void consume(long nodeId, long triangles, double value);
    }
}
