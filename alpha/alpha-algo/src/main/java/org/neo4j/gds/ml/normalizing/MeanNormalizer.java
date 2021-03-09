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
package org.neo4j.gds.ml.normalizing;

import org.neo4j.graphalgo.api.NodeProperties;

final class MeanNormalizer implements Normalizer {

    private final NodeProperties properties;
    final double avg;
    final double maxMinDiff;

    private MeanNormalizer(NodeProperties properties, double avg, double maxMinDiff) {
        this.properties = properties;
        this.avg = avg;
        this.maxMinDiff = maxMinDiff;
    }

    static MeanNormalizer create(NodeProperties properties, long nodeCount) {
        if (nodeCount == 0) {
            return new MeanNormalizer(properties, 0, 0);
        }

        var max = Double.MIN_VALUE;
        var min = Double.MAX_VALUE;
        var sum = 0D;

        for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
            var propertyValue = properties.doubleValue(nodeId);
            sum += propertyValue;
            if (propertyValue < min) {
                min = propertyValue;
            }
            if (propertyValue > max) {
                max = propertyValue;
            }
        }

        return new MeanNormalizer(properties, sum / nodeCount, max - min);
    }

    @Override
    public double normalize(long nodeId) {
        if (Math.abs(maxMinDiff) < 1e-15) {
            return 0D;
        }
        return (properties.doubleValue(nodeId) - avg) / maxMinDiff;
    }

}
