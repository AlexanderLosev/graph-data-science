/*
 * Copyright (c) 2017-2020 "Neo4j,"
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

import com.carrotsearch.hppc.BitSet;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.core.loading.IdMap;

import java.util.Map;
import java.util.Set;

/**
 * A graph implementation where the nodes are backed by an {@link IdMap}.
 */
public interface IdMapGraph extends Graph, LabeledIdMapping {

    IdMap idMap();

    @Override
    IdMapGraph concurrentCopy();

    @Override
    default Set<NodeLabel> nodeLabels(long nodeId) {
        return idMap().nodeLabels(nodeId);
    }

    @Override
    default Set<NodeLabel> availableNodeLabels() {
        return idMap().availableNodeLabels();
    }

    @Override
    default Map<NodeLabel, BitSet> labelInformation() {
        return idMap().labelInformation();
    }

}
