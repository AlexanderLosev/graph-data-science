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

import org.neo4j.graphalgo.api.GraphLoadingContext;
import org.neo4j.graphalgo.api.GraphStoreFactory;
import org.neo4j.graphalgo.compat.GraphDatabaseApiProxy;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.config.GraphCreateFromCypherConfig;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.ImmutableGraphDimensions;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.NotInTransactionException;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.security.AuthSubject;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Optional;
import java.util.function.Function;

import static org.neo4j.graphalgo.compat.GraphDatabaseApiProxy.newKernelTransaction;
import static org.neo4j.graphalgo.core.loading.CypherRecordLoader.QueryType.NODE;
import static org.neo4j.graphalgo.core.loading.CypherRecordLoader.QueryType.RELATIONSHIP;
import static org.neo4j.internal.kernel.api.security.AccessMode.Static.READ;

public class CypherFactory extends GraphStoreFactory {

    private final GraphDatabaseAPI api;
    private final GraphCreateConfig graphCreateConfig;
    private final GraphLoadingContext loadingContext;

    public CypherFactory(
        GraphDatabaseAPI api,
        GraphCreateConfig graphCreateConfig,
        GraphLoadingContext loadingContext
    ) {
        super(api, loadingContext, graphCreateConfig, false);
        this.api = api;
        this.graphCreateConfig = graphCreateConfig;
        this.loadingContext = loadingContext;
    }

    public final MemoryEstimation memoryEstimation() {
        BatchLoadResult nodeCount;
        BatchLoadResult relCount;
        try (Ktx ktx = setReadOnlySecurityContext()) {
            nodeCount = new CountingCypherRecordLoader(nodeQuery(), NODE, api, graphCreateConfig, loadingContext).load(ktx);
            relCount = new CountingCypherRecordLoader(relationshipQuery(), RELATIONSHIP, api, graphCreateConfig,
                loadingContext
            ).load(ktx);
        }

        GraphDimensions estimateDimensions = ImmutableGraphDimensions.builder()
            .from(dimensions)
            .nodeCount(nodeCount.rows())
            .maxRelCount(relCount.rows())
            .build();

        return NativeFactory.getMemoryEstimation(estimateDimensions, graphCreateConfig);
    }

    @Override
    public MemoryEstimation memoryEstimation(GraphDimensions dimensions) {
        return NativeFactory.getMemoryEstimation(dimensions, graphCreateConfig);
    }

    @Override
    public ImportResult build() {
        // Temporarily override the security context to enforce read-only access during load
        try (Ktx ktx = setReadOnlySecurityContext()) {
            BatchLoadResult nodeCount = new CountingCypherRecordLoader(nodeQuery(), NODE, api, graphCreateConfig,
                loadingContext
            ).load(ktx);

            CypherNodeLoader.LoadResult nodes = new CypherNodeLoader(
                nodeQuery(),
                nodeCount.rows(),
                api,
                graphCreateConfig,
                loadingContext,
                dimensions
            ).load(ktx);

            RelationshipImportResult relationships = loadRelationships(
                relationshipQuery(),
                nodes.idsAndProperties(),
                nodes.dimensions(),
                ktx
            );

            GraphStore graphStore = createGraphStore(
                nodes.idsAndProperties(),
                relationships,
                loadingContext.tracker(),
                relationships.dimensions()
            );

            progressLogger.logMessage(loadingContext.tracker());
            return ImportResult.of(relationships.dimensions(), graphStore);
        }
    }

    private String nodeQuery() {
        return getCypherConfig(graphCreateConfig)
            .orElseThrow(() -> new IllegalArgumentException("Missing node query"))
            .nodeQuery();
    }

    private String relationshipQuery() {
        return getCypherConfig(graphCreateConfig)
            .orElseThrow(() -> new IllegalArgumentException("Missing relationship query"))
            .relationshipQuery();
    }

    private Optional<GraphCreateFromCypherConfig> getCypherConfig(GraphCreateConfig config) {
        if (!config.isCypher()) {
            return Optional.empty();
        }
        return Optional.of((GraphCreateFromCypherConfig) config);
    }

    private RelationshipImportResult loadRelationships(
        String relationshipQuery,
        IdsAndProperties idsAndProperties,
        GraphDimensions nodeLoadDimensions,
        Ktx ktx
    ) {
        CypherRelationshipLoader relationshipLoader = new CypherRelationshipLoader(
            relationshipQuery,
            idsAndProperties.idMap(),
            api,
            graphCreateConfig,
            loadingContext,
            nodeLoadDimensions
        );

        CypherRelationshipLoader.LoadResult result = relationshipLoader.load(ktx);

        return RelationshipImportResult.of(
            relationshipLoader.allBuilders(),
            result.relationshipCounts(),
            result.dimensions()
        );
    }

    private Ktx setReadOnlySecurityContext() {
        GraphDatabaseApiProxy.Transactions transactions = newKernelTransaction(api);
        KernelTransaction ktx = transactions.ktx();
        try {
            AuthSubject subject = ktx.securityContext().subject();
            SecurityContext securityContext = new SecurityContext(subject, READ);
            return new Ktx(api, transactions, securityContext);
        } catch (NotInTransactionException ex) {
            // happens only in tests
            throw new IllegalStateException("Must run in a transaction.", ex);
        }
    }

    static final class Ktx implements AutoCloseable {
        private final GraphDatabaseService db;
        private final GraphDatabaseApiProxy.Transactions top;
        private final SecurityContext securityContext;
        private final KernelTransaction.Revertable revertTop;

        private Ktx(
            GraphDatabaseService db,
            GraphDatabaseApiProxy.Transactions top,
            SecurityContext securityContext
        ) {
            this.db = db;
            this.top = top;
            this.securityContext = securityContext;
            this.revertTop = top.ktx().overrideWith(securityContext);
        }

        <T> T run(Function<Transaction, T> block) {
            return block.apply(top.tx());
        }

        <T> T fork(Function<Transaction, T> block) {
            GraphDatabaseApiProxy.Transactions txs = newKernelTransaction(db);
            try (Transaction tx = txs.tx();
                 KernelTransaction.Revertable ignore = txs.ktx().overrideWith(securityContext)) {
                return block.apply(tx);
            }
        }

        @Override
        public void close() {
            try {
                this.revertTop.close();
            } finally {
                top.close();
            }
        }
    }
}
