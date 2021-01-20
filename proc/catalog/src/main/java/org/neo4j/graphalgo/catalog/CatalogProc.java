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
package org.neo4j.graphalgo.catalog;

import org.neo4j.graphalgo.BaseProc;
import org.neo4j.graphalgo.core.CypherMapWrapper;

abstract class CatalogProc extends BaseProc {
    private static final String DEGREE_DISTRIBUTION_FIELD_NAME = "degreeDistribution";

    boolean computeHistogram() {
        if (callContext == null) {
            // treat an absent callContext as YIELD <*>.
            // It is only null if we've been called as a DBMS procedure.
            // We are not DBMS procedure so
            //   This Should Never Happen™
            // unless we have specified `mode=DBMS` on the @Procedure,
            // but setting either this or WRITE is the only way to
            // make sure that we're not being optimized away.
            // tl;dr: callContext could be null now
            return true;
        }
        return callContext.outputFields().anyMatch(DEGREE_DISTRIBUTION_FIELD_NAME::equals);
    }

    void validateGraphName(String graphName) {
        CypherMapWrapper.failOnBlank("graphName", graphName);
    }
}
