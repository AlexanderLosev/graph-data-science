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
package org.neo4j.graphalgo.core.huge.loader;

import org.neo4j.graphalgo.api.GraphSetup;
import org.neo4j.graphalgo.api.IdMapping;
import org.neo4j.graphalgo.core.utils.ImportProgress;
import org.neo4j.graphalgo.core.utils.RawValues;
import org.neo4j.graphalgo.core.utils.StatementAction;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.store.record.RelationshipRecord;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

final class RelationshipsScanner extends StatementAction implements RecordScanner {

    static ImportingThreadPool.CreateScanner of(
            GraphDatabaseAPI api,
            GraphSetup setup,
            ImportProgress progress,
            IdMapping idMap,
            AbstractStorePageCacheScanner<RelationshipRecord> scanner,
            int relType,
            AllocationTracker tracker,
            WeightBuilder weights,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        final Imports imports = imports(setup, weights.loadsWeights());
        if (imports == null) {
            return ImportingThreadPool.createEmptyScanner();
        }
        final AdjacencyBuilder actualInAdjacency = setup.loadAsUndirected ? outAdjacency : inAdjacency;
        return new RelationshipsScanner.Creator(
                api, progress, idMap, scanner, relType, tracker,
                weights, outAdjacency, actualInAdjacency, imports);
    }

    static final class Creator implements ImportingThreadPool.CreateScanner {
        private final GraphDatabaseAPI api;
        private final ImportProgress progress;
        private final IdMapping idMap;
        private final AbstractStorePageCacheScanner<RelationshipRecord> scanner;
        private final int relType;
        private final AllocationTracker tracker;
        private final WeightBuilder weights;
        private final AdjacencyBuilder outAdjacency;
        private final AdjacencyBuilder inAdjacency;
        private final Imports imports;

        Creator(
                GraphDatabaseAPI api,
                ImportProgress progress,
                IdMapping idMap,
                AbstractStorePageCacheScanner<RelationshipRecord> scanner,
                int relType,
                AllocationTracker tracker,
                WeightBuilder weights,
                AdjacencyBuilder outAdjacency,
                AdjacencyBuilder inAdjacency,
                Imports imports) {
            this.api = api;
            this.progress = progress;
            this.idMap = idMap;
            this.scanner = scanner;
            this.relType = relType;
            this.tracker = tracker;
            this.weights = weights;
            this.outAdjacency = outAdjacency;
            this.inAdjacency = inAdjacency;
            this.imports = imports;
        }

        @Override
        public RecordScanner create(final int index) {
            return new RelationshipsScanner(
                    api, progress, idMap, scanner, relType, index,
                    tracker, weights, outAdjacency, inAdjacency, imports);
        }

        @Override
        public Collection<Runnable> flushTasks() {
            if (outAdjacency != null) {
                if (inAdjacency == null || inAdjacency == outAdjacency) {
                    return outAdjacency.flushTasks();
                }
                Collection<Runnable> tasks = new ArrayList<>(outAdjacency.flushTasks());
                tasks.addAll(inAdjacency.flushTasks());
                return tasks;
            }
            if (inAdjacency != null) {
                return inAdjacency.flushTasks();
            }
            return Collections.emptyList();
        }
    }

    private final ImportProgress progress;
    private final IdMapping idMap;
    private final AbstractStorePageCacheScanner<RelationshipRecord> scanner;
    private final int relType;
    private final int scannerIndex;

    private final AllocationTracker tracker;
    private final WeightBuilder weights;
    private final AdjacencyBuilder outAdjacency;
    private final AdjacencyBuilder inAdjacency;
    private final Imports imports;

    private volatile long relationshipsImported;
    private long weightsImported;

    private RelationshipsScanner(
            GraphDatabaseAPI api,
            ImportProgress progress,
            IdMapping idMap,
            AbstractStorePageCacheScanner<RelationshipRecord> scanner,
            int relType,
            int threadIndex,
            AllocationTracker tracker,
            WeightBuilder weights,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency,
            Imports imports) {
        super(api);
        this.progress = progress;
        this.idMap = idMap;
        this.scanner = scanner;
        this.relType = relType;
        this.scannerIndex = threadIndex;
        this.tracker = tracker;
        this.weights = weights;
        this.outAdjacency = outAdjacency;
        this.inAdjacency = inAdjacency;
        this.imports = imports;
    }

    @Override
    public String threadName() {
        return "relationship-store-scan-" + scannerIndex;
    }

    @Override
    public void accept(final KernelTransaction transaction) {
        scanRelationships(transaction.dataRead(), transaction.cursors());
    }

    private void scanRelationships(final Read read, final CursorFactory cursors) {
        try (AbstractStorePageCacheScanner<RelationshipRecord>.Cursor cursor = scanner.getCursor()) {
            RelationshipsBatchBuffer batches = new RelationshipsBatchBuffer(idMap, relType, cursor.bulkSize());

            final WeightBuilder weights = this.weights;
            final ImportProgress progress = this.progress;
            final AdjacencyBuilder outAdjacency = this.outAdjacency;
            final AdjacencyBuilder inAdjacency = this.inAdjacency;
            final AllocationTracker tracker = this.tracker;
            final Imports imports = this.imports;

            long allImportedRels = 0L;
            long allImportedWeights = 0L;
            while (batches.scan(cursor)) {
                int batchLength = batches.length();
                long imported = imports.importRels(
                        batches, batchLength, weights, cursors, read, tracker, outAdjacency, inAdjacency
                );
                int importedRels = RawValues.getHead(imported);
                int importedWeights = RawValues.getTail(imported);
                progress.relationshipsImported(importedRels);
                allImportedRels += importedRels;
                allImportedWeights += importedWeights;
            }
            relationshipsImported = allImportedRels;
            weightsImported = allImportedWeights;
        }
    }

