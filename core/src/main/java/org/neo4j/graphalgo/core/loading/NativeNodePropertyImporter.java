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

import com.carrotsearch.hppc.procedures.LongObjectProcedure;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.PropertyMappings;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.neo4j.graphalgo.core.loading.NodesBatchBuffer.ANY_LABEL;
import static org.neo4j.graphalgo.core.loading.NodesBatchBuffer.IGNORE_LABEL;

public final class NativeNodePropertyImporter {

    private final Map<NodeLabel, Map<PropertyMapping, NodePropertiesBuilder>> buildersByIdentifier;
    private final Map<Long, Map<Integer, List<NodePropertiesBuilder>>> buildersByLabelIdAndPropertyToken;
    private final boolean containsAnyLabelProjection;

    public static Builder builder() {
        return new Builder();
    }

    private NativeNodePropertyImporter(
        Map<NodeLabel, Map<PropertyMapping, NodePropertiesBuilder>> buildersByIdentifier,
        Map<Long, Map<Integer, List<NodePropertiesBuilder>>> buildersByLabelIdAndPropertyToken,
        boolean containsAnyLabelProjection
    ) {
        this.buildersByIdentifier = buildersByIdentifier;
        this.buildersByLabelIdAndPropertyToken = buildersByLabelIdAndPropertyToken;
        this.containsAnyLabelProjection = containsAnyLabelProjection;
    }

    int importProperties(
        long nodeId,
        long neoNodeId,
        long[] labelIds,
        long propertiesReference,
        CursorFactory cursors,
        Read read
    ) {
        try (PropertyCursor pc = cursors.allocatePropertyCursor()) {
            read.nodeProperties(neoNodeId, propertiesReference, pc);
            int nodePropertiesRead = 0;
            while (pc.next()) {
                nodePropertiesRead += importProperty(nodeId, labelIds, pc);
            }
            return nodePropertiesRead;
        }
    }

