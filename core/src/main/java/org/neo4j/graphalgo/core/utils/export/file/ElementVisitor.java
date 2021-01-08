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
package org.neo4j.graphalgo.core.utils.export.file;

import org.neo4j.graphalgo.ElementIdentifier;
import org.neo4j.graphalgo.api.schema.ElementSchema;
import org.neo4j.graphalgo.api.schema.PropertySchema;
import org.neo4j.internal.batchimport.input.InputEntityVisitor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class ElementVisitor<
    ELEMENT_SCHEMA extends ElementSchema<ELEMENT_SCHEMA, I, PROPERTY_SCHEMA>,
    I extends ElementIdentifier,
    PROPERTY_SCHEMA extends PropertySchema> extends InputEntityVisitor.Adapter {

    final ElementSchema<ELEMENT_SCHEMA, I, PROPERTY_SCHEMA> elementSchema;

    private final Object[] currentProperties;
    private final Map<String, List<PROPERTY_SCHEMA>> propertySchemas;
    private final Map<String, Integer> propertyKeyPositions;

    ElementVisitor(ElementSchema<ELEMENT_SCHEMA, I, PROPERTY_SCHEMA> elementSchema) {
        this.elementSchema = elementSchema;
        this.propertySchemas = new HashMap<>();

        this.propertyKeyPositions = new HashMap<>();
        var allProperties = elementSchema.allProperties();
        var i = 0;
        for (String propertyKey : allProperties) {
            propertyKeyPositions.put(propertyKey, i++);
        }

        this.currentProperties = new Object[propertyKeyPositions.size()];
    }

    protected abstract void exportElement();

    abstract String elementIdentifier();

    abstract List<PROPERTY_SCHEMA> getPropertySchema();

    abstract void reset();

    @Override
    public boolean property(String key, Object value) {
        var propertyPosition = propertyKeyPositions.get(key);
        currentProperties[propertyPosition] = value;
        return true;
    }

    @Override
    public void endOfEntity() {
        // Check if we encounter a new label combination
        if (!propertySchemas.containsKey(elementIdentifier())) {
            calculateElementSchema();
        }

        // do the export
        exportElement();

        // reset
        reset();
        Arrays.fill(currentProperties, null);
    }

    protected void forEachProperty(PropertyConsumer propertyConsumer) {
        for (PROPERTY_SCHEMA propertySchema : propertySchemas.get(elementIdentifier())) {
            var propertyPosition = propertyKeyPositions.get(propertySchema.key());
            var propertyValue = currentProperties[propertyPosition];
            propertyConsumer.accept(propertySchema.key(), propertyValue, propertySchema.valueType());
        }
    }

    private void calculateElementSchema() {
        var propertySchema = getPropertySchema();
        propertySchema.sort(Comparator.comparing(PropertySchema::key));
        propertySchemas.put(elementIdentifier(), propertySchema);
    }

}