    @Override
    public long propertiesImported() {
        return weightsImported;
    }

    @Override
    public long recordsImported() {
        return relationshipsImported;
    }

    interface Imports {
        long importRels(
                RelationshipsBatchBuffer batches,
                int batchLength,
                WeightBuilder weights,
                CursorFactory cursors,
                Read read,
                AllocationTracker tracker,
                AdjacencyBuilder outAdjacency,
                AdjacencyBuilder inAdjacency);
    }

    private static Imports imports(GraphSetup setup, boolean loadWeights) {
        if (setup.loadAsUndirected) {
            return loadWeights
                    ? RelationshipsScanner::importUndirectedWithWeight
                    : RelationshipsScanner::importBothOrUndirected;
        }
        if (setup.loadOutgoing) {
            if (setup.loadIncoming) {
                return loadWeights
                        ? RelationshipsScanner::importBothWithWeight
                        : RelationshipsScanner::importBothOrUndirected;
            }
            return loadWeights
                    ? RelationshipsScanner::importOutgoingWithWeight
                    : RelationshipsScanner::importOutgoing;
        }
        if (setup.loadIncoming) {
            return loadWeights
                    ? RelationshipsScanner::importIncomingWithWeight
                    : RelationshipsScanner::importIncoming;
        }

        return null;
    }

    private static long importBothOrUndirected(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortBySource();
        int importedOut = importRelationships(buffer, batch, batchLength, outAdjacency, tracker);
        batch = buffer.sortByTarget();
        int importedIn = importRelationships(buffer, batch, batchLength, inAdjacency, tracker);
        return RawValues.combineIntInt(importedOut + importedIn, 0);
    }

    private static long importUndirectedWithWeight(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortBySource();
        int importedOut = importRelationships(buffer, batch, batchLength, outAdjacency, tracker);
        int importedOutWeights = importWeights(batch, batchLength, weights, cursors, read);
        batch = buffer.sortByTarget();
        int importedIn = importRelationships(buffer, batch, batchLength, inAdjacency, tracker);
        int importedInWeights = importWeights(batch, batchLength, weights, cursors, read);
        return RawValues.combineIntInt(importedOut + importedIn, importedOutWeights + importedInWeights);
    }

    private static long importBothWithWeight(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortBySource();
        int importedOut = importRelationships(buffer, batch, batchLength, outAdjacency, tracker);
        int importedOutWeights = importWeights(batch, batchLength, weights, cursors, read);
        batch = buffer.sortByTarget();
        int importedIn = importRelationships(buffer, batch, batchLength, inAdjacency, tracker);
        return RawValues.combineIntInt(importedOut + importedIn, importedOutWeights);
    }

    private static long importOutgoing(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortBySource();
        return RawValues.combineIntInt(importRelationships(buffer, batch, batchLength, outAdjacency, tracker), 0);
    }

    private static long importOutgoingWithWeight(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortBySource();
        int importedOut = importRelationships(buffer, batch, batchLength, outAdjacency, tracker);
        int importedOutWeights = importWeights(batch, batchLength, weights, cursors, read);
        return RawValues.combineIntInt(importedOut, importedOutWeights);
    }

    private static long importIncoming(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortByTarget();
        return RawValues.combineIntInt(importRelationships(buffer, batch, batchLength, inAdjacency, tracker), 0);
    }

    private static long importIncomingWithWeight(
            RelationshipsBatchBuffer buffer,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read,
            AllocationTracker tracker,
            AdjacencyBuilder outAdjacency,
            AdjacencyBuilder inAdjacency) {
        long[] batch = buffer.sortBySource();
        int importedInWeights = importWeights(batch, batchLength, weights, cursors, read);
        batch = buffer.sortByTarget();
        int importedIn = importRelationships(buffer, batch, batchLength, inAdjacency, tracker);
        return RawValues.combineIntInt(importedIn, importedInWeights);
    }

    private static int importRelationships(
            RelationshipsBatchBuffer buffer,
            long[] batch,
            int batchLength,
            AdjacencyBuilder adjacency,
            AllocationTracker tracker) {

        int[] offsets = buffer.spareInts();
        long[] targets = buffer.spareLongs();

        long source, target, prevSource = batch[0];
        int offset = 0, nodesLength = 0;

        for (int i = 0; i < batchLength; i += 4) {
            source = batch[i];
            target = batch[1 + i];
            if (source > prevSource) {
                offsets[nodesLength++] = offset;
                prevSource = source;
            }
            targets[offset++] = target;
        }
        offsets[nodesLength++] = offset;

        adjacency.addAll(
                batch,
                targets,
                offsets,
                nodesLength,
                tracker
        );

        return batchLength >> 2;
    }

    private static int importWeights(
            long[] batch,
            int batchLength,
            WeightBuilder weights,
            CursorFactory cursors,
            Read read) {
        int importedWeights = 0;

        for (int i = 0; i < batchLength; i += 4) {
            importedWeights += weights.addWeight(
                    cursors,
                    read,
                    /* rel ref */ batch[2 + i],
                    /* prop ref */ batch[3 + i],
                    /* source */ batch[i],
                    /* target */ batch[1 + i]);
        }
        return importedWeights;
    }
}
