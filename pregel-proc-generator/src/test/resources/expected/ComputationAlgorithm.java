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
package org.neo4j.graphalgo.beta.pregel.cc;

import javax.annotation.processing.Generated;
import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.beta.pregel.Pregel;
import org.neo4j.graphalgo.beta.pregel.PregelConfig;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.logging.Log;

@Generated("org.neo4j.graphalgo.beta.pregel.PregelProcessor")
public final class ComputationAlgorithm extends Algorithm<ComputationAlgorithm, Pregel.PregelResult> {
    private final Pregel<PregelConfig> pregelJob;

    ComputationAlgorithm(Graph graph, PregelConfig configuration, AllocationTracker tracker,
            Log log) {
        this.pregelJob = Pregel.create(graph, configuration, new Computation(), Pools.DEFAULT,tracker);
    }

    @Override
    public Pregel.PregelResult compute() {
        return pregelJob.run();
    }

    @Override
    public ComputationAlgorithm me() {
        return this;
    }

    @Override
    public void release() {
        pregelJob.release();
    }
}
