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
package org.neo4j.graphalgo.compat;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.SettingValueParsers;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.connectors.HttpsConnector;
import org.neo4j.graphdb.config.Setting;

import java.nio.file.Path;

import static org.neo4j.configuration.SettingImpl.newBuilder;

public final class Settings {

    public static Setting<Boolean> unlimitedCores() {
        return ConcurrencyControllerSettings.unlimitedCores;
    }

    public static Setting<Boolean> boltEnabled() {
        return BoltConnector.enabled;
    }

    public static Setting<Boolean> httpEnabled() {
        return HttpConnector.enabled;
    }

    public static Setting<Boolean> httpsEnabled() {
        return HttpsConnector.enabled;
    }

    public static Setting<Boolean> udc() {
        return newBuilder("dbms.udc.enabled", SettingValueParsers.BOOL, true).build();
    }

    public static Setting<String> pagecacheMemory() {
        return GraphDatabaseSettings.pagecache_memory;
    }

    public static Setting<Boolean> allowUpgrade() {
        return GraphDatabaseSettings.allow_upgrade;
    }

    public static Setting<Path> storeInternalLogPath() {
        return GraphDatabaseSettings.store_internal_log_path;
    }

    private Settings() {
        throw new UnsupportedOperationException();
    }
}
