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

import com.carrotsearch.hppc.BitSet;
import org.neo4j.graphalgo.ElementIdentifier;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.IdMapGraph;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.api.UnionNodeProperties;
import org.neo4j.graphalgo.core.ProcedureConstants;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.huge.NodeFilteredGraph;
import org.neo4j.graphalgo.core.huge.UnionGraph;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.neo4j.graphalgo.NodeLabel.ALL_NODES;
import static org.neo4j.graphalgo.RelationshipType.ALL_RELATIONSHIPS;
import static org.neo4j.graphalgo.config.AlgoBaseConfig.ALL_NODE_LABEL_IDENTIFIERS;

public final class GraphStore {

    private final IdMap nodes;

    private final Map<NodeLabel, Map<String, NodeProperties>> nodeProperties;

    private final Map<String, HugeGraph.TopologyCSR> relationships;

    private final Map<String, Map<String, HugeGraph.PropertyCSR>> relationshipProperties;

    private final Set<Graph> createdGraphs;

    private final AllocationTracker tracker;

    private LocalDateTime modificationTime;

    public static GraphStore of(
        IdMap nodes,
        Map<NodeLabel, Map<String, NodeProperties>> nodeProperties,
        Map<String, HugeGraph.TopologyCSR> relationships,
        Map<String, Map<String, HugeGraph.PropertyCSR>> relationshipProperties,
        AllocationTracker tracker
    ) {
        return new GraphStore(
            nodes,
            nodeProperties,
            relationships,
            relationshipProperties,
            tracker
        );
    }

    public static GraphStore of(
        HugeGraph graph,
        String relationshipType,
        Optional<String> relationshipProperty,
        AllocationTracker tracker
    ) {
        HugeGraph.Relationships relationships = graph.relationships();

        Map<String, HugeGraph.TopologyCSR> topology = singletonMap(relationshipType, relationships.topology());

        Map<NodeLabel, Map<String, NodeProperties>> nodeProperties = new HashMap<>();
        nodeProperties.put(
            ALL_NODES,
            graph.availableNodeProperties().stream().collect(Collectors.toMap(
                Function.identity(),
                graph::nodeProperties
            ))
        );

        Map<String, Map<String, HugeGraph.PropertyCSR>> relationshipProperties = Collections.emptyMap();
        if (relationshipProperty.isPresent() && relationships.properties().isPresent()) {
            relationshipProperties = singletonMap(
                relationshipType,
                singletonMap(relationshipProperty.get(), relationships.properties().get())
            );
        }

        return GraphStore.of(graph.idMap(), nodeProperties, topology, relationshipProperties, tracker);
    }

    private GraphStore(
        IdMap nodes,
        Map<NodeLabel, Map<String, NodeProperties>> nodeProperties,
        Map<String, HugeGraph.TopologyCSR> relationships,
        Map<String, Map<String, HugeGraph.PropertyCSR>> relationshipProperties,
        AllocationTracker tracker
    ) {
        this.nodes = nodes;
        this.nodeProperties = nodeProperties;
        this.relationships = relationships;
        this.relationshipProperties = relationshipProperties;
        this.createdGraphs = new HashSet<>();
        this.modificationTime = LocalDateTime.now();
        this.tracker = tracker;
    }

    public LocalDateTime modificationTime() {
        return modificationTime;
    }

    public IdMap nodes() {
        return this.nodes;
    }

    public Set<NodeLabel> nodeLabels() {
        return new HashSet<>(this
            .nodes
            .maybeLabelInformation
            .map(Map::keySet)
            .orElseGet(() -> Collections.singleton(ALL_NODES)));
    }

    public Set<String> nodePropertyKeys(ElementIdentifier label) {
        return new HashSet<>(nodeProperties.getOrDefault(label, new HashMap<>()).keySet());
    }

    public Map<ElementIdentifier, Set<String>> nodePropertyKeys() {
        return nodeLabels().stream().collect(Collectors.toMap(Function.identity(), this::nodePropertyKeys));
    }

