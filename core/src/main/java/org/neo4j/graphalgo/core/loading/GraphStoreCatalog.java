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

import org.jetbrains.annotations.Nullable;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.config.GraphCreateConfig;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public final class GraphStoreCatalog {

    private static final ConcurrentHashMap<String, UserCatalog> userCatalogs = new ConcurrentHashMap<>();

    private GraphStoreCatalog() { }

    public static GraphStoreWithConfig get(String username, String graphName) {
        return getUserCatalog(username).get(graphName);
    }

    public static void set(GraphCreateConfig config, GraphStore graphStore) {
        graphStore.canRelease(false);
        userCatalogs.compute(config.username(), (user, userCatalog) -> {
            if (userCatalog == null) {
                userCatalog = new UserCatalog();
            }
            userCatalog.set(config, graphStore);
            return userCatalog;
        });
    }

    public static Optional<Graph> getUnion(String username, String graphName) {
        return getUserCatalog(username).getUnion(graphName);
    }

    public static boolean exists(String username, String graphName) {
        return getUserCatalog(username).exists(graphName);
    }

    public static void remove(String username, String graphName, Consumer<GraphStoreWithConfig> removedGraphConsumer) {
        GraphStoreWithConfig graphStoreWithConfig = Optional
            .ofNullable(getUserCatalog(username).remove(graphName))
            .orElseThrow(failOnNonExistentGraph(graphName));

        removedGraphConsumer.accept(graphStoreWithConfig);

        GraphStore graphStore = graphStoreWithConfig.graphStore();
        graphStore.canRelease(true);
        graphStore.release();
    }

    public static int graphStoresCount() {
        return (int) userCatalogs
            .values()
            .stream()
            .mapToLong(userCatalog -> userCatalog.getGraphStores().values().size())
            .sum();
    }

    public static UserCatalog getUserCatalog(String username) {
        return userCatalogs.getOrDefault(username, UserCatalog.EMPTY);
    }

    public static void removeAllLoadedGraphs() {
        userCatalogs.clear();
    }

    public static Map<GraphCreateConfig, GraphStore> getGraphStores(String username) {
        return getUserCatalog(username).getGraphStores();
    }

    private static Supplier<RuntimeException> failOnNonExistentGraph(String graphName) {
        return () -> new IllegalArgumentException(formatWithLocale(
            "Graph with name `%s` does not exist and can't be removed.",
            graphName
        ));
    }

    public static class UserCatalog {

        private static final UserCatalog EMPTY = new UserCatalog();

        private final Map<String, GraphStoreWithConfig> graphsByName = new ConcurrentHashMap<>();

        private final Map<String, Map<String, Object>> degreeDistributionByName = new ConcurrentHashMap<>();

        void set(GraphCreateConfig config, GraphStore graphStore) {
            if (config.graphName() == null || graphStore == null) {
                throw new IllegalArgumentException("Both name and graph store must be not null");
            }
            GraphStoreWithConfig graphStoreWithConfig = ImmutableGraphStoreWithConfig.of(graphStore, config);
            if (graphsByName.putIfAbsent(config.graphName(), graphStoreWithConfig) != null) {
                throw new IllegalStateException(formatWithLocale(
                    "Graph name %s already loaded",
                    config.graphName()
                ));
            }
            graphStore.canRelease(false);
        }

        public void setDegreeDistribution(String graphName, Map<String, Object> degreeDistribution) {

            if (graphName == null || degreeDistribution == null) {
                throw new IllegalArgumentException("Both name and degreeDistribution must be not null");
            }
            if (!graphsByName.containsKey(graphName)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "Cannot set degreeDistribution because graph %s does not exist",
                    graphName)
                );
            }
            degreeDistributionByName.put(graphName, degreeDistribution);
        }

        public void removeDegreeDistribution(String graphName) {
            degreeDistributionByName.remove(graphName);
        }

        GraphStoreWithConfig get(String graphName) {
            if (graphsByName.containsKey(graphName)) {
                return graphsByName.get(graphName);
            } else {
                throw new NoSuchElementException(formatWithLocale("Cannot find graph with name '%s'.", graphName));
            }
        }

        public Optional<Map<String, Object>> getDegreeDistribution(String graphName) {
            if (!graphsByName.containsKey(graphName)) {
                return Optional.empty();
            }
            return Optional.ofNullable(degreeDistributionByName.get(graphName));
        }

        /**
         * A named graph is potentially split up into multiple sub-graphs.
         * Each sub-graph has the same node set and represents a unique relationship type / property combination.
         * This method returns the union of all subgraphs refered to by the given name.
         */
        Optional<Graph> getUnion(String graphName) {
            return !exists(graphName) ? Optional.empty() : Optional.of(graphsByName.get(graphName).graphStore().getUnion());
        }

        boolean exists(String graphName) {
            return graphName != null && graphsByName.containsKey(graphName);
        }

        @Nullable
        GraphStoreWithConfig remove(String graphName) {
            if (!exists(graphName)) {
                // remove is allowed to return null if the graph does not exist
                // as it's being used by algo.graph.info or algo.graph.remove,
                // that can deal with missing graphs
                return null;
            }
            return graphsByName.remove(graphName);
        }

        Map<GraphCreateConfig, GraphStore> getGraphStores() {
            return graphsByName.values().stream().collect(Collectors.toMap(
                GraphStoreWithConfig::config, GraphStoreWithConfig::graphStore
            ));
        }
    }

}
