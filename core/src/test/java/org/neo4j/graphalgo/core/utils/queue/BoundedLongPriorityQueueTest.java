/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.core.utils.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedLongPriorityQueueTest {

    @Test
    void shouldKeepMinValues() {
        List<Long> expected = new ArrayList<>();
        expected.add(0L);
        expected.add(1L);
        expected.add(2L);

        BoundedLongPriorityQueue queue = BoundedLongPriorityQueue.min(3);

        assertTrue(queue.offer(0, 0.0));
        assertTrue(queue.offer(6, 6.0));
        assertTrue(queue.offer(1, 1.0));
        assertTrue(queue.offer(5, 5.0));
        assertTrue(queue.offer(2, 2.0));
        assertFalse(queue.offer(4, 4.0));
        assertFalse(queue.offer(3, 3.0));

        List<Long> actual = queue.elements().boxed().collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    void shouldKeepMaxValues() {
        List<Long> expected = new ArrayList<>();
        expected.add(6L);
        expected.add(5L);
        expected.add(4L);

        BoundedLongPriorityQueue queue = BoundedLongPriorityQueue.max(3);

        assertTrue(queue.offer(0, 0.0));
        assertTrue(queue.offer(6, 6.0));
        assertTrue(queue.offer(1, 1.0));
        assertTrue(queue.offer(5, 5.0));
        assertTrue(queue.offer(2, 2.0));
        assertTrue(queue.offer(4, 4.0));
        assertFalse(queue.offer(3, 3.0));

        List<Long> actual = queue.elements().boxed().collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    void shouldLimitReturnWhenNotFull() {
        List<Long> expected = new ArrayList<>();
        expected.add(6L);
        expected.add(5L);
        expected.add(4L);

        BoundedLongPriorityQueue queue = BoundedLongPriorityQueue.max(10);

        assertTrue(queue.offer(6, 6.0));
        assertTrue(queue.offer(5, 5.0));
        assertTrue(queue.offer(4, 4.0));

        List<Long> actual = queue.elements().boxed().collect(Collectors.toList());
        assertEquals(expected, actual);
    }
}
