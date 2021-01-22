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
package org.neo4j.graphalgo.core.utils.export.file.csv;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.core.utils.export.file.GraphStoreToFileExporter;
import org.neo4j.graphalgo.core.utils.export.file.ImmutableGraphStoreToFileExporterConfig;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.extension.GdlExtension;
import org.neo4j.graphalgo.extension.GdlGraph;
import org.neo4j.graphalgo.extension.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeVisitor.ID_COLUMN_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvRelationshipVisitor.END_ID_COLUMN_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvRelationshipVisitor.START_ID_COLUMN_NAME;

@GdlExtension
class GraphStoreToFileExporterTest extends CsvTest {

    @GdlGraph
    private static final String GDL =
        "CREATE" +
        "  (a:A:B { prop1: 0, prop2: 42, prop3: [1L, 3L, 3L, 7L]})" +
        ", (b:A:B { prop1: 1, prop2: 43})" +
        ", (c:A:C { prop1: 2, prop2: 44, prop3: [1L, 9L, 8L, 4L] })" +
        ", (d:B { prop1: 3 })" +
        ", (a)-[:REL1 { prop1: 0, prop2: 42 }]->(a)" +
        ", (a)-[:REL1 { prop1: 1, prop2: 43 }]->(b)" +
        ", (b)-[:REL1 { prop1: 2, prop2: 44 }]->(a)" +
        ", (b)-[:REL2 { prop3: 3, prop4: 45 }]->(c)" +
        ", (c)-[:REL2 { prop3: 4, prop4: 46 }]->(d)" +
        ", (d)-[:REL2 { prop3: 5, prop4: 47 }]->(a)";

    @Inject
    public GraphStore graphStore;

    @GdlGraph(graphNamePrefix = "concurrent")
    private static final String GDL_FOR_CONCURRENCY =
        "CREATE" +
        "  (a)" +
        ", (b)" +
        ", (c)" +
        ", (d)" +
        ", (a)-[:REL1]->(a)" +
        ", (a)-[:REL1]->(b)" +
        ", (b)-[:REL1]->(a)" +
        ", (b)-[:REL1]->(c)" +
        ", (c)-[:REL1]->(d)" +
        ", (d)-[:REL1]->(a)";

    @Inject
    public GraphStore concurrentGraphStore;
    public static final List<String> NODE_COLUMNS = List.of(ID_COLUMN_NAME);
    public static final List<String> RELATIONSHIP_COLUMNS = List.of(START_ID_COLUMN_NAME, END_ID_COLUMN_NAME);

    @Test
    void exportTopology() {
        var config = ImmutableGraphStoreToFileExporterConfig
            .builder()
            .exportName(tempDir.toString())
            .writeConcurrency(1)
            .build();

        // export db
        var exporter = GraphStoreToFileExporter.csv(graphStore, config, tempDir);
        exporter.run(AllocationTracker.empty());

        var aLabel = NodeLabel.of("A");
        var bLabel = NodeLabel.of("B");
        var cLabel = NodeLabel.of("C");
        var rel1Type = RelationshipType.of("REL1");
        var rel2Type = RelationshipType.of("REL2");

        var abSchema = graphStore.schema().nodeSchema().filter(Set.of(aLabel, bLabel)).unionProperties();
        var acSchema = graphStore.schema().nodeSchema().filter(Set.of(aLabel, cLabel)).unionProperties();
        var bSchema = graphStore.schema().nodeSchema().filter(Set.of(bLabel)).unionProperties();
        var rel1Schema = graphStore.schema().relationshipSchema().filter(Set.of(rel1Type)).unionProperties();
        var rel2Schema = graphStore.schema().relationshipSchema().filter(Set.of(rel2Type)).unionProperties();

        assertCsvFiles(List.of(
            "nodes_A_B_0.csv", "nodes_A_B_header.csv",
            "nodes_A_C_0.csv", "nodes_A_C_header.csv",
            "nodes_B_0.csv", "nodes_B_header.csv",
            "relationships_REL1_0.csv", "relationships_REL1_header.csv",
            "relationships_REL2_0.csv", "relationships_REL2_header.csv"
        ));

        // Assert nodes

        assertHeaderFile("nodes_A_B_header.csv", NODE_COLUMNS, abSchema);
        assertDataContent(
            "nodes_A_B_0.csv",
            List.of(
                List.of("0", "0", "42", "1;3;3;7"),
                List.of("1", "1", "43", "")
            )
        );

        assertHeaderFile("nodes_A_C_header.csv", NODE_COLUMNS, acSchema);
        assertDataContent(
            "nodes_A_C_0.csv",
            List.of(
                List.of("2", "2", "44", "1;9;8;4")
            )
        );

        assertHeaderFile("nodes_B_header.csv", NODE_COLUMNS, bSchema);
        assertDataContent(
            "nodes_B_0.csv",
            List.of(
                List.of("3", "3", "", "")
            )
        );

        // assert relationships

        assertHeaderFile("relationships_REL1_header.csv", RELATIONSHIP_COLUMNS, rel1Schema);
        assertDataContent(
            "relationships_REL1_0.csv",
            List.of(
                List.of("0", "0", "42.0"),
                List.of("0", "1", "43.0"),
                List.of("1", "0", "44.0")
            )
        );

        assertHeaderFile("relationships_REL2_header.csv", RELATIONSHIP_COLUMNS, rel2Schema);
        assertDataContent(
            "relationships_REL2_0.csv",
            List.of(
                List.of("1", "2", "45.0"),
                List.of("2", "3", "46.0"),
                List.of("3", "0", "47.0")
            )
        );

    }

    @Test
    void exportMultithreaded() {
        var config = ImmutableGraphStoreToFileExporterConfig
            .builder()
            .exportName(tempDir.toString())
            .writeConcurrency(2)
            .build();

        // export db
        var exporter = GraphStoreToFileExporter.csv(concurrentGraphStore, config, tempDir);
        exporter.run(AllocationTracker.empty());

        // Assert headers
        assertHeaderFile("nodes_header.csv", NODE_COLUMNS, Collections.emptyMap());
        assertHeaderFile("relationships_REL1_header.csv", RELATIONSHIP_COLUMNS, Collections.emptyMap());

        // Sometimes we end up with only one file, so we cannot make absolute assumptions about the files created
        var nodeContents = Arrays.stream(tempDir
            .toFile()
            .listFiles((file, name) -> name.startsWith("nodes") && !name.contains("header")))
            .map(File::toPath)
            .flatMap(path -> {
                try {
                    return Files.readAllLines(path).stream();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        assertThat(nodeContents).containsExactlyInAnyOrder("0", "1", "2", "3");

        var relationshipContents = Arrays.stream(tempDir
            .toFile()
            .listFiles((file, name) -> name.startsWith("relationships") && !name.contains("header")))
            .map(File::toPath)
            .flatMap(path -> {
                try {
                    return Files.readAllLines(path).stream();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        assertThat(relationshipContents).containsExactlyInAnyOrder(
            "0,0",
            "0,1",
            "1,0",
            "1,2",
            "2,3",
            "3,0"
        );
    }
}
