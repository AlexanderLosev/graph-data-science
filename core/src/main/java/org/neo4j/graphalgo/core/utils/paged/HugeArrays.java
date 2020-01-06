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
package org.neo4j.graphalgo.core.utils.paged;

final class HugeArrays {

    static final int PAGE_SHIFT = 14;
    static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final long PAGE_MASK = PAGE_SIZE - 1;

    static int pageIndex(long index) {
        return (int) (index >>> PAGE_SHIFT);
    }

    static int indexInPage(long index) {
        return (int) (index & PAGE_MASK);
    }

    static int exclusiveIndexOfPage(long index) {
        return 1 + (int) ((index - 1L) & PAGE_MASK);
    }

    static int numberOfPages(long capacity) {
        final long numPages = (capacity + PAGE_MASK) >>> PAGE_SHIFT;
        assert numPages <= Integer.MAX_VALUE : "pageSize=" + (PAGE_SIZE) + " is too small for capacity: " + capacity;
        return (int) numPages;
    }

    private HugeArrays() {
        throw new UnsupportedOperationException("No instances");
    }
}
