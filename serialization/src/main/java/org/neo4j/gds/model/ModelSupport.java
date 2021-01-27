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
package org.neo4j.gds.model;

import org.neo4j.gds.embeddings.graphsage.algo.GraphSage;
import org.neo4j.graphalgo.utils.StringJoining;

import java.util.Set;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public final class ModelSupport {
    private static final Set<String> SUPPORTED_TYPES = Set.of(GraphSage.MODEL_TYPE);

    public static void validateAlgoType(String algoType) {
        if (!SUPPORTED_TYPES.contains(algoType)) {
            throw new IllegalArgumentException(formatWithLocale(
                "Unknown model type '%s', supported model types are: %.",
                algoType,
                StringJoining.join(SUPPORTED_TYPES)
            ));
        }
    }

    public static <R, E extends Exception> R onValidAlgoType(
        String algoType,
        SupportedModelVisitor<R, E> visitor
    ) throws E {
        if (!SUPPORTED_TYPES.contains(algoType)) {
            throw new IllegalArgumentException(formatWithLocale(
                "Unknown model type '%s', supported model types are: %.",
                algoType,
                StringJoining.join(SUPPORTED_TYPES)
            ));
        }
        return visitor.graphSage();
    }

    public interface SupportedModelVisitor<R, E extends Exception> {
        R graphSage() throws E;
    }

    private ModelSupport() {
        throw new UnsupportedOperationException("No instances");
    }
}
