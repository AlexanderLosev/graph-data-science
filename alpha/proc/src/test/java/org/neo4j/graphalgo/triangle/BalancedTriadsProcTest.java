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
package org.neo4j.graphalgo.triangle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.Projection;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.impl.triangle.BalancedTriads;
import org.neo4j.graphalgo.impl.triangle.BalancedTriadsConfig;
import org.neo4j.graphalgo.impl.triangle.TriangleConfig;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

class BalancedTriadsProcTest extends TriangleBaseProcTest<BalancedTriads, BalancedTriads, BalancedTriadsConfig> {

    @Override
    TriangleBaseProc<BalancedTriads, BalancedTriads, BalancedTriadsConfig> newInstance() {
        return new BalancedTriadsProc();
    }

    @Override
    BalancedTriadsConfig newConfig() {
        return BalancedTriadsConfig.of(
            getUsername(),
            Optional.empty(),
            Optional.empty(),
            CypherMapWrapper.empty()
        );
    }

    @Override
    String dbCypher() {
        return "CREATE " +
            "  (a:Node {name: 'a'})" + // center node
            ", (b:Node {name: 'b'})" +
            ", (c:Node {name: 'c'})" +
            ", (d:Node {name: 'd'})" +
            ", (e:Node {name: 'e'})" +
            ", (f:Node {name: 'f'})" +
            ", (g:Node {name: 'g'})" +

            ", (a)-[:TYPE {w: 1.0}]->(b)" +
            ", (a)-[:TYPE {w: -1.0}]->(c)" +
            ", (a)-[:TYPE {w: 1.0}]->(d)" +
            ", (a)-[:TYPE {w: -1.0}]->(e)" +
            ", (a)-[:TYPE {w: 1.0}]->(f)" +
            ", (a)-[:TYPE {w: -1.0}]->(g)" +

            ", (b)-[:TYPE {w: -1.0}]->(c)" +
            ", (c)-[:TYPE {w: 1.0}]->(d)" +
            ", (d)-[:TYPE {w: -1.0}]->(e)" +
            ", (e)-[:TYPE {w: 1.0}]->(f)" +
            ", (f)-[:TYPE {w: -1.0}]->(g)" +
            ", (g)-[:TYPE {w: 1.0}]->(b)";
    }

    @Test
    void testWriting() {
        String query = GdsCypher.call()
            .withRelationshipProperty("w")
            .loadEverything(Projection.UNDIRECTED)
            .algo("gds", "alpha", "balancedTriads")
            .writeMode()
            .addParameter("relationshipWeightProperty", "w")
            .yields();

        runQueryWithRowConsumer(query, row -> {
            Assertions.assertEquals(3L, row.getNumber("balancedTriadCount"));
            Assertions.assertEquals(3L, row.getNumber("unbalancedTriadCount"));
        });
    }

    @Test
    void testStreaming() {
        BalancedTriadsConsumer mock = mock(BalancedTriadsConsumer.class);
        String query = GdsCypher.call()
            .withRelationshipProperty("w")
            .loadEverything(Projection.UNDIRECTED)
            .algo("gds", "alpha", "balancedTriads")
            .streamMode()
            .addParameter("relationshipWeightProperty", "w")
            .yields();

        runQueryWithRowConsumer(query, row -> {
            long nodeId = row.getNumber("nodeId").longValue();
            double balanced = row.getNumber("balanced").doubleValue();
            double unbalanced = row.getNumber("unbalanced").doubleValue();
            mock.consume(nodeId, balanced, unbalanced);
        });
        verify(mock, times(7)).consume(anyLong(), AdditionalMatchers.eq(1.0, 3.0), AdditionalMatchers.eq(1.0, 3.0));
    }

    interface BalancedTriadsConsumer {
        void consume(long nodeId, double balanced, double unbalanced);
    }
}
