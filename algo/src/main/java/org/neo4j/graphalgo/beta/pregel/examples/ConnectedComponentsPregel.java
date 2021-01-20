/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.beta.pregel.examples;

import org.neo4j.graphalgo.beta.pregel.PregelComputation;
import org.neo4j.graphalgo.beta.pregel.PregelContext;

import java.util.Queue;

public class ConnectedComponentsPregel implements PregelComputation {

    @Override
    public void compute(PregelContext pregel, final long nodeId, Queue<Double> messages) {
        if (pregel.isInitialSuperStep()) {
            // Inremental computation
            double currentValue = pregel.getNodeValue(nodeId);
            if (Double.compare(currentValue, pregel.getInitialNodeValue()) == 0) {
                pregel.sendMessages(nodeId, nodeId);
                pregel.setNodeValue(nodeId, nodeId);
            } else {
                pregel.sendMessages(nodeId, currentValue);
            }
        } else {
            long newComponentId = (long) pregel.getNodeValue(nodeId);
            boolean hasChanged = false;

            if (messages != null) {
                Double message;
                while ((message = messages.poll()) != null) {
                    if (message < newComponentId) {
                        newComponentId = message.longValue();
                        hasChanged = true;
                    }
                }
            }

            if (hasChanged) {
                pregel.setNodeValue(nodeId, newComponentId);
                pregel.sendMessages(nodeId, newComponentId);
            }

            pregel.voteToHalt(nodeId);
        }
    }
}
