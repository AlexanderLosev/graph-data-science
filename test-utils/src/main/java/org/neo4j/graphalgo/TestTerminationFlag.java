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

import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.exceptions.Status;

import java.util.ArrayList;

public class TestTerminationFlag implements TerminationFlag {

    private final KernelTransaction transaction;
    private final long sleepMillis;

    public TestTerminationFlag(KernelTransaction transaction, long sleepMillis) {
        this.transaction = transaction;
        this.sleepMillis = sleepMillis;
    }

    @Override
    public boolean running() {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            // ignore
        }
        return !transaction.getReasonIfTerminated().isPresent() && transaction.isOpen();
    }

    public static void executeAndTerminate(KernelTransaction transaction, Runnable runnable, long sleepMillis) {
        ArrayList<Runnable> runnables = new ArrayList<>();

        runnables.add(runnable);

        runnables.add(() -> {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            transaction.markForTermination(Status.Transaction.TransactionMarkedAsFailed);
        });

        ParallelUtil.run(runnables, Pools.DEFAULT);
    }
}
