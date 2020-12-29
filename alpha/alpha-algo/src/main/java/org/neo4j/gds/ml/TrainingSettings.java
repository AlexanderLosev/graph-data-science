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
package org.neo4j.gds.ml;

import org.immutables.value.Value;
import org.neo4j.gds.embeddings.graphsage.AdamOptimizer;
import org.neo4j.gds.embeddings.graphsage.ddl4j.functions.Weights;
import org.neo4j.gds.embeddings.graphsage.ddl4j.tensor.Tensor;
import org.neo4j.graphalgo.annotation.ValueClass;

import java.util.List;

@ValueClass
public interface TrainingSettings {

    @Value.Default
    default int batchSize() {
        return 100;
    }
    @Value.Default
    default int minIterations() {
        return 1;
    }
    @Value.Default
    default int maxStreakCount() {
        return 1;
    }
    @Value.Default
    default int maxIterations() {
        return 100;
    }
    @Value.Default
    default int windowSize() {
        return 1;
    }
    @Value.Default
    default double tolerance() {
        return 1e-3;
    }

    default Updater updater(List<Weights<? extends Tensor<?>>> weights) {
        AdamOptimizer adamOptimizer = new AdamOptimizer(weights);
        return adamOptimizer::update;
    }

    default TrainingStopper stopper() {
        //TODO: move these to configuration?
        return new StreakStopper(minIterations(), maxStreakCount(), maxIterations(), windowSize(), tolerance());
    }

    default boolean sharedUpdater() {
        return false;
    }

    default BatchQueue batchQueue(long nodeCount) {
        return new BatchQueue(nodeCount, batchSize());
    }
}
