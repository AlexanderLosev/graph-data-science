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
package org.neo4j.graphalgo.core.loading;

import com.carrotsearch.hppc.LongSet;
import org.neo4j.kernel.impl.store.NodeLabelsField;
import org.neo4j.kernel.impl.store.NodeStore;
import org.neo4j.kernel.impl.store.record.NodeRecord;

public final class NodesBatchBuffer extends RecordsBatchBuffer<NodeRecord> {

    public static final int IGNORE_LABEL = -1;

    private final LongSet nodeLabelIds;
    private final NodeStore nodeStore;
    private final long[][] labelIds;


    // property ids, consecutive
    private final long[] properties;

    public NodesBatchBuffer(
        final NodeStore store,
        final LongSet labels,
        int capacity,
        boolean readProperty
    ) {
        super(capacity);
        this.nodeLabelIds = labels;
        this.nodeStore = store;
        this.properties = readProperty ? new long[capacity] : null;
        this.labelIds = nodeLabelIds.isEmpty() ? null : new long[capacity][];
    }

    @Override
    public void offer(final NodeRecord record) {
        if (nodeLabelIds.isEmpty()) {
            add(record.getId(), record.getNextProp(), null);
        } else {
            boolean atLeastOneLabelFound = false;
            final long[] labels = NodeLabelsField.get(record, nodeStore);
            for (int i = 0; i < labels.length; i++) {
                long l = labels[i];
                if (!nodeLabelIds.contains(l)) {
                    labels[i] = IGNORE_LABEL;
                } else {
                    atLeastOneLabelFound = true;
                }
            }
            if (atLeastOneLabelFound) {
                add(record.getId(), record.getNextProp(), labels);
            }
        }
    }

    public void add(long nodeId, long propertiesIndex, long[] labels) {
        int len = length++;
        buffer[len] = nodeId;
        if (properties != null) {
            properties[len] = propertiesIndex;
        }
        if (labelIds != null) {
            labelIds[len] = labels;
        }
    }

    long[] properties() {
        return this.properties;
    }

    long[][] labelIds() {
        return this.labelIds;
    }
}
