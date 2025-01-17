/*
 * Copyright (c) "Neo4j"
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
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.kernel.database.NamedDatabaseId;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.graphalgo.core.StringSimilarity.prettySuggestions;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public final class GraphStoreCatalog {

    private static final ConcurrentHashMap<String, UserCatalog> userCatalogs = new ConcurrentHashMap<>();

    private GraphStoreCatalog() { }

    public static GraphStoreWithConfig get(String username, NamedDatabaseId databaseId, String graphName) {
        return getUserCatalog(username).get(UserCatalog.UserCatalogKey.of(databaseId, graphName));
    }

    public static void set(GraphCreateConfig config, GraphStore graphStore) {
        graphStore.canRelease(false);
        userCatalogs.compute(config.username(), (user, userCatalog) -> {
            if (userCatalog == null) {
                userCatalog = new UserCatalog();
            }
            userCatalog.set(
                UserCatalog.UserCatalogKey.of(graphStore.databaseId(), config.graphName()),
                config,
                graphStore
            );
            return userCatalog;
        });
    }

    public static boolean exists(String username, NamedDatabaseId databaseId, String graphName) {
        return getUserCatalog(username).exists(UserCatalog.UserCatalogKey.of(databaseId, graphName));
    }

    public static void remove(
        String username,
        NamedDatabaseId databaseId,
        String graphName,
        Consumer<GraphStoreWithConfig> removedGraphConsumer,
        boolean failOnMissing
    ) {
        remove(username, databaseId.name(), graphName, removedGraphConsumer, failOnMissing);
    }

    public static void remove(
        String username,
        String databaseName,
        String graphName,
        Consumer<GraphStoreWithConfig> removedGraphConsumer,
        boolean failOnMissing
    ) {
        getUserCatalog(username).remove(
            UserCatalog.UserCatalogKey.of(databaseName, graphName),
            removedGraphConsumer,
            failOnMissing
        );
    }

    public static int graphStoresCount() {
        return userCatalogs
            .values()
            .stream()
            .mapToInt(userCatalog -> userCatalog.getGraphStores().values().size())
            .sum();
    }

    public static int graphStoresCount(NamedDatabaseId databaseId) {
        return userCatalogs
            .values()
            .stream()
            .mapToInt(userCatalog -> userCatalog.getGraphStores(databaseId).values().size())
            .sum();
    }

    public static boolean isEmpty() {
        return graphStoresCount() == 0;
    }

    public static Optional<Map<String, Object>> getDegreeDistribution(
        String username,
        NamedDatabaseId databaseId,
        String graphName
    ) {
        return getUserCatalog(username).getDegreeDistribution(UserCatalog.UserCatalogKey.of(databaseId, graphName));
    }

    public static void setDegreeDistribution(
        String username,
        NamedDatabaseId databaseId,
        String graphName,
        Map<String, Object> degreeDistribution
    ) {
        getUserCatalog(username).setDegreeDistribution(
            UserCatalog.UserCatalogKey.of(databaseId, graphName),
            degreeDistribution
        );
    }

    public static void removeAllLoadedGraphs() {
        userCatalogs.clear();
    }

    public static void removeAllLoadedGraphs(NamedDatabaseId databaseId) {
        userCatalogs.forEach((user, userCatalog) -> userCatalog.remove(databaseId.name()));
    }

    public static Map<GraphCreateConfig, GraphStore> getGraphStores(String username) {
        return getUserCatalog(username).getGraphStores();
    }

    public static Map<GraphCreateConfig, GraphStore> getGraphStores(String username, NamedDatabaseId databaseId) {
        return getUserCatalog(username).getGraphStores(databaseId);
    }

    public static Stream<GraphStoreWithUserNameAndConfig> getAllGraphStores() {
        return userCatalogs
            .entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().streamGraphStores(entry.getKey()));
    }

    private static UserCatalog getUserCatalog(String username) {
        return userCatalogs.getOrDefault(username, UserCatalog.EMPTY);
    }

    @ValueClass
    public interface GraphStoreWithUserNameAndConfig {

        GraphStore graphStore();

        String userName();

        GraphCreateConfig config();
    }

    static class UserCatalog {

        @ValueClass
        public interface UserCatalogKey {

            String graphName();

            String databaseName();

            static UserCatalogKey of(GraphCreateConfig createConfig, String databaseName) {
                return of(databaseName, createConfig.graphName());
            }

            static UserCatalogKey of(NamedDatabaseId databaseId, String graphName) {
                return of(databaseId.name(), graphName);
            }

            static UserCatalogKey of(String databaseName, String graphName) {
                return ImmutableUserCatalogKey.of(graphName, databaseName);
            }
        }

        private static final UserCatalog EMPTY = new UserCatalog();

        private final Map<UserCatalogKey, GraphStoreWithConfig> graphsByName = new ConcurrentHashMap<>();

        private final Map<UserCatalogKey, Map<String, Object>> degreeDistributionByName = new ConcurrentHashMap<>();

        private void set(UserCatalogKey userCatalogKey, GraphCreateConfig config, GraphStore graphStore) {
            if (config.graphName() == null || graphStore == null) {
                throw new IllegalArgumentException("Both name and graph store must be not null");
            }
            GraphStoreWithConfig graphStoreWithConfig = GraphStoreWithConfig.of(graphStore, config);

            if (graphsByName.putIfAbsent(userCatalogKey, graphStoreWithConfig) != null) {
                throw new IllegalStateException(formatWithLocale(
                    "Graph name %s already loaded",
                    config.graphName()
                ));
            }
            graphStore.canRelease(false);
        }

        private void setDegreeDistribution(UserCatalogKey userCatalogKey, Map<String, Object> degreeDistribution) {

            if (userCatalogKey == null || degreeDistribution == null) {
                throw new IllegalArgumentException("Both name and degreeDistribution must be not null");
            }
            if (!graphsByName.containsKey(userCatalogKey)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "Cannot set degreeDistribution because graph %s does not exist",
                    userCatalogKey.graphName()
                ));
            }
            degreeDistributionByName.put(userCatalogKey, degreeDistribution);
        }

        private void removeDegreeDistribution(UserCatalogKey userCatalogKey) {
            degreeDistributionByName.remove(userCatalogKey);
        }

        private GraphStoreWithConfig get(UserCatalogKey userCatalogKey) {
            //noinspection ConstantConditions
            return get(userCatalogKey, true);
        }

        private @Nullable GraphStoreWithConfig get(UserCatalogKey userCatalogKey, boolean failOnMissing) {
            var graphStoreWithConfig = graphsByName.get(userCatalogKey);

            if (graphStoreWithConfig == null && failOnMissing) {
                var graphName = userCatalogKey.graphName();

                var availableGraphNames = graphsByName
                    .keySet()
                    .stream()
                    .map(UserCatalogKey::graphName)
                    .collect(Collectors.toList());

                throw new NoSuchElementException(prettySuggestions(
                    formatWithLocale(
                        "Graph with name `%s` does not exist on database `%s`.",
                        graphName,
                        userCatalogKey.databaseName()
                    ),
                    graphName,
                    availableGraphNames
                ));
            }

            return graphStoreWithConfig;
        }

        private Optional<Map<String, Object>> getDegreeDistribution(UserCatalogKey userCatalogKey) {
            if (!graphsByName.containsKey(userCatalogKey)) {
                return Optional.empty();
            }
            return Optional.ofNullable(degreeDistributionByName.get(userCatalogKey));
        }

        private boolean exists(UserCatalogKey userCatalogKey) {
            return userCatalogKey != null && graphsByName.containsKey(userCatalogKey);
        }

        private void remove(
            UserCatalogKey userCatalogKey,
            Consumer<GraphStoreWithConfig> removedGraphConsumer,
            boolean failOnMissing
        ) {
            Optional.ofNullable(get(userCatalogKey, failOnMissing)).ifPresent(graphStoreWithConfig -> {
                removedGraphConsumer.accept(graphStoreWithConfig);
                graphStoreWithConfig.graphStore().canRelease(true);
                graphStoreWithConfig.graphStore().release();
                removeDegreeDistribution(userCatalogKey);
                graphsByName.remove(userCatalogKey);
            });
        }

        private void remove(String databaseName) {
            graphsByName.keySet().removeIf(userCatalogKey -> userCatalogKey.databaseName().equals(databaseName));
        }

        private Stream<GraphStoreWithUserNameAndConfig> streamGraphStores(String userName) {
            return graphsByName
                .values()
                .stream()
                .map(graphStoreWithConfig -> ImmutableGraphStoreWithUserNameAndConfig.of(
                    graphStoreWithConfig.graphStore(),
                    userName,
                    graphStoreWithConfig.config()
                ));
        }

        private Map<GraphCreateConfig, GraphStore> getGraphStores() {
            return graphsByName.values().stream()
                .collect(Collectors.toMap(
                    GraphStoreWithConfig::config,
                    GraphStoreWithConfig::graphStore
                    )
                );
        }

        private Map<GraphCreateConfig, GraphStore> getGraphStores(NamedDatabaseId databaseId) {
            return graphsByName.entrySet().stream()
                .filter(entry -> entry.getKey().databaseName().equals(databaseId.name()))
                .collect(Collectors.toMap(
                    entry -> entry.getValue().config(),
                    entry -> entry.getValue().graphStore()
                    )
                );
        }
    }

}
