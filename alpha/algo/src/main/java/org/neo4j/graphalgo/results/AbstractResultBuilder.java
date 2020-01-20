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
package org.neo4j.graphalgo.results;

import org.neo4j.graphalgo.core.utils.ProgressTimer;

import java.util.function.Supplier;

public abstract class AbstractResultBuilder<R> {

    protected long loadMillis = -1;
    protected long computeMillis = -1;
    protected long writeMillis = -1;

    protected long nodeCount;
    protected long relationshipCount;

    protected boolean write = false;
    protected String writeProperty;

    public void setLoadMillis(long loadMillis) {
        this.loadMillis = loadMillis;
    }

    public void setComputeMillis(long computeMillis) {
        this.computeMillis = computeMillis;
    }

    public void setWriteMillis(long writeMillis) {
        this.writeMillis = writeMillis;
    }

    public ProgressTimer timeLoad() {
        return ProgressTimer.start(this::setLoadMillis);
    }

    public ProgressTimer timeCompute() {
        return ProgressTimer.start(this::setComputeMillis);
    }

    public ProgressTimer timeWrite() {
        return ProgressTimer.start(this::setWriteMillis);
    }

    public void timeLoad(Runnable runnable) {
        try (ProgressTimer ignored = timeLoad()) {
            runnable.run();
        }
    }

    public void timeCompute(Runnable runnable) {
        try (ProgressTimer ignored = timeCompute()) {
            runnable.run();
        }
    }

    public <U> U timeCompute(Supplier<U> supplier) {
        try (ProgressTimer ignored = timeCompute()) {
            return supplier.get();
        }
    }

    public void timeWrite(Runnable runnable) {
        try (ProgressTimer ignored = timeWrite()) {
            runnable.run();
        }
    }

    public AbstractResultBuilder<R> withNodeCount(long nodeCount) {
        this.nodeCount = nodeCount;
        return this;
    }

    public AbstractResultBuilder<R> withRelationshipCount(long relationshipCount) {
        this.relationshipCount = relationshipCount;
        return this;
    }

    public AbstractResultBuilder<R> withWrite(boolean write) {
        this.write = write;
        return this;
    }

    public AbstractResultBuilder<R> withWriteProperty(String writeProperty) {
        this.writeProperty = writeProperty;
        return this;
    }

    public AbstractResultBuilder<R> withComputeMillis(long computeMillis) {
        this.computeMillis = computeMillis;
        return this;
    }

    public AbstractResultBuilder<R> withLoadMillis(long loadMillis) {
        this.loadMillis = loadMillis;
        return this;
    }

    public abstract R build();
}