    public Map<NodeLabel, Map<PropertyMapping, NodeProperties>> result() {
        return buildersByIdentifier
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    builderEntry -> builderEntry.getValue().build()
                ))
            ));
    }

    private int importProperty(long nodeId, long[] labels, PropertyCursor propertyCursor) {
        int propertiesImported = 0;
        int propertyKey = propertyCursor.propertyKey();

        for (long label : labels) {
            if (label == IGNORE_LABEL || label == ANY_LABEL) {
                continue;
            }

            if (buildersByLabelIdAndPropertyToken.containsKey(label)) {
                propertiesImported += setPropertyValue(
                    nodeId,
                    propertyCursor,
                    propertyKey,
                    buildersByLabelIdAndPropertyToken.get(label)
                );
            }
        }

        if (containsAnyLabelProjection) {
            propertiesImported += setPropertyValue(nodeId, propertyCursor, propertyKey, buildersByLabelIdAndPropertyToken.get((long) ANY_LABEL));
        }

        return propertiesImported;
    }

    private int setPropertyValue(
        long nodeId,
        PropertyCursor propertyCursor,
        int propertyToken,
        Map<Integer, List<NodePropertiesBuilder>> buildersByPropertyId
    ) {
        int propertiesImported = 0;
        if (buildersByPropertyId.containsKey(propertyToken)) {
            List<NodePropertiesBuilder> builders = buildersByPropertyId.get(propertyToken);
            Value value = propertyCursor.propertyValue();

            if (value instanceof NumberValue) {
                for (NodePropertiesBuilder builder : builders) {
                    builder.set(nodeId, ((NumberValue) value).doubleValue());
                    propertiesImported++;
                }
            } else if (!Values.NO_VALUE.equals(value)) {
                throw new IllegalArgumentException(String.format(
                    "Unsupported type [%s] of value %s. Please use a numeric property.",
                    value.valueGroup(),
                    value
                ));
            }
        }

        return propertiesImported;
    }

    public static final class Builder {
        private long nodeCount;
        private Map<NodeLabel, PropertyMappings> propertyMappingsByLabel;
        private GraphDimensions dimensions;
        private int concurrency;
        private AllocationTracker tracker = AllocationTracker.EMPTY;


        private Builder() {
        }

        public Builder nodeCount(long nodeCount) {
            this.nodeCount = nodeCount;
            return this;
        }

        public Builder propertyMappings(Map<NodeLabel, PropertyMappings> propertyMappingsByLabel) {
            this.propertyMappingsByLabel = propertyMappingsByLabel;
            return this;
        }

        public Builder dimensions(GraphDimensions dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public Builder tracker(AllocationTracker tracker) {
            this.tracker = tracker;
            return this;
        }

        public NativeNodePropertyImporter build() {
            Map<NodeLabel, Map<PropertyMapping, NodePropertiesBuilder>> nodePropertyBuilders = initializeNodePropertyBuilders();
            Map<Long, Map<Integer, List<NodePropertiesBuilder>>> buildersByLabelIdAndPropertyId =
                buildersByLabelIdAndPropertyId(nodePropertyBuilders);
            return new NativeNodePropertyImporter(
                nodePropertyBuilders,
                buildersByLabelIdAndPropertyId,
                buildersByLabelIdAndPropertyId.containsKey((long) ANY_LABEL)
            );
        }

        private Map<NodeLabel, Map<PropertyMapping, NodePropertiesBuilder>> initializeNodePropertyBuilders() {
            Map<NodeLabel, Map<PropertyMapping, NodePropertiesBuilder>> builders = new HashMap<>();
            propertyMappingsByLabel.forEach((nodeLabel, propertyMappings) -> {
                if (propertyMappings.numberOfMappings() > 0) {
                    builders.putIfAbsent(nodeLabel, new HashMap<>());
                    for (PropertyMapping propertyMapping : propertyMappings) {
                        NodePropertiesBuilder builder = NodePropertiesBuilder.of(
                            nodeCount,
                            tracker,
                            propertyMapping.defaultValue(),
                            dimensions.nodePropertyTokens().get(propertyMapping.neoPropertyKey()),
                            propertyMapping.propertyKey(),
                            concurrency
                        );
                        builders.get(nodeLabel).put(propertyMapping, builder);
                    }
                }
            });
            return builders;
        }

        private Map<Long, Map<Integer, List<NodePropertiesBuilder>>> buildersByLabelIdAndPropertyId(Map<NodeLabel, Map<PropertyMapping, NodePropertiesBuilder>> buildersByIdentifier) {
            Map<NodeLabel, Long> inverseIdentifierIdMapping = inverseIdentifierIdMapping();

            Map<Long, Map<Integer, List<NodePropertiesBuilder>>> buildersByLabelIdAndPropertyId = new HashMap<>();
            buildersByIdentifier.forEach((labelIdentifier, builders) -> {
                long labelId = inverseIdentifierIdMapping.get(labelIdentifier);

                Map<Integer, List<NodePropertiesBuilder>> buildersByPropertyId =
                    buildersByLabelIdAndPropertyId.computeIfAbsent(labelId, (ignore) -> new HashMap<>());

                builders.forEach((propertyMapping, builder) -> {
                    int propertyId = dimensions.nodePropertyTokens().get(propertyMapping.neoPropertyKey());

                    List<NodePropertiesBuilder> builderList = buildersByPropertyId.computeIfAbsent(
                        propertyId,
                        (ignore) -> new ArrayList<>()
                    );

                    builderList.add(builder);
                });
            });

            return buildersByLabelIdAndPropertyId;
        }

        private Map<NodeLabel, Long> inverseIdentifierIdMapping() {
            HashMap<NodeLabel, Long> inverseLabelIdentifierMapping = new HashMap<>();

            LongObjectProcedure<List<NodeLabel>> listLongObjectProcedure = (labelId, elementIdentifiers) -> {
                elementIdentifiers.forEach(identifier -> inverseLabelIdentifierMapping.put(identifier, labelId));
            };

            dimensions.labelTokenNodeLabelMapping().forEach(listLongObjectProcedure);

            return inverseLabelIdentifierMapping;
        }

    }


}
