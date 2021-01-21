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
package org.neo4j.gds.ml.nodemodels.metrics;

import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.openjdk.jol.util.Multiset;

public class F1Weighted implements Metric.MetricStrategy {

    @Override
    public double compute(
        HugeLongArray targets,
        HugeLongArray predictions,
        HugeLongArray globalTargets
    ) {
        if (targets.size() == 0) {
            return 0.0;
        }
        var targetCounts = new Multiset<Long>();
        for (long offset = 0; offset < globalTargets.size(); offset++) {
            targetCounts.add(globalTargets.get(offset));
        }

        var distinctTargets = targetCounts.keys().stream();
        var weightedScores = distinctTargets
            .mapToDouble(target -> {
                var weight = targetCounts.count(target);
                return weight * new F1Score(target).compute(targets, predictions);
            });
        return weightedScores.sum() / targets.size();
    }
}
