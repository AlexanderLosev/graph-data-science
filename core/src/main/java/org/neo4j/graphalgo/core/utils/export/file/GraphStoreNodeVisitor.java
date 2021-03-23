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
package org.neo4j.graphalgo.core.utils.export.file;

import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.api.schema.NodeSchema;
import org.neo4j.graphalgo.core.loading.construction.NodesBuilder;

public class GraphStoreNodeVisitor extends NodeVisitor {

    private final NodesBuilder nodesBuilder;

    public GraphStoreNodeVisitor(NodeSchema nodeSchema, NodesBuilder nodesBuilder, boolean reverseIdMapping) {
        super(nodeSchema, reverseIdMapping);
        this.nodesBuilder = nodesBuilder;
    }

    @Override
    protected void exportElement() {
        NodeLabel[] nodeLabels = labels().stream().map(NodeLabel::of).toArray(NodeLabel[]::new);
        // TODO: this is wrong we need to export the neo id (and the mapped id)
        nodesBuilder.addNode(id(), nodeLabels);
    }

    static final class Builder extends NodeVisitor.Builder<Builder, GraphStoreNodeVisitor> {

        private NodesBuilder nodesBuilder;

        public Builder withNodesBuilder(NodesBuilder nodesBuilder) {
            this.nodesBuilder = nodesBuilder;
            return this;
        }

        @Override
        Builder me() {
            return this;
        }

        @Override
        GraphStoreNodeVisitor build() {
            return new GraphStoreNodeVisitor(nodeSchema, nodesBuilder, reverseIdMapping);
        }
    }
}