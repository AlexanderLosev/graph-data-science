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
package org.neo4j.graphalgo.graphbuilder;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a grid of nodes
 *
 * A -- B -- C -- D -- E -- F ..
 * |    |    |    |    |    |
 * G -- H -- I -- J -- K -- L ..
 * |    |    |    |    |    |
 * ..   ..   ..   ..   ..   ..
 */
public class GridBuilder extends GraphBuilder<GridBuilder> {

    private List<List<Node>> lines = new ArrayList<>();

    GridBuilder(GraphDatabaseAPI api, Label label, RelationshipType relationship, Random random) {
        super(api, label, relationship, random);
    }

    public GridBuilder createGrid(int width, int height) {
        return createGrid(width, height, 1.0);
    }

    public GridBuilder createGrid(int width, int height, double connectivity) {
        withinTransaction(tx -> {
            List<Node> temp = null;
            for (int i = 0; i < height; i++) {
                List<Node> line = createLine(tx, width);
                if (null != temp) {
                    for (int j = 0; j < width; j++) {
                        if (randomDouble() < connectivity) {
                            createRelationship(tx, temp.get(j), line.get(j));
                        }
                    }
                }
                temp = line;
            }
        });
        return this;
    }

    private List<Node> createLine(Transaction tx, int length) {
        ArrayList<Node> nodes = new ArrayList<>();
        Node temp = createNode(tx);
        for (int i = 1; i < length; i++) {
            Node node = createNode(tx);
            nodes.add(temp);
            createRelationship(tx, temp, node);
            temp = node;
        }
        nodes.add(temp);
        lines.add(nodes);
        return nodes;
    }

    public List<List<Node>> getLineNodes() {
        return lines;
    }

    @Override
    protected GridBuilder me() {
        return this;
    }
}
