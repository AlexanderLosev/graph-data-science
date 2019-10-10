package org.neo4j.graphalgo.pregel;


public abstract class Computation {

    private Pregel.ComputeStep computeStep;

    void setComputeStep(final Pregel.ComputeStep computeStep) {
        this.computeStep = computeStep;
    }

    protected abstract void compute(final long nodeId);

    protected int getSuperStep() {
        return computeStep.getIteration();
    }

    protected double[] getMessages(final long nodeId) {
        return computeStep.getMessages(nodeId);
    }

    protected void sendToNeighbors(final long nodeId , final double message) {
        computeStep.sendToNeighbors(nodeId, message);
    }

    protected double getValue(final long nodeId) {
        return computeStep.getNodeValue(nodeId);
    }

    protected void setValue(final long nodeId, final double value) {
        computeStep.setNodeValue(nodeId, value);
    }
}
