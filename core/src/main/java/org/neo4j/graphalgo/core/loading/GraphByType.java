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

package org.neo4j.graphalgo.core.loading;

import org.neo4j.graphalgo.api.Graph;

public interface GraphByType {

    Graph loadGraph(String relationshipType);

    void release();

    String getType();

    void canRelease(boolean canRelease);

    final class SingleGraph implements GraphByType {
        private final Graph graph;

        public SingleGraph(Graph graph) {
            this.graph = graph;
        }

        @Override
        public Graph loadGraph(String relationshipType) {
            return graph;
        }

        @Override
        public void canRelease(boolean canRelease) {
            graph.canRelease(canRelease);
        }

        @Override
        public void release() {
            graph.release();
        }

        @Override
        public String getType() {
            return graph.getType();
        }
    }
}
