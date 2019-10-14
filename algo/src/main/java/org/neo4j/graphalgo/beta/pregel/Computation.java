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
package org.neo4j.graphalgo.beta.pregel;

import org.neo4j.graphdb.Direction;

import java.util.Queue;

public abstract class Computation {

    private Pregel.ComputeStep computeStep;

    protected abstract void compute(long nodeId, Queue<Double> messages);

    protected void voteToHalt(long nodeId) {
        computeStep.voteToHalt(nodeId);
    }

    protected int getSuperstep() {
        return computeStep.getIteration();
    }

    protected double getNodeValue(long nodeId) {
        return computeStep.getNodeValue(nodeId);
    }

    protected void setNodeValue(long nodeId, double value) {
        computeStep.setNodeValue(nodeId, value);
    }

    protected void sendMessages(long nodeId, double message) {
        sendMessages(nodeId, message, getMessageDirection());
    }

    protected void sendMessages(long nodeId, double message, Direction direction) {
        computeStep.sendMessages(nodeId, message, direction);
    }

    protected int getDegree(long nodeId) {
        return getDegree(nodeId, getMessageDirection());
    }

    protected int getDegree(long nodeId, Direction direction) {
        return computeStep.getDegree(nodeId, direction);
    }

    protected Direction getMessageDirection() {
        return Direction.OUTGOING;
    }

    protected double getDefaultNodeValue() {
        return -1.0;
    }

    void setComputeStep(Pregel.ComputeStep computeStep) {
        this.computeStep = computeStep;
    }

    protected boolean supportsAsynchronousParallel() {
        return false;
    }
}
