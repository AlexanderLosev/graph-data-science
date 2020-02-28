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

import org.neo4j.graphalgo.core.concurrency.ConcurrencyControllerExtension;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.LogProvider;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;
import java.util.UUID;
import java.util.function.Consumer;

import static org.neo4j.graphalgo.config.ConcurrencyValidation.CORE_LIMITATION_SETTING;

public final class TestDatabaseCreator {

    private TestDatabaseCreator() {}

    public static GraphDatabaseAPI createTestDatabase() {
        return (GraphDatabaseAPI) dbBuilder().setConfig(GraphDatabaseSettings.procedure_unrestricted, "gds.*")
            .newGraphDatabase();
    }

    public static GraphDatabaseAPI createTestDatabaseWithCustomLoadCsvRoot(String value) {
        return (GraphDatabaseAPI) new TestGraphDatabaseFactory()
            .newImpermanentDatabaseBuilder(new File(UUID.randomUUID().toString()))
            .setConfig(GraphDatabaseSettings.load_csv_file_url_root, value)
            .setConfig(GraphDatabaseSettings.procedure_unrestricted, "gds.*")
            .newGraphDatabase();
    }

    public static GraphDatabaseAPI createTestDatabase(Consumer<GraphDatabaseBuilder> configuration) {
        GraphDatabaseBuilder builder = dbBuilder();
        configuration.accept(builder);
        return (GraphDatabaseAPI) builder.newGraphDatabase();
    }

    public static GraphDatabaseAPI createUnlimitedConcurrencyTestDatabase() {
        return createTestDatabase(builder -> builder.setConfig(CORE_LIMITATION_SETTING, "true"));
    }

    public static GraphDatabaseAPI createTestDatabase(LogProvider logProvider) {
        return (GraphDatabaseAPI) new TestGraphDatabaseFactory(logProvider)
            .newImpermanentDatabaseBuilder(new File(UUID.randomUUID().toString()))
            .newGraphDatabase();
    }

    public static GraphDatabaseAPI createTestDatabase(File storeDir) {
        return (GraphDatabaseAPI) new TestGraphDatabaseFactory().newEmbeddedDatabase(storeDir);
    }

    private static GraphDatabaseBuilder dbBuilder() {
        return dbBuilder(new File(UUID.randomUUID().toString()));
    }

    private static GraphDatabaseBuilder dbBuilder(File storeDir) {
        return new TestGraphDatabaseFactory()
            .addKernelExtension(new ConcurrencyControllerExtension())
            .newImpermanentDatabaseBuilder(storeDir);
    }

}
