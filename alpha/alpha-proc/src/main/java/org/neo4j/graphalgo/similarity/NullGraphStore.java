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
package org.neo4j.graphalgo.similarity;

import org.neo4j.graphalgo.ElementIdentifier;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.api.IdMapGraph;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.loading.DeletionResult;
import org.neo4j.graphalgo.core.loading.GraphStore;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.values.storable.NumberType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The NullGraphStore is used to store a {@link NullGraph}.
 * It helps non-product algos work under the standard API.
 */
public class NullGraphStore extends GraphStore {

    NullGraphStore() {
        super(null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), AllocationTracker.EMPTY);
    }

    static class NullGraphException extends UnsupportedOperationException {

        NullGraphException() {
            super("This algorithm does not support operating on named graphs. " +
                  "Please report this stacktrace to https://github.com/neo4j/graph-data-science");
        }
    }

    @Override
    public long nodeCount() {
        return 0;
    }

    @Override
    public Set<NodeLabel> nodeLabels() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> nodePropertyKeys(NodeLabel label) {
        return Collections.emptySet();
    }

    @Override
    public Map<ElementIdentifier, Set<String>> nodePropertyKeys() {
        return Collections.emptyMap();
    }

    @Override
    public long nodePropertyCount() {
        return 0;
    }

    @Override
    public boolean hasNodeProperty(Collection<NodeLabel> labels, String propertyKey) {
        return false;
    }

    @Override
    public void addNodeProperty(
        NodeLabel nodeLabel,
        String propertyKey,
        NumberType propertyType,
        NodeProperties propertyValues
    ) {}

    @Override
    public void removeNodeProperty(NodeLabel nodeLabel, String propertyKey) {}

    @Override
    public long relationshipCount() {
        return 0;
    }

    @Override
    public long relationshipCount(RelationshipType relationshipType) {
        return 0;
    }

    @Override
    public Set<RelationshipType> relationshipTypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean hasRelationshipType(RelationshipType relationshipType) {
        return false;
    }

    @Override
    public NumberType relationshipPropertyType(String propertyKey) {
        return NumberType.NO_NUMBER;
    }

    @Override
    public long relationshipPropertyCount() {
        return 0;
    }

    @Override
    public Set<String> relationshipPropertyKeys() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> relationshipPropertyKeys(RelationshipType relationshipType) {
        return Collections.emptySet();
    }

    @Override
    public void addRelationshipType(
        RelationshipType relationshipType,
        Optional<String> relationshipPropertyKey,
        Optional<NumberType> relationshipPropertyType,
        HugeGraph.Relationships relationships
    ) {}

    @Override
    public DeletionResult deleteRelationships(RelationshipType relationshipType) {
        return DeletionResult.of(c -> {});
    }

    @Override
    protected IdMapGraph createGraph(
        List<NodeLabel> filteredLabels,
        List<RelationshipType> relationshipTypes,
        Optional<String> maybeRelationshipProperty,
        int concurrency
    ) {
        return new NullGraph();
    }

    @Override
    public IdMapGraph getUnion() {
        return new NullGraph();
    }

    @Override
    public void canRelease(boolean canRelease) {}

    @Override
    public void release() {}
}