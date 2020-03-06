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
package org.neo4j.graphalgo.compat;

import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.api.KernelTransactions;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.impl.storageengine.impl.recordstorage.RecordStorageEngine;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GraphDatabaseApiProxy {

    public static <T> T resolveDependency(GraphDatabaseService db, Class<T> dependency) {
        return ((GraphDatabaseAPI) db)
            .getDependencyResolver()
            .resolveDependency(dependency, DependencyResolver.SelectionStrategy.ONLY);
    }

    public static void registerProcedures(GraphDatabaseService db, Class<?>... procedureClasses) throws Exception {
        Procedures procedures = resolveDependency(db, Procedures.class);
        for (Class<?> clazz : procedureClasses) {
            procedures.registerProcedure(clazz);
        }
    }

    public static void registerFunctions(GraphDatabaseService db, Class<?>... functionClasses) throws Exception {
        Procedures procedures = resolveDependency(db, Procedures.class);
        for (Class<?> clazz : functionClasses) {
            procedures.registerFunction(clazz);
        }
    }

    public static void registerAggregationFunctions(GraphDatabaseService db, Class<?>... functionClasses) throws Exception {
        Procedures procedures = resolveDependency(db, Procedures.class);
        for (Class<?> clazz : functionClasses) {
            procedures.registerAggregationFunction(clazz);
        }
    }

    public static Node getNodeById(GraphDatabaseService db, long id) {
        return applyInTransaction(db, tx -> {
            try {
                return db.getNodeById(id);
            } catch (NotFoundException e) {
                return null;
            }
        });
    }

    public static Node createNode(GraphDatabaseService db) {
        return applyInTransaction(db, tx -> db.createNode());
    }

    public static Node findNode(GraphDatabaseService db, Label label, String propertyKey, Object propertyValue) {
        return applyInTransaction(db, tx -> db.findNode(label, propertyKey, propertyValue));
    }

    public static ResourceIterator<Node> findNodes(GraphDatabaseService db, Label label, String propertyKey, Object propertyValue) {
        return applyInTransaction(db, tx -> db.findNodes(label, propertyKey, propertyValue));
    }

    public static NeoStores neoStores(GraphDatabaseService db) {
        return resolveDependency(db, RecordStorageEngine.class).testAccessNeoStores();
    }

    public static KernelTransaction newExplicitKernelTransaction(
        GraphDatabaseService db,
        long timeout,
        TimeUnit timeoutUnit
    ) {
        return GraphDatabaseApiProxy
            .resolveDependency(db, KernelTransactions.class)
            .newInstance(
                KernelTransaction.Type.explicit,
                LoginContext.AUTH_DISABLED,
                timeoutUnit.toMillis(timeout)
            );
    }

    public static ProcedureCallContext procedureCallContext(String... outputFieldNames) {
        return new ProcedureCallContext(outputFieldNames, false);
    }

    public static void runInTransaction(GraphDatabaseService db, Consumer<Transaction> block) {
        try (Transaction tx = db.beginTx()) {
            block.accept(tx);
            tx.success();
        }
    }

    public static <T> T applyInTransaction(GraphDatabaseService db, Function<Transaction, T> block) {
        try (Transaction tx = db.beginTx()) {
            T returnValue = block.apply(tx);
            tx.success();
            return returnValue;
        }
    }

    public static <T> T withKernelTransaction(GraphDatabaseService db, Function<KernelTransaction, T> block) {
        return applyInTransaction(db, tx -> {
             KernelTransaction kernelTransaction = resolveDependency(db, ThreadToStatementContextBridge.class)
                .getKernelTransactionBoundToThisThread(true);
            return block.apply(kernelTransaction);
        });
    }

    public static Result runQuery(GraphDatabaseService db, String query, Map<String, Object> params) {
        return applyInTransaction(db, tx -> db.execute(query, params));
    }

    private GraphDatabaseApiProxy() {
        throw new UnsupportedOperationException("No instances");
    }
}
