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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.core.loading.HugeGraphFactory;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.File;

import static org.neo4j.graphalgo.QueryRunner.runQuery;
import static org.neo4j.graphalgo.TestSupport.assertGraphEquals;

class NeoExportTest {

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a { prop1: 23.0 })" +
        ", (b { prop1: 42.0 })" +
        ", (c { prop1: 84.0 })" +
        ", (a)-[:REL]->(b)" +
        ", (b)-[:REL]->(a)" +
        ", (b)-[:REL]->(c)";

    @TempDir
    File tempDir;

    private GraphDatabaseAPI db;

    @BeforeEach
    void setup() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(db, DB_CYPHER);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
        GraphCatalog.removeAllLoadedGraphs();
    }

    @Test
    void exportTopology() {
        StoreLoaderBuilder loaderBuilder = new StoreLoaderBuilder()
            .loadAnyLabel()
            .loadAnyRelationshipType();

        Graph inputGraph = loaderBuilder.api(db).build().graph(HugeGraphFactory.class);

        NeoExportConfig config = NeoExportConfig.of(CypherMapWrapper.empty()
            .withString("storeDir", tempDir.getAbsolutePath())
            .withString("dbName", "test-db")
        );

        NeoExport neoExport = new NeoExport(inputGraph, config);
        neoExport.run(true);

        GraphDatabaseAPI exportDb = TestDatabaseCreator.createTestDatabase(tempDir);
        Graph outputGraph = loaderBuilder.api(exportDb).build().graph(HugeGraphFactory.class);

        assertGraphEquals(inputGraph, outputGraph);

        exportDb.shutdown();
    }

    @Disabled
    void exportTopologyAndNodeProperties() {
        StoreLoaderBuilder loaderBuilder = new StoreLoaderBuilder()
            .loadAnyLabel()
            .addNodeProperty(PropertyMapping.of("prop1", 23.0))
            .loadAnyRelationshipType();

        Graph inputGraph = loaderBuilder.api(db).build().graph(HugeGraphFactory.class);

        NeoExportConfig config = NeoExportConfig.of(CypherMapWrapper.empty()
            .withString("storeDir", tempDir.getAbsolutePath())
            .withString("dbName", "test-db")
        );

        NeoExport neoExport = new NeoExport(inputGraph, config);
        neoExport.run(true);

        GraphDatabaseAPI exportDb = TestDatabaseCreator.createTestDatabase(tempDir);
        Graph outputGraph = loaderBuilder.api(exportDb).build().graph(HugeGraphFactory.class);

        assertGraphEquals(inputGraph, outputGraph);

        exportDb.shutdown();
    }
}