    public long nodePropertyCount() {
        // TODO: This is not the correct value. We would need to look into the bitsets in order to retrieve the correct value.
        return nodeProperties.values().stream().mapToLong(properties -> properties.keySet().size()).sum() * nodeCount();
    }

    public boolean hasNodeProperty(Collection<NodeLabel> labels, String propertyKey) {
        return labels
            .stream()
            .allMatch(label -> nodeProperties.containsKey(label) && nodeProperties.get(label).containsKey(propertyKey));
    }

    public void addNodeProperty(NodeLabel nodeLabel, String propertyKey, NodeProperties nodeProperties) {
        updateGraphStore((graphStore) -> graphStore.nodeProperties.compute(nodeLabel, (k, nodePropertyMap) -> {
            Map<String, NodeProperties> updatedPropertyMap = nodePropertyMap == null ? new HashMap<>() : nodePropertyMap;
            updatedPropertyMap.putIfAbsent(propertyKey, nodeProperties);
            return updatedPropertyMap;
        }));
    }

    public void removeNodeProperty(NodeLabel nodeLabel, String propertyKey) {
        updateGraphStore(graphStore -> {
            if (graphStore.nodeProperties.containsKey(nodeLabel)) {
                Map<String, NodeProperties> propertiesForLabel = graphStore.nodeProperties.get(nodeLabel);

                propertiesForLabel.remove(propertyKey);

                if (propertiesForLabel.isEmpty()) {
                    graphStore.nodeProperties.remove(nodeLabel);
                }
            }
        });
    }

    public NodeProperties nodeProperty(String propertyKey) {
        if (nodes.maybeLabelInformation.isPresent()) {
            Map<NodeLabel, NodeProperties> properties = new HashMap<>();
            this.nodeProperties.forEach((labelIdentifier, propertyMap) -> {
                if (propertyMap.containsKey(propertyKey)) {
                    properties.put(labelIdentifier, propertyMap.get(propertyKey));
                }
            });
            return new UnionNodeProperties(properties, nodes.maybeLabelInformation.get());
        }
        return nodeProperties.get(ALL_NODES).get(propertyKey);
    }

    public NodeProperties nodeProperty(ElementIdentifier label, String propertyKey) {
        return this.nodeProperties.getOrDefault(label, Collections.emptyMap()).get(propertyKey);
    }

    public Set<String> relationshipTypes() {
        return relationships.keySet();
    }

    public boolean hasRelationshipType(String relationshipType) {
        return relationships.containsKey(relationshipType);
    }

    public long relationshipCount() {
        return relationships.values().stream()
            .mapToLong(HugeGraph.TopologyCSR::elementCount)
            .sum();
    }

    public long relationshipCount(String relationshipType) {
        return relationships.get(relationshipType).elementCount();
    }

    public long relationshipPropertyCount() {
        return relationshipProperties
            .values()
            .stream()
            .flatMapToLong(map -> map.values().stream().mapToLong(HugeGraph.PropertyCSR::elementCount))
            .sum();
    }

    public Set<String> relationshipPropertyKeys() {
        return relationshipProperties
            .values()
            .stream()
            .flatMap(properties -> properties.keySet().stream())
            .collect(Collectors.toSet());
    }

    public Set<String> relationshipPropertyKeys(String relationshipType) {
        return relationshipProperties.getOrDefault(relationshipType, Collections.emptyMap()).keySet();
    }

    public void addRelationshipType(
        String relationshipType,
        Optional<String> relationshipProperty,
        HugeGraph.Relationships relationships
    ) {
        updateGraphStore(graphStore -> {
            if (!hasRelationshipType(relationshipType)) {
                graphStore.relationships.put(relationshipType, relationships.topology());

                if (relationshipProperty.isPresent() && relationships.properties().isPresent()) {
                    HugeGraph.PropertyCSR propertyCSR = relationships.properties().get();
                    graphStore.relationshipProperties
                        .computeIfAbsent(relationshipType, ignore -> new HashMap<>())
                        .putIfAbsent(relationshipProperty.get(), propertyCSR);
                }
            }
        });
    }

