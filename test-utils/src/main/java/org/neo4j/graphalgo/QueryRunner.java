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

import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.security.AuthSubject;
import org.neo4j.internal.kernel.api.security.AuthenticationResult;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.neo4j.internal.kernel.api.security.AccessMode.Static.READ;

public final class QueryRunner {

    private QueryRunner() {}

    public static void runQuery(
        GraphDatabaseAPI db,
        String username,
        String query,
        Map<String, Object> params,
        Consumer<Result.ResultRow> check
    ) {
        try (KernelTransaction.Revertable ignored = withUsername(db.beginTx(), username)) {
            Result result = db.execute(query, params);
            result.accept(row -> {
                check.accept(row);
                return true;
            });
        }
    }

    public static Result runQuery(GraphDatabaseAPI db, String query) {
        return runQuery(db, query, Collections.emptyMap());
    }

    public static Result runQuery(GraphDatabaseAPI db, String query, Map<String, Object> params) {
        return db.execute(query, params);
    }

    public static void runQuery(GraphDatabaseAPI db, String query, Consumer<Result.ResultRow> check) {
        runQuery(db, query, Collections.emptyMap(), check);
    }

    public static Result runQuery(GraphDatabaseAPI db, String username, String query, Map<String, Object> params) {
        try (KernelTransaction.Revertable ignored = withUsername(db.beginTx(), username)) {
            return db.execute(query, params);
        }
    }

    public static void runQuery(GraphDatabaseAPI db, String query, Map<String, Object> params, Consumer<Result.ResultRow> check) {
        runQuery(db, query, params).accept(row -> {
                check.accept(row);
                return true;
            }
        );
    }

    private static KernelTransaction.Revertable withUsername(Transaction tx, String username) {
        InternalTransaction topLevelTransaction = (InternalTransaction) tx;
        AuthSubject subject = topLevelTransaction.securityContext().subject();
        SecurityContext securityContext = new SecurityContext(new CustomUserNameAuthSubject(username, subject), READ);
        return topLevelTransaction.overrideWith(securityContext);
    }

    private static class CustomUserNameAuthSubject implements AuthSubject {

        private final String username;
        private final AuthSubject authSubject;

        CustomUserNameAuthSubject(String username, AuthSubject authSubject) {
            this.username = username;
            this.authSubject = authSubject;
        }

        @Override
        public void logout() {
            authSubject.logout();
        }

        @Override
        public AuthenticationResult getAuthenticationResult() {
            return authSubject.getAuthenticationResult();
        }

        @Override
        public void setPasswordChangeNoLongerRequired() {
            authSubject.setPasswordChangeNoLongerRequired();
        }

        @Override
        public boolean hasUsername(String username) {
            return this.username.equals(username);
        }

        @Override
        public String username() {
            return username;
        }
    }
}
