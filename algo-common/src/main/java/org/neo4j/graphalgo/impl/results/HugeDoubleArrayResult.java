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
package org.neo4j.graphalgo.impl.results;

import org.neo4j.graphalgo.core.utils.paged.HugeDoubleArray;
import org.neo4j.graphalgo.core.write.Exporter;
import org.neo4j.graphalgo.core.write.PropertyTranslator;

import java.util.function.DoubleUnaryOperator;

public final class HugeDoubleArrayResult implements CentralityResult {
    private final HugeDoubleArray result;

    public HugeDoubleArrayResult(HugeDoubleArray result) {
        this.result = result;
    }

    public HugeDoubleArray array() {
        return this.result;
    }

    @Override
    public void export(
            final String propertyName, final Exporter exporter) {
        exporter.write(
                propertyName,
                result,
                HugeDoubleArray.Translator.INSTANCE);
    }

    @Override
    public void export(String propertyName, Exporter exporter, DoubleUnaryOperator normalizationFunction) {
        exporter.write(
                propertyName,
                result,
                new MapTranslator(normalizationFunction));
    }

    public static class MapTranslator implements PropertyTranslator.OfDouble<HugeDoubleArray> {

        private DoubleUnaryOperator fn;

        public MapTranslator(DoubleUnaryOperator fn) {
            this.fn = fn;
        }

        @Override
        public double toDouble(final HugeDoubleArray data, final long nodeId) {
            return fn.applyAsDouble(data.get(nodeId));
        }
    }

    @Override
    public final double score(final long nodeId) {
        return result.get(nodeId);
    }

    @Override
    public double score(final int nodeId) {
        return result.get(nodeId);
    }
}
