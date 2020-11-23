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

import org.jetbrains.annotations.NotNull;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.api.CSRGraph;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.api.NodeMapping;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.api.NodeProperty;
import org.neo4j.graphalgo.api.NodePropertyStore;
import org.neo4j.graphalgo.api.RelationshipProperty;
import org.neo4j.graphalgo.api.RelationshipPropertyStore;
import org.neo4j.graphalgo.api.Relationships;
import org.neo4j.graphalgo.api.UnionNodeProperties;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;
import org.neo4j.graphalgo.api.schema.GraphSchema;
import org.neo4j.graphalgo.api.schema.NodeSchema;
import org.neo4j.graphalgo.api.schema.RelationshipPropertySchema;
import org.neo4j.graphalgo.api.schema.RelationshipSchema;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.ProcedureConstants;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.huge.NodeFilteredGraph;
import org.neo4j.graphalgo.core.huge.UnionGraph;
import org.neo4j.graphalgo.core.utils.TimeUtil;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.utils.ExceptionUtil;
import org.neo4j.graphalgo.utils.StringJoining;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.values.storable.NumberType;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.neo4j.graphalgo.NodeLabel.ALL_NODES;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public class CSRGraphStore implements GraphStore {

    private final int concurrency;

    private final NamedDatabaseId databaseId;

    private final NodeMapping nodes;

    private final Map<NodeLabel, NodePropertyStore> nodeProperties;

    protected final Map<RelationshipType, Relationships.Topology> relationships;

    private final Map<RelationshipType, RelationshipPropertyStore> relationshipProperties;

    private final Set<Graph> createdGraphs;

    private final AllocationTracker tracker;

    private ZonedDateTime modificationTime;

    public static CSRGraphStore of(
        NamedDatabaseId databaseId,
        NodeMapping nodes,
        Map<NodeLabel, Map<PropertyMapping, NodeProperties>> nodeProperties,
        Map<RelationshipType, Relationships.Topology> relationships,
        Map<RelationshipType, Map<PropertyMapping, Relationships.Properties>> relationshipProperties,
        int concurrency,
        AllocationTracker tracker
    ) {
        return of(
            databaseId,
            nodes,
            nodeProperties,
            relationships,
            relationshipProperties,
            concurrency,
            tracker,
            CSRGraphStore::new
        );
    }

    public interface CSRGraphStoreConstructor<T> {
        T construct(
            NamedDatabaseId databaseId,
            NodeMapping nodes,
            Map<NodeLabel, NodePropertyStore> nodeProperties,
            Map<RelationshipType, Relationships.Topology> relationships,
            Map<RelationshipType, RelationshipPropertyStore> relationshipProperties,
            int concurrency,
            AllocationTracker tracker
        );
    }

    public static <T extends CSRGraphStore> T of(
        NamedDatabaseId databaseId,
        NodeMapping nodes,
        Map<NodeLabel, Map<PropertyMapping, NodeProperties>> nodeProperties,
        Map<RelationshipType, Relationships.Topology> relationships,
        Map<RelationshipType, Map<PropertyMapping, Relationships.Properties>> relationshipProperties,
        int concurrency,
        AllocationTracker tracker,
        CSRGraphStoreConstructor<T> constructor
    ) {
        Map<NodeLabel, NodePropertyStore> nodePropertyStores = new HashMap<>(nodeProperties.size());
        nodeProperties.forEach((nodeLabel, propertyMap) -> {
            NodePropertyStore.Builder builder = NodePropertyStore.builder();
            propertyMap.forEach((propertyMapping, propertyValues) -> builder.putNodeProperty(
                propertyMapping.propertyKey(),
                NodeProperty.of(
                    propertyMapping.propertyKey(),
                    PropertyState.PERSISTENT,
                    propertyValues,
                    propertyMapping.defaultValue().isUserDefined()
                        ? propertyMapping.defaultValue()
                        : propertyValues.valueType().fallbackValue()
                )
            ));
            nodePropertyStores.put(nodeLabel, builder.build());
        });

        Map<RelationshipType, RelationshipPropertyStore> relationshipPropertyStores = new HashMap<>();
        relationshipProperties.forEach((relationshipType, propertyMap) -> {
            RelationshipPropertyStore.Builder builder = RelationshipPropertyStore.builder();
            propertyMap.forEach((propertyMapping, propertyValues) -> builder.putRelationshipProperty(
                propertyMapping.propertyKey(),
                RelationshipProperty.of(
                    propertyMapping.propertyKey(),
                    NumberType.FLOATING_POINT,
                    PropertyState.PERSISTENT,
                    propertyValues,
                    propertyMapping.defaultValue().isUserDefined()
                        ? propertyMapping.defaultValue()
                        : ValueType.fromNumberType(NumberType.FLOATING_POINT).fallbackValue(),
                    propertyMapping.aggregation()
                )
            ));
            relationshipPropertyStores.put(relationshipType, builder.build());
        });

        return constructor.construct(
            databaseId,
            nodes,
            nodePropertyStores,
            relationships,
            relationshipPropertyStores,
            concurrency,
            tracker
        );
    }

    public static CSRGraphStore of(
        NamedDatabaseId databaseId,
        HugeGraph graph,
        String relationshipTypeString,
        Optional<String> relationshipProperty,
        int concurrency,
        AllocationTracker tracker
    ) {
        Relationships relationships = graph.relationships();

        RelationshipType relationshipType = RelationshipType.of(relationshipTypeString);
        Map<RelationshipType, Relationships.Topology> topology = singletonMap(relationshipType, relationships.topology());

        Map<NodeLabel, NodePropertyStore> nodeProperties = constructNodePropertiesFromGraph(graph);

        Map<RelationshipType, RelationshipPropertyStore> relationshipProperties = constructRelationshipPropertiesFromGraph(
            graph,
            relationshipProperty,
            relationships,
            relationshipType
        );

        return new CSRGraphStore(databaseId, graph.idMap(), nodeProperties, topology, relationshipProperties, concurrency, tracker);
    }

    @NotNull
    private static Map<NodeLabel, NodePropertyStore> constructNodePropertiesFromGraph(HugeGraph graph) {
        Map<NodeLabel, NodePropertyStore> nodePropertyStores = new HashMap<>();
        NodePropertyStore.Builder nodePropertyStoreBuilder = NodePropertyStore.builder();
        graph
            .schema()
            .nodeSchema()
            .properties()
            .values()
            .stream()
            .flatMap(map -> map.values().stream())
            .forEach(propertySchema -> nodePropertyStoreBuilder.putIfAbsent(
                propertySchema.key(),
                NodeProperty.of(propertySchema.key(), propertySchema.state(), graph.nodeProperties(propertySchema.key()), propertySchema.defaultValue())
            ));

        nodePropertyStores.put(ALL_NODES, nodePropertyStoreBuilder.build());
        return nodePropertyStores;
    }

    @NotNull
    private static Map<RelationshipType, RelationshipPropertyStore> constructRelationshipPropertiesFromGraph(
        HugeGraph graph,
        Optional<String> relationshipProperty,
        Relationships relationships,
        RelationshipType relationshipType
    ) {
        Map<RelationshipType, RelationshipPropertyStore> relationshipProperties = Collections.emptyMap();
        if (relationshipProperty.isPresent() && relationships.properties().isPresent()) {
            Map<String, RelationshipPropertySchema> relationshipPropertySchemas = graph
                .schema()
                .relationshipSchema()
                .properties()
                .get(relationshipType);

            if (relationshipPropertySchemas.size() != 1) {
                throw new IllegalStateException(formatWithLocale(
                    "Relationship schema is expected to have exactly one property but had %s",
                    relationshipPropertySchemas.size()
                ));
            }

            RelationshipPropertySchema relationshipPropertySchema = relationshipPropertySchemas
                .values()
                .stream()
                .findFirst()
                .get();

            String propertyKey = relationshipProperty.get();
            relationshipProperties = singletonMap(
                relationshipType,
                RelationshipPropertyStore.builder().putIfAbsent(
                    propertyKey,
                    RelationshipProperty.of(
                        propertyKey,
                        NumberType.FLOATING_POINT,
                        relationshipPropertySchema.state(),
                        relationships.properties().get(),
                        relationshipPropertySchema.defaultValue(),
                        relationshipPropertySchema.aggregation())
                ).build()
            );
        }
        return relationshipProperties;
    }

    public CSRGraphStore(
        NamedDatabaseId databaseId,
        NodeMapping nodes,
        Map<NodeLabel, NodePropertyStore> nodeProperties,
        Map<RelationshipType, Relationships.Topology> relationships,
        Map<RelationshipType, RelationshipPropertyStore> relationshipProperties,
        int concurrency,
        AllocationTracker tracker
    ) {
        this.databaseId = databaseId;
        this.nodes = nodes;

        // make sure that the following maps are mutable
        this.nodeProperties = new HashMap<>(nodeProperties);
        this.relationships = new HashMap<>(relationships);
        this.relationshipProperties = new HashMap<>(relationshipProperties);

        this.concurrency = concurrency;
        this.createdGraphs = new HashSet<>();
        this.modificationTime = TimeUtil.now();
        this.tracker = tracker;
    }

    @Override
    public NamedDatabaseId databaseId() {
        return databaseId;
    }

    @Override
    public GraphSchema schema() {
        return GraphSchema.of(nodeSchema(), relationshipTypeSchema());
    }

    @Override
    public ZonedDateTime modificationTime() {
        return modificationTime;
    }

    @Override
    public NodeMapping nodes() {
        return this.nodes;
    }

    @Override
    public Set<NodeLabel> nodeLabels() {
        return nodes.availableNodeLabels();
    }

    @Override
    public Set<String> nodePropertyKeys(NodeLabel label) {
        return new HashSet<>(nodeProperties.getOrDefault(label, NodePropertyStore.empty()).keySet());
    }

    @Override
    public Map<NodeLabel, Set<String>> nodePropertyKeys() {
        return nodeLabels().stream().collect(Collectors.toMap(Function.identity(), this::nodePropertyKeys));
    }

    @Override
    public boolean hasNodeProperty(Collection<NodeLabel> labels, String propertyKey) {
        return labels
            .stream()
            .allMatch(label -> nodeProperties.containsKey(label) && nodeProperties.get(label).containsKey(propertyKey));
    }

    @Override
    public void addNodeProperty(
        NodeLabel nodeLabel,
        String propertyKey,
        NodeProperties propertyValues
    ) {
        if (!nodeLabels().contains(nodeLabel)) {
            throw new IllegalArgumentException(formatWithLocale(
                "Adding '%s.%s' to the graph store failed. Node label '%s' does not exist in the store. Available node labels: %s",
                nodeLabel.name, propertyKey, nodeLabel.name,
                StringJoining.join(nodeLabels().stream().map(NodeLabel::name))
            ));
        }
        updateGraphStore((graphStore) -> graphStore.nodeProperties.compute(nodeLabel, (k, nodePropertyStore) -> {
            NodePropertyStore.Builder storeBuilder = NodePropertyStore.builder();
            if (nodePropertyStore != null) {
                storeBuilder.from(nodePropertyStore);
            }
            return storeBuilder
                .putIfAbsent(propertyKey, NodeProperty.of(propertyKey, PropertyState.TRANSIENT, propertyValues))
                .build();
        }));
    }

    @Override
    public void removeNodeProperty(NodeLabel nodeLabel, String propertyKey) {
        updateGraphStore(graphStore -> {
            if (graphStore.nodeProperties.containsKey(nodeLabel)) {
                NodePropertyStore updatedNodePropertyStore = NodePropertyStore.builder()
                    .from(graphStore.nodeProperties.get(nodeLabel))
                    .removeProperty(propertyKey)
                    .build();

                if (updatedNodePropertyStore.isEmpty()) {
                    graphStore.nodeProperties.remove(nodeLabel);
                } else {
                    graphStore.nodeProperties.replace(nodeLabel, updatedNodePropertyStore);
                }
            }
        });
    }

    @Override
    public ValueType nodePropertyType(NodeLabel label, String propertyKey) {
        return nodeProperty(label, propertyKey).valueType();
    }

    @Override
    public PropertyState nodePropertyState(String propertyKey) {
        return nodeProperty(propertyKey).propertyState();
    }

    @Override
    public NodeProperties nodePropertyValues(String propertyKey) {
        return nodeProperty(propertyKey).values();
    }

    @Override
    public NodeProperties nodePropertyValues(NodeLabel label, String propertyKey) {
        return nodeProperty(label, propertyKey).values();
    }

    @Override
    public Set<RelationshipType> relationshipTypes() {
        return relationships.keySet();
    }

    @Override
    public boolean hasRelationshipType(RelationshipType relationshipType) {
        return relationships.containsKey(relationshipType);
    }

    @Override
    public long relationshipCount() {
        long sum = 0L;
        for (var topology : relationships.values()) {
            long elementCount = topology.elementCount();
            sum += elementCount;
        }
        return sum;
    }

    @Override
    public long relationshipCount(RelationshipType relationshipType) {
        return relationships.get(relationshipType).elementCount();
    }

    @Override
    public boolean hasRelationshipProperty(Collection<RelationshipType> relTypes, String propertyKey) {
        return relTypes
            .stream()
            .allMatch(relType -> relationshipProperties.containsKey(relType) && relationshipProperties
                .get(relType)
                .containsKey(propertyKey));
    }

    @Override
    public ValueType relationshipPropertyType(String propertyKey) {
        return relationshipProperties.values().stream()
            .filter(propertyStore -> propertyStore.containsKey(propertyKey))
            .map(propertyStore -> propertyStore.get(propertyKey).valueType())
            .findFirst()
            .orElse(ValueType.UNKNOWN);
    }

    @Override
    public Set<String> relationshipPropertyKeys() {
        return relationshipProperties
            .values()
            .stream()
            .flatMap(relationshipPropertyStore -> relationshipPropertyStore.keySet().stream())
            .collect(Collectors.toSet());
    }

    @Override
    public Set<String> relationshipPropertyKeys(RelationshipType relationshipType) {
        return relationshipProperties.getOrDefault(relationshipType, RelationshipPropertyStore.empty()).keySet();
    }

    @Override
    public void addRelationshipType(
        RelationshipType relationshipType,
        Optional<String> relationshipPropertyKey,
        Optional<NumberType> relationshipPropertyType,
        Relationships relationships
    ) {
        updateGraphStore(graphStore -> {
            if (!hasRelationshipType(relationshipType)) {
                graphStore.relationships.put(relationshipType, relationships.topology());

                if (relationshipPropertyKey.isPresent()
                    && relationshipPropertyType.isPresent()
                    && relationships.properties().isPresent()) {
                    addRelationshipProperty(
                        relationshipType,
                        relationshipPropertyKey.get(),
                        relationshipPropertyType.get(),
                        relationships.properties().get(),
                        graphStore
                    );
                }
            }
        });
    }

    @Override
    public DeletionResult deleteRelationships(RelationshipType relationshipType) {
        return DeletionResult.of(builder ->
            updateGraphStore(graphStore -> {
                builder.deletedRelationships(graphStore.relationships.get(relationshipType).elementCount());
                graphStore.relationshipProperties
                    .getOrDefault(relationshipType, RelationshipPropertyStore.empty())
                    .relationshipProperties()
                    .values()
                    .forEach(property -> builder.putDeletedProperty(property.key(), property.values().elementCount()));
                graphStore.relationships.remove(relationshipType);
                graphStore.relationshipProperties.remove(relationshipType);
            })
        );
    }

    @Override
    public CSRGraph getGraph(
        Collection<NodeLabel> nodeLabels,
        Collection<RelationshipType> relationshipTypes,
        Optional<String> maybeRelationshipProperty
    ) {
        validateInput(relationshipTypes, maybeRelationshipProperty);
        return createGraph(nodeLabels, relationshipTypes, maybeRelationshipProperty);
    }

    @Override
    public CSRGraph getUnion() {
        return UnionGraph.of(relationships
            .keySet()
            .stream()
            .flatMap(relationshipType -> {
                if (relationshipProperties.containsKey(relationshipType) && !relationshipProperties.get(relationshipType).isEmpty()) {
                    return relationshipProperties
                        .get(relationshipType)
                        .keySet()
                        .stream()
                        .map(propertyKey -> createGraph(nodeLabels(), relationshipType, Optional.of(propertyKey)));
                } else {
                    return Stream.of(createGraph(nodeLabels(), relationshipType, Optional.empty()));
                }
            })
            .collect(Collectors.toList()));
    }

    @Override
    public void canRelease(boolean canRelease) {
        createdGraphs.forEach(graph -> graph.canRelease(canRelease));
    }

    @Override
    public void release() {
        createdGraphs.forEach(Graph::release);
        releaseInternals();
    }

    private void releaseInternals() {
        var closeables = Stream.<AutoCloseable>builder();
        if (this.nodes instanceof AutoCloseable) {
            closeables.accept((AutoCloseable) this.nodes);
        }
        this.relationships.values().forEach(rel -> closeables.add(rel.list()).add(rel.offsets()));
        this.relationshipProperties.forEach((propertyName, properties) ->
            properties.values().forEach(prop -> closeables.add(prop.values().list()).add(prop.values().offsets()))
        );

        var errorWhileClosing = closeables.build().flatMap(closeable -> {
            try {
                closeable.close();
                return Stream.empty();
            } catch (Exception e) {
                return Stream.of(e);
            }
        }).reduce(null, ExceptionUtil::chain);
        if (errorWhileClosing != null) {
            ExceptionUtil.throwIfUnchecked(errorWhileClosing);
            throw new RuntimeException(errorWhileClosing);
        }
    }

    @Override
    public long nodeCount() {
        return nodes.nodeCount();
    }

    private synchronized void updateGraphStore(Consumer<CSRGraphStore> updateFunction) {
        updateFunction.accept(this);
        this.modificationTime = TimeUtil.now();
    }

    private NodeProperty nodeProperty(NodeLabel label, String propertyKey) {
        return this.nodeProperties.getOrDefault(label, NodePropertyStore.empty()).get(propertyKey);
    }

    private NodeProperty nodeProperty(String propertyKey) {
        if (nodes.availableNodeLabels().size() > 1) {
            var unionValues = new HashMap<NodeLabel, NodeProperties>();
            var unionOrigin = PropertyState.PERSISTENT;

            for (var labelAndPropertyStore : nodeProperties.entrySet()) {
                var nodeLabel = labelAndPropertyStore.getKey();
                var nodePropertyStore = labelAndPropertyStore.getValue();
                if (nodePropertyStore.containsKey(propertyKey)) {
                    var nodeProperty = nodePropertyStore.get(propertyKey);
                    unionValues.put(nodeLabel, nodeProperty.values());
                    unionOrigin = nodeProperty.propertyState();
                }
            }

            return NodeProperty.of(
                propertyKey,
                unionOrigin,
                new UnionNodeProperties(nodes, unionValues)
            );
        } else {
            return nodeProperties.get(nodes.availableNodeLabels().iterator().next()).get(propertyKey);
        }
    }

    private void addRelationshipProperty(
        RelationshipType relationshipType,
        String propertyKey,
        NumberType propertyType,
        Relationships.Properties properties,
        CSRGraphStore graphStore
    ) {
        graphStore.relationshipProperties.compute(relationshipType, (relType, propertyStore) -> {
            RelationshipPropertyStore.Builder builder = RelationshipPropertyStore.builder();
            if (propertyStore != null) {
                builder.from(propertyStore);
            }
            return builder.putIfAbsent(
                propertyKey,
                RelationshipProperty.of(
                    propertyKey,
                    propertyType,
                    PropertyState.TRANSIENT,
                    properties,
                    ValueType.fromNumberType(propertyType).fallbackValue(),
                    Aggregation.NONE
                )
            ).build();
        });
    }

    private CSRGraph createGraph(
        Collection<NodeLabel> nodeLabels,
        RelationshipType relationshipType,
        Optional<String> maybeRelationshipProperty
    ) {
        return createGraph(nodeLabels, singletonList(relationshipType), maybeRelationshipProperty);
    }

    private CSRGraph createGraph(
        Collection<NodeLabel> filteredLabels,
        Collection<RelationshipType> relationshipTypes,
        Optional<String> maybeRelationshipProperty
    ) {
        boolean loadAllNodes = filteredLabels.containsAll(nodeLabels());

        Optional<NodeMapping> filteredNodes = loadAllNodes || nodes.containsOnlyAllNodesLabel()
            ? Optional.empty()
            : Optional.of(nodes.withFilteredLabels(filteredLabels, concurrency));

        List<CSRGraph> filteredGraphs = relationships.entrySet().stream()
            .filter(relTypeAndCSR -> relationshipTypes.contains(relTypeAndCSR.getKey()))
            .map(relTypeAndCSR -> {
                Map<String, NodeProperties> filteredNodeProperties = filterNodeProperties(filteredLabels);

                RelationshipType relType = relTypeAndCSR.getKey();
                var graphSchema = GraphSchema.of(
                    schema().nodeSchema(),
                    schema()
                        .relationshipSchema()
                        .singleTypeAndProperty(relTypeAndCSR.getKey(), maybeRelationshipProperty)
                );

                HugeGraph initialGraph = HugeGraph.create(
                    nodes,
                    graphSchema,
                    filteredNodeProperties,
                    relTypeAndCSR.getValue(),
                    maybeRelationshipProperty.map(propertyKey -> relationshipProperties
                        .get(relType)
                        .get(propertyKey).values()),
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

    private Map<String, NodeProperties> filterNodeProperties(Collection<NodeLabel> labels) {
        if (this.nodeProperties.isEmpty()) {
            return Collections.emptyMap();
        }
        if (labels.size() == 1 || nodes.containsOnlyAllNodesLabel()) {
            return this.nodeProperties.getOrDefault(labels.iterator().next(), NodePropertyStore.empty()).nodePropertyValues();
        }

        Map<String, Map<NodeLabel, NodeProperties>> invertedNodeProperties = new HashMap<>();
        nodeProperties
            .entrySet()
            .stream()
            .filter(entry -> labels.contains(entry.getKey()) || labels.contains(ALL_NODES))
            .forEach(entry -> entry.getValue().nodeProperties()
                .forEach((propertyKey, nodeProperty) -> invertedNodeProperties
                    .computeIfAbsent(
                        propertyKey,
                        ignored -> new HashMap<>()
                    )
                    .put(entry.getKey(), nodeProperty.values())
                ));

        return invertedNodeProperties
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Entry::getKey,
                entry -> new UnionNodeProperties(nodes, entry.getValue())
            ));
    }

    private void validateInput(Collection<RelationshipType> relationshipTypes, Optional<String> maybeRelationshipProperty) {
        if (relationshipTypes.isEmpty()) {
            throw new IllegalArgumentException(formatWithLocale(
                "The parameter '%s' should not be empty. Use '*' to load all relationship types.",
                ProcedureConstants.RELATIONSHIP_TYPES
            ));
        }

        relationshipTypes.forEach(relationshipType -> {
            if (!relationships.containsKey(relationshipType)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "No relationships have been loaded for relationship type '%s'",
                    relationshipType
                ));
            }

            maybeRelationshipProperty.ifPresent(relationshipProperty -> {
                if (!hasRelationshipProperty(singletonList(relationshipType), relationshipProperty)) {
                    throw new IllegalArgumentException(formatWithLocale(
                        "Property '%s' does not exist for relationships with type '%s'.",
                        maybeRelationshipProperty.get(),
                        relationshipType
                    ));
                }
            });
        });
    }

    private NodeSchema nodeSchema() {
        NodeSchema.Builder nodePropsBuilder = NodeSchema.builder();

        nodeProperties.forEach((label, propertyStore) ->
            propertyStore.nodeProperties().forEach((propertyName, nodeProperty) ->
                nodePropsBuilder.addProperty(
                    label,
                    propertyName,
                    nodeProperty.propertySchema())
            ));

        for (NodeLabel nodeLabel : nodeLabels()) {
            nodePropsBuilder.addLabel(nodeLabel);
        }
        return nodePropsBuilder.build();
    }

    private RelationshipSchema relationshipTypeSchema() {
        RelationshipSchema.Builder relationshipPropsBuilder = RelationshipSchema.builder();

        relationshipProperties.forEach((type, propertyStore) ->
            propertyStore.relationshipProperties().forEach((propertyName, relationshipProperty) ->
                relationshipPropsBuilder.addProperty(
                    type,
                    propertyName,
                    relationshipProperty.propertySchema()
                )
            )
        );

        for (RelationshipType type : relationshipTypes()) {
            relationshipPropsBuilder.addRelationshipType(type);
        }
        return relationshipPropsBuilder.build();
    }

}



