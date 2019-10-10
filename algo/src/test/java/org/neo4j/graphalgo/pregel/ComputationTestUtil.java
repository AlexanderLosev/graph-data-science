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

package org.neo4j.graphalgo.pregel;

import org.junit.Assert;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.paged.HugeDoubleArray;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;

import java.util.HashMap;
import java.util.Map;

public final class ComputationTestUtil {

    static void assertLongValues(
            final GraphDatabaseService db,
            Label nodeLabel,
            String idProperty,
            final Graph graph,
            HugeDoubleArray computedValues,
            final long... values) {
        Map<Long, Long> expectedValues = new HashMap<>();
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < values.length; i++) {
                expectedValues.put(db.findNode(nodeLabel, idProperty, i).getId(), values[i]);
            }
            tx.success();
        }
        expectedValues.forEach((idProp, expectedValue) -> {
            long neoId = graph.toOriginalNodeId(idProp);
            long computedValue = (long) computedValues.get(neoId);
            Assert.assertEquals(
                    String.format("Node.id = %d should have value %d", idProp, expectedValue),
                    (long) expectedValue,
                    computedValue);
        });
    }

    static void assertDoubleValues(
            final GraphDatabaseService db,
            Label nodeLabel,
            String idProperty,
            final Graph graph,
            HugeDoubleArray computedValues,
            double delta,
            final double... values) {
        Map<Long, Double> expectedValues = new HashMap<>();
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < values.length; i++) {
                expectedValues.put(db.findNode(nodeLabel, idProperty, i).getId(), values[i]);
            }
            tx.success();
        }

        expectedValues.forEach((idProp, expectedValue) -> {
            long neoId = graph.toOriginalNodeId(idProp);
            double computedValue = computedValues.get(neoId);
            Assert.assertEquals(
                    String.format("Node.id = %d should have value %f", idProp, expectedValue),
                    expectedValue,
                    computedValue,
                    delta);
        });
    }
}
