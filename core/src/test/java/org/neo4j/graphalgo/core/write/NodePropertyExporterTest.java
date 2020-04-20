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
package org.neo4j.graphalgo.core.write;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.BaseTest;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.huge.DirectIdMapping;
import org.neo4j.graphalgo.core.loading.NativeFactory;
import org.neo4j.graphalgo.core.utils.TerminationFlag;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.neo4j.graphalgo.TestGraph.Builder.fromGdl;
import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;
import static org.neo4j.graphalgo.TestSupport.assertTransactionTermination;

class NodePropertyExporterTest extends BaseTest {

    @BeforeEach
    void setup() {
        runQuery(
            "CREATE " +
            "  (n1:Node {prop1: 1, prop2: 42})" +
            ", (n2:Node {prop1: 2, prop2: 42})" +
            ", (n3:Node {prop1: 3, prop2: 42})" +
            ", (n1)-[:REL]->(n2)" +
            ", (n1)-[:REL]->(n3)" +
            ", (n2)-[:REL]->(n3)" +
            ", (n2)-[:REL]->(n3)"
        );
    }

    @Test
    void exportSingleNodeProperty() {
        Graph graph = new StoreLoaderBuilder().api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .addNodeProperty("newProp1", "prop1", 42.0, Aggregation.NONE)
            .build()
            .graph(NativeFactory.class);

        NodePropertyExporter exporter = NodePropertyExporter.builder(db, graph, TerminationFlag.RUNNING_TRUE).build();

        exporter.write("newProp1", new int[]{23, 42, 84}, Translators.INT_ARRAY_TRANSLATOR);

        Graph updatedGraph = new StoreLoaderBuilder().api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .addNodeProperty("prop1", "prop1", 42.0, Aggregation.NONE)
            .addNodeProperty("newProp1", "newProp1", 42.0, Aggregation.NONE)
            .build()
            .graph(NativeFactory.class);

        assertGraphEquals(
            fromGdl(
                "(a { prop1: 1, newProp1: 23 })" +
                "(b { prop1: 2, newProp1: 42 })" +
                "(c { prop1: 3, newProp1: 84 })" +
                "(a)-->(b)" +
                "(a)-->(c)" +
                "(b)-->(c)" +
                "(b)-->(c)"),
            updatedGraph
        );
    }

    @Test
    void exportMultipleNodeProperties() {
        Graph graph = new StoreLoaderBuilder().api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .addNodeProperty("newProp1", "prop1", 42.0, Aggregation.NONE)
            .addNodeProperty("newProp2", "prop2", 42.0, Aggregation.NONE)
            .build()
            .graph(NativeFactory.class);

        NodePropertyExporter exporter = NodePropertyExporter.builder(db, graph, TerminationFlag.RUNNING_TRUE).build();

        List<NodePropertyExporter.NodeProperty<?>> nodeProperties = Arrays.asList(
            ImmutableNodeProperty.of("newProp1", new int[]{23, 42, 84}, Translators.INT_ARRAY_TRANSLATOR),
            ImmutableNodeProperty.of("newProp2", new double[]{123D, 142D, 184D}, Translators.DOUBLE_ARRAY_TRANSLATOR)
        );

        exporter.write(nodeProperties);

        Graph updatedGraph = new StoreLoaderBuilder().api(db)
            .loadAnyLabel()
            .loadAnyRelationshipType()
            .addNodeProperty("prop1", "prop1", 42.0, Aggregation.NONE)
            .addNodeProperty("newProp1", "newProp1", 42.0, Aggregation.NONE)
            .addNodeProperty("newProp2", "newProp2", 42.0, Aggregation.NONE)
            .build()
            .graph(NativeFactory.class);

        assertGraphEquals(
            fromGdl(
                "(a { prop1: 1, newProp1: 23, newProp2: 123.0d })" +
                "(b { prop1: 2, newProp1: 42, newProp2: 142.0d })" +
                "(c { prop1: 3, newProp1: 84, newProp2: 184.0d })" +
                "(a)-->(b)" +
                "(a)-->(c)" +
                "(b)-->(c)" +
                "(b)-->(c)"),
            updatedGraph
        );
    }

    @Test
    void stopsExportingWhenTransactionHasBeenTerminated() {
        transactionTerminationTest(null);
    }

    @Test
    void stopsParallelExportingWhenTransactionHasBeenTerminated() {
        transactionTerminationTest(Pools.DEFAULT);
    }

    private void transactionTerminationTest(ExecutorService executorService) {
        TerminationFlag terminationFlag = () -> false;
        NodePropertyExporter exporter = NodePropertyExporter.builder(db, new DirectIdMapping(3), terminationFlag)
            .parallel(executorService, 4)
            .build();

        assertTransactionTermination(() -> exporter.write("foo", 42.0, new DoublePropertyTranslator()));

        runQueryWithRowConsumer(db, "MATCH (n) WHERE n.foo IS NOT NULL RETURN COUNT(*) AS count", row -> {
            Number count = row.getNumber("count");
            assertNotNull(count);
            assertEquals(0, count.intValue());
        });
    }

    static class DoublePropertyTranslator implements PropertyTranslator.OfDouble<Double> {
        @Override
        public double toDouble(final Double data, final long nodeId) {
            return data;
        }
    }
}
