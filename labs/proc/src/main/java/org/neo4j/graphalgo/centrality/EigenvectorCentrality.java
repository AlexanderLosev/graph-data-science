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
package org.neo4j.graphalgo.centrality;

import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.logging.Log;

import java.util.concurrent.ExecutorService;

public final class EigenvectorCentrality extends Algorithm<EigenvectorCentrality, EigenvectorCentrality> {

    private final Graph rootGraph;
    private final EigenvectorCentralityConfig config;
    private final ExecutorService executorService;
    private final Log log;
    private final AllocationTracker tracker;

    // results
    // TODO

    public EigenvectorCentrality(
        Graph graph,
        EigenvectorCentralityConfig config,
        ExecutorService executorService,
        Log log,
        AllocationTracker tracker
    ) {
        this.config = config;
        this.rootGraph = graph;
        this.log = log;
        this.executorService = executorService;
        this.tracker = tracker;
    }

    @Override
    public EigenvectorCentrality compute() {
        //TODO
        return this;
    }

    //TODO: is needed?
    public EigenvectorCentralityConfig config() {
        return this.config;
    }

    @Override
    public void release() {
        this.rootGraph.releaseTopology();
    }

    @Override
    public EigenvectorCentrality me() {
        return this;
    }
}