    public Graph getGraph(String... relationshipTypes) {
        return getGraph(ALL_NODE_LABEL_IDENTIFIERS, Arrays.asList(relationshipTypes), Optional.empty(), 1);
    }

    public Graph getGraph(String relationshipType, Optional<String> relationshipProperty) {
        return getGraph(ALL_NODE_LABEL_IDENTIFIERS, singletonList(relationshipType), relationshipProperty, 1);
    }

    public Graph getGraph(List<String> relationshipTypes, Optional<String> maybeRelationshipProperty) {
        validateInput(relationshipTypes, maybeRelationshipProperty);
        return createGraph(ALL_NODE_LABEL_IDENTIFIERS, relationshipTypes, maybeRelationshipProperty, 1);
    }

    public Graph getGraph(
        List<NodeLabel> nodeLabels,
        List<String> relationshipTypes,
        Optional<String> maybeRelationshipProperty,
        int concurrency
    ) {
        validateInput(relationshipTypes, maybeRelationshipProperty);
        return createGraph(nodeLabels, relationshipTypes, maybeRelationshipProperty, concurrency);
    }

    public IdMapGraph getUnion() {
        return UnionGraph.of(relationships
            .keySet()
            .stream()
            .flatMap(relationshipType -> {
                if (relationshipProperties.containsKey(relationshipType)) {
                    return relationshipProperties
                        .get(relationshipType)
                        .keySet()
                        .stream()
                        .map(propertyKey -> createGraph(ALL_NODE_LABEL_IDENTIFIERS, relationshipType, Optional.of(propertyKey)));
                } else {
                    return Stream.of(createGraph(ALL_NODE_LABEL_IDENTIFIERS, relationshipType, Optional.empty()));
                }
            })
            .collect(Collectors.toList()));
    }

    public void canRelease(boolean canRelease) {
        createdGraphs.forEach(graph -> graph.canRelease(canRelease));
    }

    public void release() {
        createdGraphs.forEach(Graph::release);
    }

    public long nodeCount() {
        return nodes.nodeCount();
    }

    private IdMapGraph createGraph(
        List<NodeLabel> nodeLabels,
        String relationshipType,
        Optional<String> maybeRelationshipProperty
    ) {
        return createGraph(nodeLabels, singletonList(relationshipType), maybeRelationshipProperty, 1);
    }

    private IdMapGraph createGraph(
        List<NodeLabel> filteredLabels,
        List<String> relationshipTypes,
        Optional<String> maybeRelationshipProperty,
        int concurrency
    ) {
        boolean loadAllRelationships = relationshipTypes.contains(ALL_RELATIONSHIPS.name);
        boolean loadAllNodes = filteredLabels.contains(ALL_NODES);

        Collection<NodeLabel> expandedLabels = loadAllNodes ? nodeLabels() : filteredLabels;

        boolean containsAllNodes = true;
        BitSet combinedBitSet = BitSet.newInstance();

        if (this.nodes.maybeLabelInformation.isPresent() && !loadAllNodes) {
            Map<NodeLabel, BitSet> labelInformation = this.nodes.maybeLabelInformation.get();
            validateNodeLabelFilter(expandedLabels, labelInformation);
            expandedLabels.forEach(label -> combinedBitSet.union(labelInformation.get(label)));
            containsAllNodes = combinedBitSet.cardinality() == this.nodes.nodeCount();
        }

        Optional<IdMap> filteredNodes = loadAllNodes || !this.nodes.maybeLabelInformation.isPresent() || containsAllNodes
            ? Optional.empty()
            : Optional.of(this.nodes.withFilteredLabels(combinedBitSet, concurrency));

        List<IdMapGraph> filteredGraphs = relationships.entrySet().stream()
            .filter(relTypeAndCSR -> loadAllRelationships || relationshipTypes.contains(relTypeAndCSR.getKey()))
            .map(relTypeAndCSR -> {
                HugeGraph initialGraph = HugeGraph.create(
                    this.nodes,
                    filterNodeProperties(expandedLabels, this.nodes.maybeLabelInformation),
                    relTypeAndCSR.getValue(),
                    maybeRelationshipProperty.map(propertyKey -> relationshipProperties
                        .get(relTypeAndCSR.getKey())
                        .get(propertyKey)),
                    tracker
                );

                if (filteredNodes.isPresent()) {
                    return new NodeFilteredGraph(initialGraph, filteredNodes.get());
                } else {
                    return initialGraph;
                }
            })
            .collect(Collectors.toList());

        filteredGraphs.forEach(graph -> graph.canRelease(false));
        createdGraphs.addAll(filteredGraphs);
        return UnionGraph.of(filteredGraphs);
    }

