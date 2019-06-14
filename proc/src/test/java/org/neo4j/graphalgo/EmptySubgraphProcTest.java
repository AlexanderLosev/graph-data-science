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
package org.neo4j.graphalgo;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class EmptySubgraphProcTest {

    private static final String DB_CYPHER = "" +
            "CREATE (a:A {id: 0}) " +
            "CREATE (b:B {id: 1}) " +
            "CREATE (a)-[:X]->(b)";

    private static GraphDatabaseAPI db;

    @BeforeClass
    public static void setup() throws KernelException {
        db = TestDatabaseCreator.createTestDatabase();
        db.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(UnionFindProc.class);
        db.execute(DB_CYPHER);
    }

    @AfterClass
    public static void tearDown() {
        if (db != null) db.shutdown();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{"Heavy"},
                new Object[]{"Huge"},
                new Object[]{"Kernel"}
        );
    }

    @Parameterized.Parameter
    public String graphImpl;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Test
    public void testUnionFindStreamWithInvalidNodeLabel() {
        expected.expect(QueryExecutionException.class);
        expected.expectMessage("Node label not found: 'C'");
        db.execute(String.format("CALL algo.unionFind.stream('C', '',{graph:'%s'})", graphImpl));
    }

    @Test
    public void testUnionFindStreamWithInvalidRelType() {
        expected.expect(QueryExecutionException.class);
        expected.expectMessage("Relationship type not found: 'Y'");
        db.execute(String.format("CALL algo.unionFind.stream('', 'Y',{graph:'%s'})", graphImpl));
    }

    @Test
    public void testUnionFindStreamWithValidNodeLabelAndInvalidRelType() {
        expected.expect(QueryExecutionException.class);
        expected.expectMessage("Relationship type not found: 'Y'");
        db.execute(String.format("CALL algo.unionFind.stream('A', 'Y',{graph:'%s'})", graphImpl));
    }
}
