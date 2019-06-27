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

import org.neo4j.graphalgo.ConfiguredAlgorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.paged.PagedDisjointSetStruct;

/**
 * unified parent for all unionfind implementations
 */
public abstract class GraphUnionFindAlgo<ME extends GraphUnionFindAlgo<ME>> extends ConfiguredAlgorithm<ME, Double> {

    protected Graph graph;

    protected GraphUnionFindAlgo(final Graph graph) {
        this.graph = graph;
    }

    /**
     * compute connected componens
     */
    public abstract PagedDisjointSetStruct compute();


    /**
     * compute connected components using a threshold
     *
     * @param threshold
     * @return
     */
    public abstract PagedDisjointSetStruct compute(double threshold);

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
}
