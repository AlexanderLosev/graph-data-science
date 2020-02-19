/*
 * Copyright (c) 2017-2019 "Neo4j,"
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
package org.neo4j.graphalgo.doc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.GetNodeFunc;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.catalog.GraphCreateProc;
import org.neo4j.graphalgo.core.loading.GraphCatalog;
import org.neo4j.graphalgo.labelpropagation.LabelPropagationStreamProc;
import org.neo4j.graphalgo.labelpropagation.LabelPropagationWriteProc;
import org.neo4j.graphalgo.pagerank.PageRankStreamProc;
import org.neo4j.graphalgo.pagerank.PageRankWriteProc;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;

import java.io.File;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRankDocTest extends DocTestBase {

    @BeforeEach
    void setUp() throws Exception {
        db = TestDatabaseCreator.createTestDatabase(
            builder -> builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, "gds.*")
        );
        registerProcedures(PageRankStreamProc.class, PageRankWriteProc.class, GraphCreateProc.class);
        registerFunctions(GetNodeFunc.class);
    }

    @AfterEach
    void tearDown() {
        asciidoctor.shutdown();
        db.shutdown();
        GraphCatalog.removeAllLoadedGraphs();
    }

    @Test
    void should() {
        asciidoctor
            .javaExtensionRegistry()
            .treeprocessor(defaultQueryConsumingTreeProcessor());
        File file = ASCIIDOC_PATH.resolve("algorithms/pagerank.adoc").toFile();
        assertTrue(file.exists() && file.canRead());
        asciidoctor.loadFile(file, Collections.emptyMap());
    }

}

