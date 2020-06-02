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
package org.neo4j.graphalgo.core.utils.container;

import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectScatterMap;
import com.carrotsearch.hppc.procedures.IntObjectProcedure;
import com.carrotsearch.hppc.procedures.ObjectProcedure;
import org.neo4j.graphalgo.utils.StringJoining;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Pathmap (map for list of nodes)
 */
public class Paths {

    public static final int INITIAL_PATH_CAPACITY = 100;

    private final IntObjectMap<Path> paths;

    public Paths() {
        this(10000);
    }

    public Paths(int expectedElements) {
        paths = new IntObjectScatterMap<>(expectedElements);
    }

    public void append(int pathId, int nodeId) {
        final Path path;
        if (!paths.containsKey(pathId)) {
            path = new Path(INITIAL_PATH_CAPACITY);
            paths.put(pathId, path);
        } else {
            path = paths.get(pathId);
        }
        path.append(nodeId);
    }

    public int size(int pathId) {
        return paths.containsKey(pathId) ? paths.get(pathId).size() : 0;
    }

    public void forEach(int pathId, IntPredicate consumer) {
        if (paths.containsKey(pathId)) {
            paths.get(pathId).forEach(consumer);
        }
    }

    public void clear() {
        paths.values().forEach((ObjectProcedure<Path>) Path::clear);
    }

    public void clear(int pathId) {
        final Path path = paths.get(pathId);
        if (null != path) {
            path.clear();
        }
    }

    @Override
    public String toString() {
        var pathStrings = new ArrayList<String>(paths.size());

        paths.forEach((IntObjectProcedure<Path>) (nodeId, path) -> {
            pathStrings.add(String.format(Locale.ENGLISH, "%d = %s", nodeId, path));
        });

        return StringJoining.join(pathStrings, ", ");
    }
}
