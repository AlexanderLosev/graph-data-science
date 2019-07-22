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
package org.neo4j.graphalgo.api;

import static org.neo4j.graphalgo.core.utils.RawValues.getHead;
import static org.neo4j.graphalgo.core.utils.RawValues.getTail;

public interface HugeWeightMapping extends WeightMapping, HugeNodeWeights {

    /**
     * returns the weight for the relationship defined by their start and end nodes.
     */
    double weight(long source, long target);

    /**
     * returns the weight for the relationship defined by their start and end nodes
     * or the given default weight if no weight has been defined.
     * The default weight has precedence over the default weight defined by the loader.
     */
    double weight(long source, long target, double defaultValue);

    /**
     * returns the weight for a node or the loaded default weight if no weight has been defined.
     */
    @Override
    default double nodeWeight(long nodeId) {
        return weight(nodeId, -1L);
    }

    /**
     * returns the weight for a node or the given default weight if no weight has been defined.
     * The default weight has precedence over the default weight defined by the loader.
     */
    default double nodeWeight(long nodeId, double defaultValue) {
        return weight(nodeId, -1L, defaultValue);
    }

    /**
     * release internal data structures and return an estimate how many
     * bytes were freed.
     * The mapping is not usable afterwards.
     */
    long release();

    /**
     * @deprecated Use {@link #weight(long, long)}
     * @param id
     * @return
     */
    @Deprecated
    @Override
    default double get(long id) {
        return weight((long) getHead(id), (long) getTail(id));
    }

    /**
     * @deprecated Use {@link #weight(long, long, double)}
     * @param id
     * @param defaultValue
     * @return
     */
    @Deprecated
    @Override
    default double get(final long id, final double defaultValue) {
        return weight((long) getHead(id), (long) getTail(id), defaultValue);
    }

    /**
     * @deprecated Use {@link #weight(long, long)}
     * @param source
     * @param target
     * @return
     */
    @Deprecated
    @Override
    default double get(int source, int target) {
        return weight((long) source, (long) target);
    }

    /**
     * @deprecated Use {@link #nodeWeight(long)}
     * @param id
     * @return
     */
    @Deprecated
    @Override
    default double get(int id) {
        return nodeWeight((long) id);
    }

    /**
     * @deprecated Use {@link #nodeWeight(long, double)}
     * @param id
     * @param defaultValue
     * @return
     */
    @Deprecated
    @Override
    default double get(int id, double defaultValue) {
        return nodeWeight((long) id, defaultValue);
    }
}
