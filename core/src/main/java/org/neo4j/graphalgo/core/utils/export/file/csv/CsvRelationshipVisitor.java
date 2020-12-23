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
import org.jetbrains.annotations.TestOnly;
import org.neo4j.graphalgo.api.schema.RelationshipSchema;
import org.neo4j.graphalgo.core.utils.export.file.RelationshipVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public class CsvRelationshipVisitor extends RelationshipVisitor {

    public static final String START_ID_COLUMN_NAME = ":START_ID";
    public static final String END_ID_COLUMN_NAME = ":END_ID";

    private final Path fileLocation;
    private final Set<String> headerFiles;
    private final int visitorId;
    private final Map<String, CsvAppender> csvAppenders;
    private final CsvWriter csvWriter;

    public CsvRelationshipVisitor(
        Path fileLocation,
        RelationshipSchema relationshipSchema,
        Set<String> headerFiles,
        int visitorId
    ) {
        super(relationshipSchema);
        this.fileLocation = fileLocation;
        this.headerFiles = headerFiles;
        this.visitorId = visitorId;
        this.csvAppenders = new HashMap<>();
        this.csvWriter = new CsvWriter();
    }

    @TestOnly
    CsvRelationshipVisitor(
        Path fileLocation,
        RelationshipSchema relationshipSchema
    ) {
        this(fileLocation, relationshipSchema, new HashSet<>(), 0);
    }

    @Override
    protected void exportElement() {
        // do the import
        var csvAppender = getAppender();
        try {
            // write start and end nodes
            csvAppender.appendField(Long.toString(startNode()));
            csvAppender.appendField(Long.toString(endNode()));

            // write properties
            forEachProperty(((key, value, type) -> {
                var propertyString = type.csvValue(value);
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
        return csvAppenders.computeIfAbsent(relationshipType(), (ignore) -> {
            var fileName = formatWithLocale("relationships_%s", relationshipType());
            var headerFileName = formatWithLocale("%s_header.csv", fileName);
            var dataFileName = formatWithLocale("%s_%d.csv", fileName, visitorId);

            if (headerFiles.add(headerFileName)) {
                writeHeaderFile(headerFileName);
            }

            try {
                return csvWriter.append(fileLocation.resolve(dataFileName), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void writeHeaderFile(String headerFileName) {
        try (var headerAppender = csvWriter.append(fileLocation.resolve(headerFileName), StandardCharsets.UTF_8)) {
            headerAppender.appendField(START_ID_COLUMN_NAME);
            headerAppender.appendField(END_ID_COLUMN_NAME);

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
