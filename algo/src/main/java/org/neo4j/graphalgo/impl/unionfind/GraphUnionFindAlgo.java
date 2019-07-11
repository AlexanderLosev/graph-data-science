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
package org.neo4j.graphalgo.impl.unionfind;

import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.HugeWeightMapping;
import org.neo4j.graphalgo.core.utils.paged.DisjointSetStruct;

/**
 * unified parent for all unionfind implementations
 */
public abstract class GraphUnionFindAlgo<ME extends GraphUnionFindAlgo<ME>> extends Algorithm<ME> {

    protected Graph graph;

    protected final GraphUnionFindAlgo.Config algoConfig;

    public static final String COMMUNITY_TYPE = "community";

    protected GraphUnionFindAlgo(final Graph graph, final GraphUnionFindAlgo.Config algoConfig) {
        this.graph = graph;
        this.algoConfig = algoConfig;
    }

    public double threshold() {
        return algoConfig.threshold;
    }

    /**
     * compute connected components
     */
    public DisjointSetStruct compute() {
        return Double.isFinite(threshold()) ? compute(threshold()) : computeUnrestricted();
    }

    public abstract DisjointSetStruct compute(double threshold);

    public abstract DisjointSetStruct computeUnrestricted();

    /**
     * method reference for self
     *
     * @return
     */
    @Override
    public ME me() {
        //noinspection unchecked
        return (ME) this;
    }

    /**
     * release internal datastructures
     *
     * @return
     */
    @Override
    public ME release() {
        graph = null;
        return me();
    }

    public static class Config {

        public final HugeWeightMapping communityMap;
        public final double threshold;

        public Config(
                final HugeWeightMapping communityMap,
                final double threshold) {
            this.communityMap = communityMap;
            this.threshold = threshold;
        }
    }
}
