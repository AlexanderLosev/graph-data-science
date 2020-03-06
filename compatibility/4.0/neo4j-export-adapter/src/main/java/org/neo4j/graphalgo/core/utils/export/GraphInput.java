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
package org.neo4j.graphalgo.core.utils.export;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.RelationshipIterator;
import org.neo4j.internal.batchimport.InputIterable;
import org.neo4j.internal.batchimport.InputIterator;
import org.neo4j.internal.batchimport.input.Collector;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.internal.batchimport.input.IdType;
import org.neo4j.internal.batchimport.input.Input;
import org.neo4j.internal.batchimport.input.InputChunk;
import org.neo4j.internal.batchimport.input.InputEntityVisitor;
import org.neo4j.internal.batchimport.input.ReadableGroups;
import org.neo4j.values.storable.Value;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class GraphInput implements Input {

    private final Graph graph;

    private final int batchSize;

    GraphInput(Graph graph, int batchSize) {
        this.graph = graph;
        this.batchSize = batchSize;
    }

    @Override
    public InputIterable nodes(Collector badCollector) {
        return () -> new NodeImporter(graph, batchSize);
    }

    @Override
    public InputIterable relationships(Collector badCollector) {
        return () -> new RelationshipImporter(graph.concurrentCopy(), graph.nodeCount(), batchSize);
    }

    @Override
    public IdType idType() {
        return IdType.ACTUAL;
    }

    @Override
    public ReadableGroups groups() {
        // TODO: @s1ck figure out what we need here
        return Groups.EMPTY;
    }

    @Override
    public Estimates calculateEstimates(ToIntFunction<Value[]> valueSizeCalculator) {
        long nodeCount = graph.nodeCount();
        long relationshipCount = graph.relationshipCount();
        long numberOfNodeProperties = graph.availableNodeProperties().size() * nodeCount;
        long numberOfRelationshipProperties = graph.hasRelationshipProperty() ? relationshipCount : 0;

        return Input.knownEstimates(
            nodeCount,
            relationshipCount,
            numberOfNodeProperties,
            numberOfRelationshipProperties,
            numberOfNodeProperties * Double.BYTES,
            numberOfRelationshipProperties * Double.BYTES,
            0
        );
    }

    abstract static class GraphImporter implements InputIterator {

        private final int batchSize;
        private final long nodeCount;

        private long id;

        GraphImporter(long nodeCount, int batchSize) {
            this.batchSize = batchSize;
            this.nodeCount = nodeCount;
        }

        @Override
        public synchronized boolean next(InputChunk chunk) {
            if (id >= nodeCount) {
                return false;
            }
            long startId = id;
            id = Math.min(nodeCount, startId + batchSize);

            ((EntityChunk) chunk).initialize(startId, id);
            return true;
        }

        @Override
        public void close() {
        }
    }

    static class NodeImporter extends GraphImporter {

        private final Graph graph;

        NodeImporter(Graph graph, int batchSize) {
            super(graph.nodeCount(), batchSize);
            this.graph = graph;
        }

        @Override
        public InputChunk newChunk() {
            return new NodeChunk(graph);
        }
    }

    static class RelationshipImporter extends GraphImporter {
        private final RelationshipIterator relationshipIterator;

        RelationshipImporter(RelationshipIterator relationshipIterator, long nodeCount, int batchSize) {
            super(nodeCount, batchSize);
            this.relationshipIterator = relationshipIterator;
        }

        @Override
        public InputChunk newChunk() {
            return new RelationshipChunk(relationshipIterator);
        }
    }

    abstract static class EntityChunk implements InputChunk {
        long id;
        long endId;

        void initialize(long startId, long endId) {
            this.id = startId;
            this.endId = endId;
        }

        @Override
        public void close() {
        }
    }

    static class NodeChunk extends EntityChunk {

        private final Graph graph;
        private final Set<String> nodeProperties;

        NodeChunk(Graph graph) {
            this.graph = graph;
            this.nodeProperties = graph.availableNodeProperties();
        }

        @Override
        public boolean next(InputEntityVisitor visitor) throws IOException {
            if (id < endId) {
                visitor.id(id);
                nodeProperties.forEach(p -> visitor.property(p, graph.nodeProperties(p).nodeProperty(id)));
                visitor.endOfEntity();
                id++;
                return true;
            }
            return false;
        }
    }

    static class RelationshipChunk extends EntityChunk {

        private final RelationshipIterator relationshipIterator;

        RelationshipChunk(RelationshipIterator relationshipIterator) {
            this.relationshipIterator = relationshipIterator;
        }

        @Override
        public boolean next(InputEntityVisitor visitor) {
            if (id < endId) {
                relationshipIterator.forEachRelationship(id, (s, t) -> {
                    visitor.startId(s);
                    visitor.endId(t);
                    visitor.type(0);
                    try {
                        visitor.endOfEntity();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                });
                id++;
                return true;
            }
            return false;
        }
    }
}
