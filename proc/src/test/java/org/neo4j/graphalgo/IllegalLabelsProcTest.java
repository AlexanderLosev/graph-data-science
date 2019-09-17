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

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.unionfind.UnionFindProc;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import static org.neo4j.graphalgo.ThrowableRootCauseMatcher.rootCause;

public class IllegalLabelsProcTest extends GraphTester {

    private static final String DB_CYPHER = "" +
            "CREATE (a:A {id: 0}) " +
            "CREATE (b:B {id: 1}) " +
            "CREATE (a)-[:X]->(b)";

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @ClassRule
    public static ImpermanentDatabaseRule db = new ImpermanentDatabaseRule();

    private final String graphName;

    public IllegalLabelsProcTest(final Class<? extends GraphFactory> graphImpl, String name) {
        super(graphImpl);
        graphName = name;
    }

    @BeforeClass
    public static void setup() throws KernelException {
        db.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(UnionFindProc.class);
        db.execute(DB_CYPHER);
    }

    @Test
    public void testUnionFindStreamWithInvalidNodeLabel() {
        expected.expect(QueryExecutionException.class);
        expected.expectMessage("Node label not found: 'C'");
        db.execute(String.format("CALL algo.unionFind.stream('C', '',{graph:'%s'})", graphName));
    }

    @Test
    public void testUnionFindStreamWithInvalidRelType() {
        expected.expect(rootCause(
                IllegalArgumentException.class,
                "Relationship type(s) not found: 'Y'"
        ));
        db.execute(String.format("CALL algo.unionFind.stream('', 'Y',{graph:'%s'})", graphName));
    }

    @Test
    public void testUnionFindStreamWithValidNodeLabelAndInvalidRelType() {
        expected.expect(rootCause(
                IllegalArgumentException.class,
                "Relationship type(s) not found: 'Y'"
        ));
        db.execute(String.format("CALL algo.unionFind.stream('A', 'Y',{graph:'%s'})", graphName));
    }

    @Test
    public void testUnionFindStreamWithMultipleInvaludRelTypes() {
        expected.expect(rootCause(
                IllegalArgumentException.class,
                "Relationship type(s) not found: 'Y', 'Z'"
        ));
        db.execute(String.format("CALL algo.unionFind.stream('A', 'Y | Z',{graph:'%s'})", graphName));
    }
}
