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
package org.neo4j.graphalgo;

import org.junit.jupiter.api.BeforeEach;
import org.neo4j.graphalgo.compat.GraphDbApi;
import org.neo4j.graphalgo.core.utils.BatchingProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.NullLog;

import java.util.function.Consumer;

public class AlgoTestBase {

    public GraphDbApi db;

    public ProgressLogger progressLogger;

    @BeforeEach
    void resetProgressLogger() {
        this.progressLogger = new BatchingProgressLogger(NullLog.getInstance(), 0, "Test", 1);
    }

    protected void runQuery(String query) {
        QueryRunner.runQuery(db, query);
    }

    protected void runQuery(String query, Consumer<Result.ResultRow> check) {
        QueryRunner.runQueryWithRowConsumer(db, query, check);
    }

    protected void runQuery(GraphDatabaseService passedInDb, String query) {
        org.neo4j.graphalgo.compat.GraphDatabaseApiProxy.runInTransaction(passedInDb, tx -> QueryRunner.runQuery(passedInDb, query));
    }
}