    private Map<String, NodeProperties> filterNodeProperties(
        Collection<NodeLabel> labels,
        Optional<Map<NodeLabel, BitSet>> maybeElementIdentifierBitSetMap
    ) {
        if (this.nodeProperties.isEmpty()) {
            return Collections.emptyMap();
        }
        if (labels.size() == 1 || !maybeElementIdentifierBitSetMap.isPresent()) {
            return this.nodeProperties.get(labels.iterator().next());
        }

        Map<String, Map<NodeLabel, NodeProperties>> invertedNodeProperties = new HashMap<>();
        nodeProperties
            .entrySet()
            .stream()
            .filter(entry -> labels.contains(entry.getKey()) || labels.contains(ALL_NODES))
            .forEach(entry -> entry
                .getValue()
                .forEach((propertyKey, nodeProperty) -> invertedNodeProperties.compute(
                    propertyKey,
                    (k, innerMap) -> {
                        Map<NodeLabel, NodeProperties> labelToNodePropertiesMap;
                        if (innerMap == null) {
                            labelToNodePropertiesMap = new HashMap<>();
                        } else {
                            labelToNodePropertiesMap = innerMap;
                        }
                        labelToNodePropertiesMap.put(entry.getKey(), nodeProperty);
                        return labelToNodePropertiesMap;
                    }
                )));
        return invertedNodeProperties
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new UnionNodeProperties(entry.getValue(), maybeElementIdentifierBitSetMap.get())
            ));
    }

    private void validateNodeLabelFilter(Collection<NodeLabel> nodeLabels, Map<NodeLabel, BitSet> labelInformation) {
        List<ElementIdentifier> invalidLabels = nodeLabels
            .stream()
            .filter(label -> !new HashSet<>(labelInformation.keySet()).contains(label))
            .collect(Collectors.toList());
        if (!invalidLabels.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "Specified labels %s do not correspond to any of the node projections %s.",
                invalidLabels,
                labelInformation.keySet()
            ));
        }
    }

    private void validateInput(Collection<String> relationshipTypes, Optional<String> maybeRelationshipProperty) {
        if (relationshipTypes.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "The parameter '%s' should not be empty. Use '*' to load all relationship types.",
                ProcedureConstants.RELATIONSHIP_TYPES
            ));
        }

        if (!relationshipTypes.contains(ALL_RELATIONSHIPS.name)) {
            relationshipTypes.forEach(relationshipType -> {
                if (!relationships.containsKey(relationshipType)) {
                    throw new IllegalArgumentException(String.format(
                        "No relationships have been loaded for relationship type '%s'",
                        relationshipType
                    ));
                }

                maybeRelationshipProperty.ifPresent(relationshipProperty -> {
                    if (!relationshipProperties.get(relationshipType).containsKey(relationshipProperty)) {
                        throw new IllegalArgumentException(String.format(
                            "No relationships have been loaded for relationship type '%s' and relationship property '%s'.",
                            relationshipType,
                            maybeRelationshipProperty.get()
                        ));
                    }
                });
            });
        }
    }

    private synchronized void updateGraphStore(Consumer<GraphStore> updateFunction) {
        updateFunction.accept(this);
        this.modificationTime = LocalDateTime.now();
    }
}

