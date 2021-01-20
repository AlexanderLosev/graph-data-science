/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.core.loading;

import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.PagedLongDoubleMap;

import java.util.OptionalLong;

public final class NodePropertyMap implements NodeProperties {

    private static final MemoryEstimation MEMORY_ESTIMATION = MemoryEstimations
            .builder(NodePropertyMap.class)
            .add("properties", PagedLongDoubleMap.memoryEstimation())
            .build();

    private PagedLongDoubleMap properties;
    private final double defaultValue;

    static MemoryEstimation memoryEstimation() {
        return MEMORY_ESTIMATION;
    }

    public NodePropertyMap(PagedLongDoubleMap properties, double defaultValue) {
        this.properties = properties;
        this.defaultValue = defaultValue;
    }

    @Override
    public double nodeProperty(long nodeId) {
        return properties.getOrDefault(nodeId, defaultValue);
    }

    @Override
    public double nodeProperty(long nodeId, double defaultValue) {
        return properties.getOrDefault(nodeId, defaultValue);
    }

    @Override
    public OptionalLong getMaxPropertyValue() {
        return properties.getMaxValue();
    }

    @Override
    public long release() {
        if (properties != null) {
            long freed = properties.release();
            properties = null;
            return freed;
        }
        return 0L;
    }

    @Override
    public long size() {
        return properties.size();
    }
}
