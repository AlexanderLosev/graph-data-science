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
package org.neo4j.graphalgo.config;

import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.configuration.Settings;

public interface ConcurrencyValidation {

    Setting<Boolean> CORE_LIMITATION_SETTING = Settings.setting(
        "gds.unlimited.cores",
        Settings.BOOLEAN,
        "false"
    );

    int CONCURRENCY_LIMITATION = 4;

    @Configuration.Ignore
    default void validateConcurrency() {
        if (this instanceof WriteConfig) {
            WriteConfig wc = (WriteConfig) this;
            Validator.validate(wc.concurrency());
            Validator.validate(wc.writeConcurrency());
        } else if (this instanceof AlgoBaseConfig) {
            AlgoBaseConfig wc = (AlgoBaseConfig) this;
            Validator.validate(wc.concurrency());
        } else if (this instanceof GraphCreateConfig) {
            GraphCreateConfig gcc = (GraphCreateConfig) this;
            Validator.validate(gcc.readConcurrency());
        }
    }

    class Validator {
        private static void validate(int requestedConcurrency) {
            if (requestedConcurrency > CONCURRENCY_LIMITATION) {
                throw new IllegalArgumentException(String.format(
                    "The configured concurrency value is too high. " +
                    "The maximum allowed concurrency value is %d but %d was configured. " +
                    "Please see the documentation for how to increase the limitation.",
                    CONCURRENCY_LIMITATION,
                    requestedConcurrency
                ));
            }
        }
    }
}
