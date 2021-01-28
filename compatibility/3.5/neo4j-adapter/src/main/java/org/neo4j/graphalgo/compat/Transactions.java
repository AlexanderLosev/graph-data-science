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
package org.neo4j.graphalgo.compat;

import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.TransactionTerminatedException;
import org.neo4j.kernel.api.exceptions.Status;

public class Transactions {

    public static RuntimeException transactionTerminated() {
        return new TransactionTerminatedException(Status.Transaction.Terminated);
    }

    public static void commit(Transaction transaction) {
        transaction.success();
    }

    public static void fail(Transaction transaction) {
        transaction.failure();
    }

    public static void close(Transaction transaction) {
        transaction.close();
    }

    public static Status markedAsFailed() {
        return Status.Transaction.TransactionMarkedAsFailed;
    }

    public static Class<? extends Throwable> transactionFailureException() {
        return TransactionFailureException.class;
    }
}
