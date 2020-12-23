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
package org.neo4j.graphalgo.api.schema;

import org.neo4j.graphalgo.ElementIdentifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public interface ElementSchema<SELF extends ElementSchema<SELF, I, PROPERTY_SCHEMA>, I extends ElementIdentifier, PROPERTY_SCHEMA extends PropertySchema> {

    Map<I, Map<String, PROPERTY_SCHEMA>> properties();

    SELF filter(Set<I> elementIdentifieresToKeep);

    SELF union(SELF other);

    default Map<String, Object> toMap() {
        return properties().entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().name,
            entry -> entry
                .getValue()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    innerEntry -> GraphSchema.forPropertySchema(innerEntry.getValue()))
                )
        ));
    }

    default Map<I, Map<String, PROPERTY_SCHEMA>> filterProperties(Set<I> identifiersToKeep) {
        return properties()
            .entrySet()
            .stream()
            .filter(entry -> identifiersToKeep.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    default Map<String, PROPERTY_SCHEMA> unionProperties() {
        return properties()
            .values()
            .stream()
            .flatMap(e -> e.entrySet().stream())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (leftSchema, rightSchema) -> {
                    if (leftSchema.valueType() != rightSchema.valueType()) {
                        throw new IllegalArgumentException(formatWithLocale(
                            "Combining schema entries with value type %s and %s is not supported.",
                            leftSchema.valueType(),
                            rightSchema.valueType()
                        ));
                    } else {
                        return leftSchema;
                    }
                }
            ));
    }

    /**
     * For internal use only!
     */
    default Map<I, Map<String, PROPERTY_SCHEMA>> unionSchema(Map<I, Map<String, PROPERTY_SCHEMA>> rightProperties) {
        return Stream.concat(
            properties().entrySet().stream(),
            rightProperties.entrySet().stream()
        ).collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (left, right) -> Stream.concat(
                left.entrySet().stream(),
                right.entrySet().stream()
            ).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (leftType, rightType) -> {
                    if (leftType.valueType() != rightType.valueType()) {
                        throw new IllegalArgumentException(formatWithLocale(
                            "Combining schema entries with value type %s and %s is not supported.",
                            left.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().valueType())),
                            right.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().valueType()))
                        ));
                    } else {
                        return leftType;
                    }
                }
            ))
        ));
    }
}
