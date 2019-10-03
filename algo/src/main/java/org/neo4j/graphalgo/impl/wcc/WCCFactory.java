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
package org.neo4j.graphalgo.impl.wcc;

import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.logging.Log;

public class WCCFactory<A extends WCC<A>> extends AlgorithmFactory<A> {

    public static final String CONFIG_ALGO_TYPE = "algoType";
    public static final String CONFIG_THRESHOLD = "threshold";
    public static final String CONFIG_SEED_PROPERTY = "seedProperty";

    public static final String SEED_TYPE = "seed";

    private final WCCType algorithmType;

    private final boolean incremental;

    public WCCFactory(final WCCType algorithmType, final boolean incremental) {
        this.algorithmType = algorithmType;
        this.incremental = incremental;
    }

    @SuppressWarnings("unchecked")
    @Override
    public A build(
            final Graph graph,
            final ProcedureConfiguration configuration,
            final AllocationTracker tracker,
            final Log log) {
        int concurrency = configuration.getConcurrency();
        int minBatchSize = configuration.getBatchSize();

        WCC.Config algoConfig = new WCC.Config(
                graph.availableNodeProperties().contains(SEED_TYPE) ? graph.nodeProperties(SEED_TYPE) : null,
                configuration.get(CONFIG_THRESHOLD, Double.NaN)
        );

        final WCC<?> algo = algorithmType.create(
                graph,
                Pools.DEFAULT,
                minBatchSize,
                concurrency,
                algoConfig,
                tracker);
        return (A) algo;
    }

    @Override
    public MemoryEstimation memoryEstimation() {
        return algorithmType.memoryEstimation(incremental);
    }
}
