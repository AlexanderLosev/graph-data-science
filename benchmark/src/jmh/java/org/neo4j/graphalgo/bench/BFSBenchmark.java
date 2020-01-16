/*
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
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
package org.neo4j.graphalgo.bench;

import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.graphbuilder.GraphBuilder;
import org.neo4j.graphalgo.impl.Traverse;
import org.neo4j.graphdb.Direction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static org.neo4j.graphalgo.impl.Traverse.TraverseAlgo.BFS;
import static org.neo4j.graphalgo.impl.Traverse.TraverseAlgo.DFS;

@Threads(1)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BFSBenchmark extends BaseBenchmark {

    private static final String LABEL = "Node";
    private static final String RELATIONSHIP = "REL";
    private static final int TRIANGLE_COUNT = 500;

    private Graph g;

    @Param({"0.2", "0.5", "0.8"})
    private double connectedness;

    @Setup
    public void setup() {
        db = TestDatabaseCreator.createTestDatabase();

        GraphBuilder.create(db)
                .setLabel(LABEL)
                .setRelationship(RELATIONSHIP)
                .newCompleteGraphBuilder()
                .createCompleteGraph(TRIANGLE_COUNT, connectedness);

        g = new GraphLoader(db)
                .withLabel(LABEL)
                .withRelationshipType(RELATIONSHIP)
                .sorted()
                .undirected()
                .load(HugeGraphFactory.class);
    }

    @TearDown
    public void tearDown() {
        db.shutdown();
        Pools.DEFAULT.shutdownNow();
    }

    @Benchmark
    public Object bfs() {
        return new Traverse(g, BFS, Direction.OUTGOING, 0L, (s, t, w) -> Traverse.ExitPredicate.Result.FOLLOW, Traverse.DEFAULT_AGGREGATOR).compute();
    }

    @Benchmark
    public Object dfs() {
        return new Traverse(g, DFS, Direction.OUTGOING, 0L, (s, t, w) -> Traverse.ExitPredicate.Result.FOLLOW, Traverse.DEFAULT_AGGREGATOR).compute();
    }
}
