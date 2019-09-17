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

import org.neo4j.graphalgo.core.utils.RawValues;

import static org.neo4j.graphalgo.core.utils.RawValues.getHead;
import static org.neo4j.graphalgo.core.utils.RawValues.getTail;

public interface WeightMapping extends NodeWeights {
    /**
     * Returns the weight for the relationship defined by their start and end nodes.
     */
    double weight(long source, long target);

    /**
     * Returns the weight for the relationship defined by their start and end nodes
     * or the given default weight if no weight has been defined.
     * The default weight has precedence over the default weight defined by the loader.
     */
    double weight(long source, long target, double defaultValue);

    /**
     * Returns the weight for a node or the loaded default weight if no weight has been defined.
     */
    @Override
    default double nodeWeight(long nodeId) {
        return weight(nodeId, -1L);
    }

    /**
     * Returns the weight for a node or the given default weight if no weight has been defined.
     * The default weight has precedence over the default weight defined by the loader.
     */
    default double nodeWeight(long nodeId, double defaultValue) {
        return weight(nodeId, -1L, defaultValue);
    }

    /**
     * Returns the weight for ID if set or the load-time specified default weight otherwise.
     */
    default double weight(long id) {
        return weight((long) getHead(id), (long) getTail(id));
    }

    /**
     * Returns the weight for ID if set or the given default weight otherwise.
     */
    default double weight(long id, final double defaultValue) {
        return weight((long) getHead(id), (long) getTail(id), defaultValue);
    }

    /**
     * Release internal data structures and return an estimate how many bytes were freed.
     *
     * Note that the mapping is not usable afterwards.
     */
    long release();

    /**
     * Returns the number of keys stored in that mapping.
     */
    long size();

    default double weight(int source, int target) {
        return weight(RawValues.combineIntInt(source, target));
    }

    default double nodeWeight(int id) {
        return weight(id, -1);
    }

    default double nodeWeight(int id, double defaultValue) {
        return weight(RawValues.combineIntInt(id, -1), defaultValue);
    }

    /**
     * Returns the maximum value contained in the mapping or {@code defaultValue} if the mapping is empty.
     *
     * @param defaultValue value being returned if the mapping is empty
     * @return maximum value or given default value if mapping is empty
     */
    default long getMaxValue(long defaultValue) {
        return size() == 0 ? defaultValue : getMaxValue();
    }

    /**
     * Returns the maximum value contained in the mapping.
     * @return
     */
    default long getMaxValue() {
        return -1L;
    }
}
