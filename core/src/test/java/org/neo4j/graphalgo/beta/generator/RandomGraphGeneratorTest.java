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
package org.neo4j.graphalgo.beta.generator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.TestSupport;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.config.RandomGraphGeneratorConfig;
import org.neo4j.graphalgo.config.RandomGraphGeneratorConfig.AllowSelfLoops;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.huge.HugeGraph;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

class RandomGraphGeneratorTest {

    @Test
    void shouldGenerateRelsUniformDistributed() {
        int nbrNodes = 10;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .build();
        HugeGraph graph = randomGraphGenerator.generate();

        assertEquals(graph.nodeCount(), nbrNodes);
        assertEquals(nbrNodes * avgDeg, graph.relationshipCount());

        graph.forEachNode((nodeId) -> {
            assertEquals(avgDeg, graph.degree(nodeId));
            return true;
        });
    }

    @Test
    void shouldGenerateRelsPowerLawDistributed() {
        int nbrNodes = 10_000;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.POWER_LAW)
            .build();
        HugeGraph graph = randomGraphGenerator.generate();

        assertEquals(graph.nodeCount(), nbrNodes);
        assertEquals((double) nbrNodes * avgDeg, graph.relationshipCount(), 1_000D);

        var previousAvgDegree = Double.POSITIVE_INFINITY;
        var bucketSize = nbrNodes / 10;
        for (var start = 0L; start < nbrNodes; start += bucketSize) {
            var end = Math.min(start + bucketSize, nbrNodes);
            var avgDegreeInBucket = LongStream
                    .rangeClosed(start, end)
                    .mapToInt(graph::degree)
                    .average()
                    .orElse(0.0);
            assertThat(avgDegreeInBucket).isLessThanOrEqualTo(previousAvgDegree);
            previousAvgDegree = avgDegreeInBucket;
        }
    }

    @Test
    void shouldNotGenerateSelfLoops() {
        int nbrNodes = 1000;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.POWER_LAW)
            .orientation(Orientation.UNDIRECTED)
            .allowSelfLoops(AllowSelfLoops.NO)
            .build();
        HugeGraph graph = randomGraphGenerator.generate();

        for (long nodeId = 0; nodeId < graph.nodeCount(); nodeId++) {
            graph.forEachRelationship(nodeId, (src, trg) -> {
                Assertions.assertNotEquals(src, trg);
                return true;
            });
        }
    }

    @Test
    void shouldGenerateRelsRandomDistributed() {
        int nbrNodes = 1_000;
        long avgDeg = 5L;

        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.RANDOM)
            .build();
        HugeGraph graph = randomGraphGenerator.generate();

        assertEquals(graph.nodeCount(), nbrNodes);

        AtomicLong actualRelCount = new AtomicLong();
        graph.forEachNode(node -> {
                actualRelCount.addAndGet(graph.degree(node));
                return true;
            }
        );

        double actualAverage = actualRelCount.get() / (double) nbrNodes;

        assertEquals((double) avgDeg, actualAverage, 0.5D);
    }

    @Test
    void shouldGenerateRelationshipPropertiesWithFixedValue() {
        int nbrNodes = 10;
        long avgDeg = 5L;

        double fixedValue = 42D;
        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .relationshipPropertyProducer(PropertyProducer.fixed("property", fixedValue))
            .build();
        HugeGraph graph = randomGraphGenerator.generate();

        graph.forEachNode((nodeId) -> {
            graph.forEachRelationship(nodeId, Double.NaN, (s, t, p) -> {
                assertEquals(fixedValue, p);
                return true;
            });
            return true;
        });
    }

    @Test
    void shouldGenerateRelationshipWithRandom() {
        int lowerBound = -10;
        int upperBound = 10;

        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(10)
            .averageDegree(5L)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .relationshipPropertyProducer(PropertyProducer.random("prop", lowerBound, upperBound))
            .build();
        HugeGraph graph = randomGraphGenerator.generate();

        graph.forEachNode((nodeId) -> {
            graph.forEachRelationship(nodeId, Double.NaN, (s, t, p) -> {
                assertTrue(p >= lowerBound);
                assertTrue(p <= upperBound);
                return true;
            });
            return true;
        });
    }

    @Test
    void shouldGenerateNodeLabels() {
        NodeLabel[] aLabel = new NodeLabel[]{NodeLabel.of("A")};
        NodeLabel[] bLabel = new NodeLabel[]{NodeLabel.of("B")};

        HugeGraph graph = RandomGraphGenerator.builder()
            .nodeCount(10)
            .averageDegree(2)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .nodeLabelProducer(nodeId -> nodeId % 2 == 0 ? aLabel : bLabel)
            .build()
            .generate();

        graph.forEachNode(nodeId -> {
                var nodeLabels = graph.nodeLabels(nodeId);
                assertEquals(1, nodeLabels.size());
                if (nodeId % 2 == 0) {
                    assertTrue(nodeLabels.contains(NodeLabel.of("A")), formatWithLocale("node %d should have label A", nodeId));
                } else {
                    assertTrue(nodeLabels.contains(NodeLabel.of("B")), formatWithLocale("node %d should have label B", nodeId));
                }
                return true;
            }
        );
    }

    @Test
    void shouldGenerateNodeProperties() {
        int lowerBound = 0;
        int upperBound = 1;
        HugeGraph graph = RandomGraphGenerator.builder()
            .nodeCount(10)
            .averageDegree(2)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .nodePropertyProducer(PropertyProducer.random("foo", lowerBound, upperBound))
            .build()
            .generate();

        NodeProperties nodeProperties = graph.nodeProperties("foo");
        graph.forEachNode(nodeId -> {
                double value = nodeProperties.doubleValue(nodeId);
                assertTrue(lowerBound <= value && value <= upperBound);
                return true;
            }
        );
    }

    @Test
    void shouldGenerateNodeLabelsAndProperties() {
        NodeLabel[] aLabel = new NodeLabel[]{NodeLabel.of("A"), NodeLabel.ALL_NODES};
        NodeLabel[] bLabel = new NodeLabel[]{NodeLabel.of("B"), NodeLabel.ALL_NODES};

        HugeGraph graph = RandomGraphGenerator.builder()
            .nodeCount(10)
            .averageDegree(2)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .nodeLabelProducer(nodeId -> nodeId % 2 == 0 ? aLabel : bLabel)
            .nodePropertyProducer(PropertyProducer.fixed("all", 1337.0))
            .addNodePropertyProducer(NodeLabel.of("A"), PropertyProducer.fixed("foo", 42.0))
            .addNodePropertyProducer(NodeLabel.of("B"), PropertyProducer.fixed("bar", 84.0))
            .addNodePropertyProducer(NodeLabel.of("B"), PropertyProducer.fixed("baz", 23.0))
            .build()
            .generate();

        var allProperties = graph.nodeProperties("all");
        var fooProperties = graph.nodeProperties("foo");
        var barProperties = graph.nodeProperties("bar");
        var bazProperties = graph.nodeProperties("baz");

        graph.forEachNode(nodeId -> {
                var nodeLabels = graph.nodeLabels(nodeId);
                assertEquals(2, nodeLabels.size());
                assertEquals(1337.0, allProperties.doubleValue(nodeId));
                if (nodeId % 2 == 0) {
                    assertTrue(nodeLabels.contains(NodeLabel.of("A")), formatWithLocale("node %d should have label A", nodeId));
                    assertEquals(42.0, fooProperties.doubleValue(nodeId));
                    assertTrue(Double.isNaN(barProperties.doubleValue(nodeId)));
                    assertTrue(Double.isNaN(bazProperties.doubleValue(nodeId)));
                } else {
                    assertTrue(nodeLabels.contains(NodeLabel.of("B")), formatWithLocale("node %d should have label B", nodeId));
                    assertEquals(84.0, barProperties.doubleValue(nodeId));
                    assertEquals(23.0, bazProperties.doubleValue(nodeId));
                    assertTrue(Double.isNaN(fooProperties.doubleValue(nodeId)));
                }
                return true;
            }
        );
    }

    @Test
    void shouldBeSeedAble() {
        int nbrNodes = 10;
        long avgDeg = 5L;
        long seed = 1337L;

        RandomGraphGenerator randomGraphGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .seed(seed)
            .allocationTracker(AllocationTracker.empty())
            .build();

        RandomGraphGenerator otherRandomGenerator = RandomGraphGenerator.builder()
            .nodeCount(nbrNodes)
            .averageDegree(avgDeg)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .seed(seed)
            .allocationTracker(AllocationTracker.empty())
            .build();

        HugeGraph graph1 = randomGraphGenerator.generate();
        HugeGraph graph2 = otherRandomGenerator.generate();

        TestSupport.assertGraphEquals(graph1, graph2);
    }

    @Test
    void shouldProduceCorrectRelationshipCountWithAggregation() {
        var graph = RandomGraphGenerator.builder()
            .nodeCount(8)
            .averageDegree(4)
            .seed(42L)
            .aggregation(Aggregation.SINGLE)
            .allocationTracker(AllocationTracker.empty())
            .allowSelfLoops(RandomGraphGeneratorConfig.AllowSelfLoops.NO)
            .relationshipDistribution(RelationshipDistribution.POWER_LAW)
            .orientation(Orientation.UNDIRECTED)
            .build()
            .generate();

        AtomicLong actualRelCount = new AtomicLong();

        graph.forEachNode(node -> {
                actualRelCount.addAndGet(graph.degree(node));
                return true;
            }
        );

        assertEquals(actualRelCount.get(), graph.relationshipCount());
    }
}
