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

package org.neo4j.graphalgo.impl.generator;

import java.util.function.LongUnaryOperator;

import static org.neo4j.graphalgo.impl.generator.DistributionHelper.gauseanSample;
import static org.neo4j.graphalgo.impl.generator.DistributionHelper.powerLawSample;
import static org.neo4j.graphalgo.impl.generator.DistributionHelper.uniformSample;

public enum RelationshipDistribution {
    UNIFORM {
        @Override
        public LongUnaryOperator degreeProducer(long nodeCount, long averageDegree) {
            return (ignore) -> averageDegree;
        }

        @Override
        public LongUnaryOperator relationshipProducer(long nodeCount, long averageDegree) {
            return (ignore) -> uniformSample(nodeCount);
        }
    },
    RANDOM {
        @Override
        public LongUnaryOperator degreeProducer(long nodeCount, long averageDegree) {
            long stdDev = averageDegree / 2;
            return (ignore) -> gauseanSample(nodeCount, averageDegree, stdDev);
        }

        @Override
        public LongUnaryOperator relationshipProducer(long nodeCount, long averageDegree) {
            return (ignore) -> uniformSample(nodeCount);

        }
    },
    POWER_LAW {
        @Override
        public LongUnaryOperator degreeProducer(long nodeCount, long averageDegree) {
            long stdDev = averageDegree / 2;
            return (ignore) -> gauseanSample(nodeCount, averageDegree, stdDev);
        }

        @Override
        public LongUnaryOperator relationshipProducer(long nodeCount, long averageDegree) {
            long min = 1;
            double gamma = 1 + 1.0 / averageDegree;
            return (ignore) -> powerLawSample(min, nodeCount - 1, gamma);
        }
    };

    abstract LongUnaryOperator degreeProducer(long nodeCount, long averageDegree);
    abstract LongUnaryOperator relationshipProducer(long nodeCount, long averageDegree);
}
