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
package org.neo4j.graphalgo.core.loading;


import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.huge.AdjacencyList;
import org.neo4j.graphalgo.core.huge.AdjacencyOffsets;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;

import java.util.Arrays;

public class RelationshipsBuilder {

    private static final AdjacencyListBuilder[] EMPTY_WEIGHTS = new AdjacencyListBuilder[0];

    private final Aggregation[] aggregations;
    final AdjacencyListBuilder adjacency;
    final AdjacencyListBuilder[] weights;

    AdjacencyOffsets globalAdjacencyOffsets;
    AdjacencyOffsets[] globalWeightOffsets;

    public RelationshipsBuilder(
        Aggregation[] aggregations,
        AllocationTracker tracker,
        int numberOfRelationshipProperties
    ) {
        if (Arrays.stream(aggregations).anyMatch(d -> d == Aggregation.DEFAULT)) {
            throw new IllegalArgumentException(String.format(
                "Needs an explicit aggregation, but got %s",
                Arrays.toString(aggregations)
            ));
        }
        this.aggregations = aggregations;
        adjacency = AdjacencyListBuilder.newBuilder(tracker);
        if (numberOfRelationshipProperties > 0) {
            weights = new AdjacencyListBuilder[numberOfRelationshipProperties];
            // TODO: can we avoid to create an allocator/complete adjacency list
            //  if we know that the property does not exist?
            Arrays.setAll(weights, i -> AdjacencyListBuilder.newBuilder(tracker));
        } else {
            weights = EMPTY_WEIGHTS;
        }
    }

    final ThreadLocalRelationshipsBuilder threadLocalRelationshipsBuilder(
            long[] adjacencyOffsets,
            long[][] weightOffsets) {
        return new ThreadLocalRelationshipsBuilder(
            aggregations,
                adjacency.newAllocator(),
                Arrays.stream(weights)
                        .map(AdjacencyListBuilder::newAllocator)
                        .toArray(AdjacencyListBuilder.Allocator[]::new),
                adjacencyOffsets,
                weightOffsets);
    }

    final void setGlobalAdjacencyOffsets(AdjacencyOffsets globalAdjacencyOffsets) {
        this.globalAdjacencyOffsets = globalAdjacencyOffsets;
    }

    final void setGlobalWeightOffsets(AdjacencyOffsets[] globalWeightOffsets) {
        this.globalWeightOffsets = globalWeightOffsets;
    }

    public AdjacencyList adjacency() {
        return adjacency.build();
    }

    // TODO: This returns only the first of possibly multiple properties
    public AdjacencyList weights() {
        return weights.length > 0 ? weights[0].build() : null;
    }

    public AdjacencyOffsets globalAdjacencyOffsets() {
        return globalAdjacencyOffsets;
    }

    // TODO: This returns only the first of possibly multiple properties
    public AdjacencyOffsets globalWeightOffsets() {
        return globalWeightOffsets[0];
    }
}
