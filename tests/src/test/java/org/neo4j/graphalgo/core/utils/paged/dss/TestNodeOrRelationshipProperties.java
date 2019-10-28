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
package org.neo4j.graphalgo.core.utils.paged.dss;

import com.carrotsearch.hppc.IntIntHashMap;
import org.neo4j.graphalgo.api.NodeProperties;

import java.util.OptionalLong;
import java.util.stream.StreamSupport;

final class TestNodeOrRelationshipProperties implements NodeProperties {
    private final IntIntHashMap weights;

    private TestNodeOrRelationshipProperties(final IntIntHashMap weights) {
        this.weights = weights;
    }

    TestNodeOrRelationshipProperties(int... values) {
        this(toMap(values));
    }

    private static IntIntHashMap toMap(int... values) {
        assert values.length % 2 == 0;
        IntIntHashMap map = new IntIntHashMap(values.length / 2);
        for (int i = 0; i < values.length; i += 2) {
            int key = values[i];
            int value = values[i + 1];
            map.put(key, value);
        }
        return map;
    }

    @Override
    public double nodeProperty(long nodeId) {
        return nodeProperty(nodeId, 0.0);
    }

    @Override
    public double nodeProperty(long nodeId, double defaultValue) {
        int key = Math.toIntExact(nodeId);
        int index = weights.indexOf(key);
        if (weights.indexExists(index)) {
            return weights.indexGet(index);
        }
        return defaultValue;
    }

    @Override
    public OptionalLong getMaxPropertyValue() {
        return StreamSupport
            .stream(weights.values().spliterator(), false)
            .mapToLong(d -> d.value)
            .max();
    }

    @Override
    public long size() {
        return weights.size();
    }
}
