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
package org.neo4j.graphalgo.core.concurrency;

import org.neo4j.graphalgo.annotation.IdenticalCompat;

@IdenticalCompat
public final class ConcurrencyMonitor {

    private static ConcurrencyMonitor INSTANCE;

    public static ConcurrencyMonitor instance() {
        if (INSTANCE == null) {
            INSTANCE = new ConcurrencyMonitor();
        }
        return INSTANCE;
    }

    private enum State {
        UNLIMITED,
        LIMITED
    }

    private State currentState;

    private ConcurrencyMonitor() {
        this.currentState = State.LIMITED;
    }

    public boolean isUnlimited() {
        return get() == State.UNLIMITED;
    }

    public boolean isLimited() {
        return !isUnlimited();
    }

    public void setUnlimited() {
        set(State.UNLIMITED);
    }

    public void setLimited() {
        set(State.LIMITED);
    }

    private void set(State state) {
        this.currentState = state;
    }

    private State get() {
        return currentState;
    }
}
