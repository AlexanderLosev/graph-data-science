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
package org.neo4j.gds.ml.linkmodels.metrics;

import org.neo4j.gds.ml.linkmodels.LinkPredictionPredictConsumer;
import org.neo4j.gds.ml.nodemodels.metrics.F1Score;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;

public enum LinkMetric {
    F1_SCORE;

    public double compute(
        HugeLongArray targets,
        HugeLongArray predictions
    ) {
        return new F1Score(LinkPredictionPredictConsumer.PREDICTED_LINK).compute(targets, predictions);
    }
}