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
package org.neo4j.graphalgo.pregel.components;

import org.neo4j.graphalgo.pregel.Computation;
import org.neo4j.graphdb.Direction;

import java.util.Queue;

public class WCCComputation extends Computation {

    @Override
    protected Direction getMessageDirection() {
        return Direction.BOTH;
    }

    @Override
    protected boolean isSynchronous() {
        return false;
    }

    @Override
    protected void compute(final long nodeId, Queue<Double> messages) {
        if (getSuperstep() == 0) {
            double currentValue = getValue(nodeId);
            if (currentValue == getDefaultNodeValue()) {
                sendMessages(nodeId, nodeId);
                setValue(nodeId, nodeId);
            } else {
                sendMessages(nodeId, currentValue);
            }
        } else {
            long oldComponentId = (long) getValue(nodeId);
            long newComponentId = oldComponentId;

            if (messages != null) {
                Double message;
                while ((message = messages.poll()) != null) {
                    if (message < newComponentId) {
                        newComponentId = message.longValue();
                    }
                }
            }

            if (newComponentId != oldComponentId) {
                setValue(nodeId, newComponentId);
                sendMessages(nodeId, newComponentId);
            }

            voteToHalt(nodeId);
        }
    }
}
