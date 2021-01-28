/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.gds.ml.linkmodels;

import org.immutables.value.Value;
import org.neo4j.gds.ml.linkmodels.metrics.LinkMetric;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.config.AlgoBaseConfig;
import org.neo4j.graphalgo.config.FeaturePropertiesConfig;
import org.neo4j.graphalgo.config.ModelConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ValueClass
@Configuration
public interface LinkPredictionTrainConfig extends AlgoBaseConfig, FeaturePropertiesConfig, ModelConfig {

    Optional<Long> randomSeed();

    @Configuration.IntegerRange(min = 2)
    int validationFolds();

    List<Map<String, Object>> params();

    @Configuration.ConvertWith("org.neo4j.graphalgo.RelationshipType#of")
    @Configuration.ToMapValue("org.neo4j.graphalgo.RelationshipType#toString")
    RelationshipType trainRelationshipType();

    @Configuration.ConvertWith("org.neo4j.graphalgo.RelationshipType#of")
    @Configuration.ToMapValue("org.neo4j.graphalgo.RelationshipType#toString")
    RelationshipType testRelationshipType();

    @Configuration.Ignore
    @Value.Default
    default List<LinkMetric> metrics() {
        return List.of(LinkMetric.F1_SCORE);
    }
}
