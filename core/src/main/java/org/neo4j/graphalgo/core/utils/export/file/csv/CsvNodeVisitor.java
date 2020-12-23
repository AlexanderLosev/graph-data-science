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
package org.neo4j.graphalgo.core.utils.export.file.csv;

import de.siegmar.fastcsv.writer.CsvAppender;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.neo4j.graphalgo.api.schema.NodeSchema;
import org.neo4j.graphalgo.core.utils.export.file.NodeVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvValueFormatter.format;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public class CsvNodeVisitor extends NodeVisitor {

    public static final String ID_COLUMN_NAME = ":ID";

    private final Path fileLocation;
    private final Map<String, CsvAppender> csvAppenders;
    private final CsvWriter csvWriter;

    public CsvNodeVisitor(Path fileLocation, NodeSchema nodeSchema) {
        super(nodeSchema);
        this.fileLocation = fileLocation;
        this.csvAppenders = new HashMap<>();
        this.csvWriter = new CsvWriter();
    }

    @Override
    protected void exportElement() {
        // do the export
        var csvAppender = getAppender();
        try {
            // write Id
            csvAppender.appendField(Long.toString(id()));

            // write properties
            forEachProperty(((key, value, type) -> {
                var propertyString = format(value);
                try {
                    csvAppender.appendField(propertyString);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

            csvAppender.endLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        csvAppenders.values().forEach(csvAppender -> {
            try {
                csvAppender.flush();
                csvAppender.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CsvAppender getAppender() {
        var labelsString = String.join("_", labels());

        return csvAppenders.computeIfAbsent(labelsString, (ignore) -> {
            var fileName = labelsString.isBlank() ? "nodes" : formatWithLocale("nodes_%s", labelsString);
            var headerFileName = formatWithLocale("%s_header.csv", fileName);
            var dataFileName = formatWithLocale("%s.csv", fileName);

            writeHeaderFile(headerFileName);

            try {
                return csvWriter.append(fileLocation.resolve(dataFileName), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void writeHeaderFile(String headerFileName) {
        try (var headerAppender = csvWriter.append(fileLocation.resolve(headerFileName), StandardCharsets.UTF_8)) {
            headerAppender.appendField(ID_COLUMN_NAME);

            forEachProperty(((key, value, type) -> {
                var propertyHeader = formatWithLocale(
                    "%s:%s",
                    key,
                    type.csvName()
                );
                try {
                    headerAppender.appendField(propertyHeader);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

            headerAppender.endLine();
        } catch (IOException e) {
            throw new RuntimeException("Could not write header file", e);
        }
    }
}
