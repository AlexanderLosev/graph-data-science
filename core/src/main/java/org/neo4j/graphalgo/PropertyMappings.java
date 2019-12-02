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

package org.neo4j.graphalgo;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.graphalgo.core.DeduplicationStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class PropertyMappings implements Iterable<PropertyMapping> {

    private final PropertyMapping[] mappings;

    public static final PropertyMappings EMPTY = new PropertyMappings();

    public static PropertyMappings of(PropertyMapping... mappings) {
        if (mappings == null) {
            mappings = new PropertyMapping[0];
        }
        return new PropertyMappings(mappings);
    }

    public static PropertyMappings fromObject(Object relPropertyMapping) {
        if (relPropertyMapping instanceof PropertyMappings) {
            return (PropertyMappings) relPropertyMapping;
        }
        if (relPropertyMapping instanceof String) {
            String propertyMapping = (String) relPropertyMapping;
            return fromObject(Collections.singletonMap(propertyMapping, propertyMapping));
        } else if (relPropertyMapping instanceof List) {
            Builder builder = new Builder();
            for (Object mapping : (List<?>) relPropertyMapping) {
                builder.addAllMappings(fromObject(mapping).mappings);
            }
            return builder.build();
        } else if (relPropertyMapping instanceof Map) {
            Builder builder = new Builder();
            ((Map<String, Object>) relPropertyMapping).forEach((key, spec) -> {
                PropertyMapping propertyMapping = PropertyMapping.fromObject(key, spec);
                builder.addMapping(propertyMapping);
            });
            return builder.build();
        } else {
            throw new IllegalArgumentException(String.format(
                    "Expected String or Map for property mappings. Got %s.",
                    relPropertyMapping.getClass().getSimpleName()));
        }
    }

    private PropertyMappings(PropertyMapping... mappings) {
        this.mappings = mappings;
    }

    public Stream<PropertyMapping> stream() {
        return Arrays.stream(mappings);
    }

    public Optional<PropertyMapping> head() {
        return stream().findFirst();
    }

    public Stream<Pair<Integer, PropertyMapping>> enumerate() {
        return IntStream.range(0, mappings.length).mapToObj(idx -> Pair.of(idx, mappings[idx]));
    }

    @Override
    public Iterator<PropertyMapping> iterator() {
        return stream().iterator();
    }

    @Deprecated
    public Optional<Double> defaultWeight() {
        return mappings.length == 0 ? Optional.empty() : Optional.of(mappings[0].defaultValue());
    }

    public boolean hasMappings() {
        return mappings.length > 0;
    }

    public int numberOfMappings() {
        return mappings.length;
    }

    public boolean atLeastOneExists() {
        return stream().anyMatch(PropertyMapping::exists);
    }

    public int[] allPropertyKeyIds() {
        return stream()
                .mapToInt(PropertyMapping::propertyKeyId)
                .toArray();
    }

    public double[] allDefaultWeights() {
        return stream()
                .mapToDouble(PropertyMapping::defaultValue)
                .toArray();
    }

    public Map<String, Object> toObject(boolean includeAggregation) {
        return stream()
            .map(mapping -> mapping.toObject(includeAggregation))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public PropertyMappings mergeWith(PropertyMappings other) {
        if (!hasMappings()) {
            return other;
        }
        if (!other.hasMappings()) {
            return this;
        }
        Builder builder = new Builder();
        builder.addAllMappings(Stream.concat(stream(), other.stream()).distinct());
        return builder.build();
    }

    public static final class Builder {
        private final List<PropertyMapping> mappings;

        public Builder() {
            mappings = new ArrayList<>();
        }

        public Builder addMapping(PropertyMapping mapping) {
            Objects.requireNonNull(mapping, "Given PropertyMapping must not be null.");
            mappings.add(mapping);
            return this;
        }

        public Builder addOptionalMapping(PropertyMapping mapping) {
            Objects.requireNonNull(mapping, "Given PropertyMapping must not be null.");
            if (mapping.hasValidName()) {
                mappings.add(mapping);
            }
            return this;
        }

        public Builder addAllMappings(PropertyMapping... propertyMappings) {
            return addAllMappings(Arrays.stream(propertyMappings));
        }

        public Builder addAllOptionalMappings(PropertyMapping... propertyMappings) {
            return addAllOptionalMappings(Arrays.stream(propertyMappings));
        }

        public Builder addAllMappings(Stream<PropertyMapping> propertyMappings) {
            propertyMappings.forEach(this::addMapping);
            return this;
        }

        public Builder addAllOptionalMappings(Stream<PropertyMapping> propertyMappings) {
            propertyMappings.forEach(this::addOptionalMapping);
            return this;
        }

        public PropertyMappings build() {
            long noneStrategyCount = this.mappings.stream()
                    .filter(d -> d.deduplicationStrategy == DeduplicationStrategy.NONE)
                    .count();

            if (noneStrategyCount > 0 && noneStrategyCount < this.mappings.size()) {
                throw new IllegalArgumentException(
                        "Conflicting relationship property deduplication strategies, it is not allowed to mix `NONE` with aggregations.");
            }

            PropertyMapping[] mappings = this.mappings.toArray(new PropertyMapping[0]);

            return of(mappings);
        }
    }
}
