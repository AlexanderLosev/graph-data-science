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

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomGraphGeneratorTest {

    @Test
    void shouldGenerateRelsUniformDistributed() {
        int nbrNodes = 10;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = new RandomGraphGenerator(
                nbrNodes,
                avgDeg,
                RelationshipDistribution.UNIFORM,
                Optional.empty(),
                AllocationTracker.EMPTY);
        HugeGraph graph = randomGraphGenerator.generate();

        assertEquals(graph.nodeCount(), nbrNodes);
        assertEquals(nbrNodes * avgDeg, graph.relationshipCount());

        graph.forEachNode((nodeId) -> {
            long[] degree = {0L};

            graph.forEachOutgoing(nodeId, (a, b) -> {
                degree[0] = degree[0] + 1;
                return true;
            });

            assertEquals(avgDeg, degree[0]);
            return true;
        });
    }

    @Test
    void shouldGenerateRelsPowerLawDistributed() {
        int nbrNodes = 10000;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = new RandomGraphGenerator(
                nbrNodes,
                avgDeg,
                RelationshipDistribution.POWER_LAW,
                Optional.empty(),
                AllocationTracker.EMPTY);
        HugeGraph graph = randomGraphGenerator.generate();

        assertEquals(graph.nodeCount(), nbrNodes);
        assertEquals((double) nbrNodes * avgDeg, graph.relationshipCount(), 1000D);
    }

    @Test
    void shouldGenerateRelsRandomDistributed() {
        int nbrNodes = 1000;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = new RandomGraphGenerator(
                nbrNodes,
                avgDeg,
                RelationshipDistribution.RANDOM,
                Optional.empty(),
                AllocationTracker.EMPTY);
        HugeGraph graph = randomGraphGenerator.generate();

        assertEquals(graph.nodeCount(), nbrNodes);

        List<Long> degrees = new ArrayList<Long>();
        graph.forEachNode((nodeId) -> {
            long[] degree = {0L};

            graph.forEachOutgoing(nodeId, (a, b) -> {
                degree[0] = degree[0] + 1;
                return true;
            });

            degrees.add(degree[0]);
            return true;
        });

        double actualAverage = degrees.stream().reduce(Long::sum).orElseGet(() -> 0L) / (double) degrees.size();
        assertEquals((double) avgDeg, actualAverage, 1D);
    }

    @Test
    void shouldGenerateRelationshipPropertiesWithFixedValue() {
        int nbrNodes = 10;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = new RandomGraphGenerator(
                nbrNodes,
                avgDeg,
                RelationshipDistribution.UNIFORM,
                Optional.of(RelationshipPropertyProducer.fixed("prop", 42D)),
                AllocationTracker.EMPTY);
        HugeGraph graph = randomGraphGenerator.generate();

        graph.forEachNode((nodeId) -> {
            graph.forEachRelationship(nodeId, Direction.BOTH, Double.NaN, (s, t, p) -> {
                assertEquals(42D, p);
                return true;
            });
            return true;
        });
    }

    @Test
    void shouldGenerateRelationshipWithRandom() {
        int nbrNodes = 10;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = new RandomGraphGenerator(
                nbrNodes,
                avgDeg,
                RelationshipDistribution.UNIFORM,
                Optional.of(RelationshipPropertyProducer.random("prop", -10, 10)),
                AllocationTracker.EMPTY);
        HugeGraph graph = randomGraphGenerator.generate();

        graph.forEachNode((nodeId) -> {
            graph.forEachRelationship(nodeId, Direction.BOTH, Double.NaN, (s, t, p) -> {
                assertTrue(p >= -10);
                assertTrue(p <= 10);
                return true;
            });
            return true;
        });
    }

    @Test
    void shouldBeSeedAble() {
        int nbrNodes = 10;
        long avgDeg = 5L;
        long edgeSeed = 1337L;
        long propertySeed = 1337L;

        RandomGraphGenerator randomGraphGenerator = new RandomGraphGenerator(
            nbrNodes,
            avgDeg,
            RelationshipDistribution.UNIFORM,
            edgeSeed,
            Optional.of(RelationshipPropertyProducer.random("prop", -10, 10, propertySeed)),
            AllocationTracker.EMPTY);

        RandomGraphGenerator otherRandomGenerator = new RandomGraphGenerator(
            nbrNodes,
            avgDeg,
            RelationshipDistribution.UNIFORM,
            edgeSeed,
            Optional.of(RelationshipPropertyProducer.random("prop", -10, 10, propertySeed)),
            AllocationTracker.EMPTY);

        HugeGraph graph1 = randomGraphGenerator.generate();

        HugeGraph graph2 = otherRandomGenerator.generate();

        assertEquals(relationships(graph1), relationships(graph2));
    }

    List<Triple<Long, Long, Double>> relationships(HugeGraph graph) {
        List<Triple<Long, Long, Double>> edgeList = new ArrayList<Triple<Long, Long, Double>>();
        graph.forEachNode(node -> {
            graph.forEachRelationship(node, Direction.INCOMING, Double.NaN, (src, target, weight) -> {
                edgeList.add(new ImmutableTriple<>(src, target, weight));
                return true;
            });
            return true;
        });
        return edgeList;
    }
}
