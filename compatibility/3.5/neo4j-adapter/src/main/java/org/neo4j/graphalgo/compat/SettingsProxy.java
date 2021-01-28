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
package org.neo4j.graphalgo.compat;

import org.neo4j.graphdb.config.Setting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.kernel.configuration.HttpConnector;
import org.neo4j.kernel.configuration.Settings;

import java.io.File;
import java.nio.file.Path;

public final class SettingsProxy {

    private static final Setting<Boolean> CORE_LIMITATION = Settings.setting(
        "gds.enterprise.licensed",
        Settings.BOOLEAN,
        "false"
    );

    public static Setting<Boolean> unlimitedCores() {
        return CORE_LIMITATION;
    }

    public static Setting<Boolean> boltEnabled() {
        return new BoltConnector("bolt").enabled;
    }

    public static Setting<Boolean> httpEnabled() {
        return new HttpConnector("http").enabled;
    }

    public static Setting<Boolean> httpsEnabled() {
        return new HttpConnector("https").enabled;
    }

    public static Setting<Boolean> udc() {
        return Settings.setting("dbms.udc.enabled", Settings.BOOLEAN, "true");
    }

    public static Setting<String> pagecacheMemory() {
        return GraphDatabaseSettings.pagecache_memory;
    }

    public static Setting<Boolean> allowUpgrade() {
        return GraphDatabaseSettings.allow_upgrade;
    }

    public static Setting<Path> storeInternalLogPath() {
        return Settings.derivedSetting(
            "dbms.logs.debug.path",
            GraphDatabaseSettings.logs_directory,
            logs -> logs.toPath().resolve("debug.log"),
            Settings.PATH.andThen(File::toPath)
        );
    }

    private SettingsProxy() {
        throw new UnsupportedOperationException();
    }
}
