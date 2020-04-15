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
package org.neo4j.graphalgo.core.utils.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphStore;
import org.neo4j.graphalgo.core.loading.NativeFactory;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import java.io.File;

import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;

class GraphStoreExportTest extends AlgoTestBase {

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a { prop1: 0, prop2: 42 })" +
        ", (b { prop1: 1, prop2: 43 })" +
        ", (c { prop1: 2, prop2: 44 })" +
        ", (d { prop1: 3 })" +
        ", (a)-[:REL]->(a)" +
        ", (a)-[:REL]->(b)" +
        ", (b)-[:REL]->(a)" +
        ", (b)-[:REL]->(c)" +
        ", (c)-[:REL]->(d)" +
        ", (d)-[:REL]->(a)";

    @TempDir
    File tempDir;

    @BeforeEach
    void setup() {
        runQuery(db, DB_CYPHER);
    }

    @Test
    void exportTopology() {
        StoreLoaderBuilder loaderBuilder = new StoreLoaderBuilder()
            .loadAnyLabel()
            .loadAnyRelationshipType();

        GraphStore inputGraphStore = loaderBuilder.api(db).build().graphStore(NativeFactory.class);

        GraphStoreExportConfig config = GraphStoreExportConfig.of(
            "test-user",
            CypherMapWrapper.empty()
                .withString("storeDir", tempDir.getAbsolutePath())
                .withString("dbName", "test-db")
        );

        GraphStoreExport graphStoreExport = new GraphStoreExport(inputGraphStore, config);
        graphStoreExport.runFromTests();

        DatabaseManagementService testDbms = new TestDatabaseManagementServiceBuilder(tempDir)
            .setConfig(GraphDatabaseSettings.fail_on_missing_files, false)
            .build();
        GraphStore outputGraphStore = loaderBuilder
            .api((GraphDatabaseAPI) testDbms.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME))
            .build()
            .graphStore(NativeFactory.class);

        assertGraphEquals(inputGraphStore.getUnion(), outputGraphStore.getUnion());

        testDbms.shutdown();
    }

    @Test
    void exportTopologyAndNodeProperties() {
        StoreLoaderBuilder loaderBuilder = new StoreLoaderBuilder()
            .loadAnyLabel()
            .addNodeProperty(PropertyMapping.of("prop1", 0))
            .addNodeProperty(PropertyMapping.of("prop2", 42))
            .loadAnyRelationshipType();

        GraphStore inputGraphStore = loaderBuilder.api(db).build().graphStore(NativeFactory.class);

        GraphStoreExportConfig config = GraphStoreExportConfig.of(
            "test-user",
            CypherMapWrapper.empty()
                .withString("storeDir", tempDir.getAbsolutePath())
                .withString("dbName", "test-db")
        );

        GraphStoreExport graphStoreExport = new GraphStoreExport(inputGraphStore, config);
        graphStoreExport.runFromTests();

        DatabaseManagementService testDbms = new TestDatabaseManagementServiceBuilder(tempDir)
            .setConfig(GraphDatabaseSettings.fail_on_missing_files, false)
            .build();
        GraphStore outputGraphStore = loaderBuilder
            .api((GraphDatabaseAPI) testDbms.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME))
            .build()
            .graphStore(NativeFactory.class);

        assertGraphEquals(inputGraphStore.getUnion(), outputGraphStore.getUnion());

        testDbms.shutdown();
    }
}
