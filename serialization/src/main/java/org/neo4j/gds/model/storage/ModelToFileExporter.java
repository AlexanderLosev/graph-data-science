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
package org.neo4j.gds.model.storage;

import org.neo4j.graphalgo.config.BaseConfig;
import org.neo4j.graphalgo.config.ModelConfig;
import org.neo4j.graphalgo.core.model.Model;

import java.io.IOException;
import java.nio.file.Path;

public final class ModelToFileExporter {

    static final String META_DATA_SUFFIX = "meta";
    static final String MODEL_DATA_SUFFIX = "data";

    private ModelToFileExporter() {}

    public static <DATA, CONFIG extends BaseConfig & ModelConfig> void toFile(
        Path exportDir,
        Model<DATA, CONFIG> model,
        ModelExportConfig config
    ) throws IOException {
        new ModelFileWriter<>(exportDir, model, config).write();
    }

    public static <DATA, CONFIG extends BaseConfig & ModelConfig> Model<DATA, CONFIG> fromFile(
        Path exportDir,
        ModelExportConfig config
    ) throws IOException {
        return (Model<DATA, CONFIG>) new ModelFileReader(exportDir, config).read();
    }
}
