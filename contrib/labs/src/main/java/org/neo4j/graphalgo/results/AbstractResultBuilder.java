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
package org.neo4j.graphalgo.results;

import org.neo4j.graphalgo.core.utils.ProgressTimer;

public abstract class AbstractResultBuilder<R> {

    protected long loadDuration = -1;
    protected long evalDuration = -1;
    protected long writeDuration = -1;

    public void setLoadDuration(long loadDuration) {
        this.loadDuration = loadDuration;
    }

    public void setEvalDuration(long evalDuration) {
        this.evalDuration = evalDuration;
    }

    public void setWriteDuration(long writeDuration) {
        this.writeDuration = writeDuration;
    }

    public ProgressTimer timeLoad() {
        return ProgressTimer.start(this::setLoadDuration);
    }

    public ProgressTimer timeEval() {
        return ProgressTimer.start(this::setEvalDuration);
    }

    public ProgressTimer timeWrite() {
        return ProgressTimer.start(this::setWriteDuration);
    }

    public void timeLoad(Runnable runnable) {
        try (ProgressTimer ignored = timeLoad()) {
            runnable.run();
        }
    }

    public void timeEval(Runnable runnable) {
        try (ProgressTimer ignored = timeEval()) {
            runnable.run();
        }
    }

    public void timeWrite(Runnable runnable) {
        try (ProgressTimer ignored = timeWrite()) {
            runnable.run();
        }
    }

    public abstract R build();
}
