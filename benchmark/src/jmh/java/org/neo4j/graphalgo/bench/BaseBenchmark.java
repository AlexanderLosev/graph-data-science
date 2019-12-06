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

package org.neo4j.graphalgo.bench;

import org.neo4j.graphalgo.QueryRunner;
import org.neo4j.graphdb.Result;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.neo4j.graphdb.DependencyResolver.SelectionStrategy.ONLY;

public class BaseBenchmark {

    GraphDatabaseAPI db;

    void registerProcedures(Class<?>... procedureClasses) throws KernelException {
        Procedures procedures = db.getDependencyResolver().resolveDependency(Procedures.class, ONLY);
        for (Class<?> clazz : procedureClasses) {
            procedures.registerProcedure(clazz);
        }
    }

    <T> T resolveDependency(Class<T> dependency) {
        return db.getDependencyResolver().resolveDependency(dependency, ONLY);
    }

    Result runQuery(String query) {
        return QueryRunner.runQuery(db, query);
    }

    void runQuery(String query, Consumer<Result.ResultRow> check) {
        QueryRunner.runQuery(db, query, check);
    }

    Result runQuery(String query, Map<String, Object> params) {
        return QueryRunner.runQuery(db, query, params);
    }
}
