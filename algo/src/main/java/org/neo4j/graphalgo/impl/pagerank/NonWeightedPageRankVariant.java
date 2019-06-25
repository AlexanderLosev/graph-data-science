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
package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;

public class NonWeightedPageRankVariant implements PageRankVariant {

    @Override
    public NonWeightedComputeStep createComputeStep(
            double dampingFactor,
            long[] sourceNodeIds,
            RelationshipIterator relationshipIterator,
            Degrees degrees,
            Direction direction,
            RelationshipWeights relationshipWeights,
            AllocationTracker tracker,
            int partitionSize,
            long start,
            DegreeCache aggregatedDegrees,
            long nodeCount) {
        return new NonWeightedComputeStep(
                dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                direction,
                tracker,
                partitionSize,
                start
        );
    }

    @Override
    public DegreeComputer degreeComputer(Graph graph, Direction direction) {
        return new NoOpDegreeComputer();
    }
}
