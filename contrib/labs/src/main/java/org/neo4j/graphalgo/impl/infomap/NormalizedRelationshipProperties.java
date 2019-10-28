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
package org.neo4j.graphalgo.impl.infomap;

import com.carrotsearch.hppc.LongDoubleMap;
import com.carrotsearch.hppc.LongDoubleScatterMap;
import org.neo4j.graphalgo.api.RelationshipIterator;
import org.neo4j.graphalgo.api.RelationshipProperties;
import org.neo4j.graphdb.Direction;

public class NormalizedRelationshipProperties implements RelationshipProperties {

    private RelationshipProperties weights;
    private LongDoubleMap nodeWeightSum;

    public NormalizedRelationshipProperties(
            int nodeCount,
            RelationshipIterator relationshipIterator,
            RelationshipProperties weights) {
        this.weights = weights;
        this.nodeWeightSum = new LongDoubleScatterMap();
        for (int n = 0; n < nodeCount; n++) {
            relationshipIterator.forEachRelationship(n, Direction.OUTGOING, 1.0D, (s, t, w) -> {
                nodeWeightSum.addTo(s, w);
                return true;
            });
        }
    }

    @Override
    public double relationshipProperty(long sourceNodeId, long targetNodeId, double fallbackValue) {
        return weights.relationshipProperty(sourceNodeId, targetNodeId, 1.0D) / nodeWeightSum.getOrDefault(sourceNodeId, 1.);
    }

    @Override
    public double relationshipProperty(long sourceNodeId, long targetNodeId) {
        return relationshipProperty(sourceNodeId, targetNodeId, 1.0D);
    }
}
