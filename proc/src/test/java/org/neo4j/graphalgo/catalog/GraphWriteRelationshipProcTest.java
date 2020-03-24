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
package org.neo4j.graphalgo.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.BaseProcTest;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.core.loading.GraphStoreCatalog;
import org.neo4j.graphdb.QueryExecutionException;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.neo4j.graphalgo.compat.MapUtil.map;
import static org.neo4j.graphalgo.utils.ExceptionUtil.rootCause;

class GraphWriteRelationshipProcTest extends BaseProcTest {
    private static final String TEST_GRAPH_NAME = "testGraph";

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node)" +
        ", (b:Node)" +
        ", (c:Node)" +
        ", (d:Node)" +
        ", (e:Node)" +
        ", (f:Node)" +
        ", (a)-[:REL1 { relProp1: 1.0 }]->(b)" +
        ", (a)-[:REL1 { relProp1: 2.0 }]->(c)" +
        ", (a)-[:REL2 { relProp2: 3.0 }]->(d)" +
        ", (d)-[:REL2 { relProp2: 4.0 }]->(e)" +
        ", (d)-[:REL3 { relProp3: 5.0 }]->(f)";

    @BeforeEach
    void setup() throws Exception {
        db = TestDatabaseCreator.createTestDatabase();
        registerProcedures(GraphCreateProc.class, GraphWriteRelationshipProc.class);
        runQuery(DB_CYPHER);

        runQuery(GdsCypher.call()
            .withAnyLabel()
            .withRelationshipType("NEW_REL1", "REL1")
            .withRelationshipType("NEW_REL2", "REL2")
            .withRelationshipType("NEW_REL3", "REL3")
            .withRelationshipProperty("newRelProp1", "relProp1")
            .withRelationshipProperty("newRelProp2", "relProp2")
            .withRelationshipProperty("newRelProp3", "relProp3")
            .graphCreate(TEST_GRAPH_NAME)
            .yields()
        );
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Test
    void writeRelationship() {
        String graphWriteQuery = String.format(
            "CALL gds.graph.writeRelationship(" +
            "   '%s', " +
            "   'NEW_REL1'" +
            ")",
            TEST_GRAPH_NAME
        );

        runQuery(graphWriteQuery);

        String validationQuery =
            "MATCH (n)-[r:NEW_REL1]->(m) " +
            "RETURN type(r) AS relType, count(r) AS count";

        assertCypherResult(validationQuery, singletonList(
            map("relType", "NEW_REL1", "count", 2L)
        ));
    }

    @Test
    void shouldFailOnNonExistingRelationshipType() {
        QueryExecutionException ex = assertThrows(
            QueryExecutionException.class,
            () -> runQuery(String.format(
                "CALL gds.graph.writeRelationship(" +
                "   '%s', " +
                "   'NEW_REL42'" +
                ")",
                TEST_GRAPH_NAME
            ))
        );

        Throwable rootCause = rootCause(ex);
        assertEquals(IllegalArgumentException.class, rootCause.getClass());
        assertThat(rootCause.getMessage(), containsString("`NEW_REL42` not found"));
        assertThat(rootCause.getMessage(), containsString("['NEW_REL1', 'NEW_REL2', 'NEW_REL3']"));
    }
}