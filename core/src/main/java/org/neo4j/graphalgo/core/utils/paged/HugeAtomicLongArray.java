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
package org.neo4j.graphalgo.core.utils.paged;

import org.neo4j.graphalgo.core.utils.BitUtil;
import org.neo4j.graphalgo.core.write.PropertyTranslator;
import org.neo4j.unsafe.impl.internal.dragons.UnsafeUtil;

import java.util.Arrays;
import java.util.function.IntToLongFunction;
import java.util.function.LongUnaryOperator;

import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfInstance;
import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfLongArray;
import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfObjectArray;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.PAGE_SHIFT;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.PAGE_SIZE;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.SINGLE_PAGE_SIZE;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.exclusiveIndexOfPage;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.indexInPage;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.numberOfPages;
import static org.neo4j.graphalgo.core.utils.paged.HugeArrays.pageIndex;

/**
 * A long-indexable version of a primitive long array ({@code long[]}) that can contain more than 2 bn. elements.
 * <p>
 * It is implemented by paging of smaller long-arrays ({@code long[][]}) to support approx. 32k bn. elements.
 * If the the provided size is small enough, an optimized view of a single {@code long[]} might be used.
 * <p>
 * <ul>
 * <li>The array is of a fixed size and cannot grow or shrink dynamically.</li>
 * <li>The array is not optimized for sparseness and has a large memory overhead if the values written to it are very sparse (see {@link org.neo4j.graphalgo.core.huge.loader.SparseNodeMapping} for a different implementation that can profit from sparse data).</li>
 * <li>The array does not support default values and returns the same default for unset values that a regular {@code long[]} does ({@code 0}).</li>
 * </ul>
 * <p>
 * <h3>Basic Usage</h3>
 * <pre>
 * {@code}
 * AllocationTracker tracker = ...;
 * long arraySize = 42L;
 * HugeLongArray array = HugeLongArray.newArray(arraySize, tracker);
 * array.set(13L, 37L);
 * long value = array.get(13L);
 * // value = 37L
 * {@code}
 * </pre>
 *
 * @author phorn@avantgarde-labs.de
 */
public abstract class HugeAtomicLongArray {

    /**
     * @return the long value at the given index
     * @throws ArrayIndexOutOfBoundsException if the index is not within {@link #size()}
     */
    public abstract long get(long index);

    /**
     * Sets the long value at the given index to the given value.
     *
     * @throws ArrayIndexOutOfBoundsException if the index is not within {@link #size()}
     */
    public abstract void set(long index, long value);

    /**
     * Atomically sets the element at position {@code index} to the given
     * updated value if the current value {@code ==} the expected value.
     *
     * @param index  the index
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that
     *         the actual value was not equal to the expected value.
     */
    public abstract boolean compareAndSet(long index, long expect, long update);

    /**
     * Atomically updates the element at index {@code index} with the results
     * of applying the given function, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param index          the index
     * @param updateFunction a side-effect-free function
     */
    public abstract void update(long index, LongUnaryOperator updateFunction);

    /**
     * Returns the length of this array.
     * <p>
     * If the size is greater than zero, the highest supported index is {@code size() - 1}
     * <p>
     * The behavior is identical to calling {@code array.length} on primitive arrays.
     */
    public abstract long size();

    /**
     * @return the amount of memory used by the instance of this array, in bytes.
     *         This should be the same as returned from {@link #release()} without actually releasing the array.
     */
    public abstract long sizeOf();

    /**
     * Destroys the data, allowing the underlying storage arrays to be collected as garbage.
     * The array is unusable after calling this method and will throw {@link NullPointerException}s on virtually every method invocation.
     * <p>
     * Note that the data might not immediately collectible if there are still cursors alive that reference this array.
     * You have to {@link HugeCursor#close()} every cursor instance as well.
     * <p>
     * The amount is not removed from the {@link AllocationTracker} that had been provided in the constructor.
     *
     * @return the amount of memory freed, in bytes.
     */
    public abstract long release();

    /**
     * Creates a new array of the given size, tracking the memory requirements into the given {@link AllocationTracker}.
     * The tracker is no longer referenced, as the arrays do not dynamically change their size.
     */
    public static HugeAtomicLongArray newArray(long size, AllocationTracker tracker) {
        return newArray(size, null, tracker);
    }

    /**
     * Creates a new array of the given size, tracking the memory requirements into the given {@link AllocationTracker}.
     * The tracker is no longer referenced, as the arrays do not dynamically change their size.
     * The values are pre-calculated according to the semantics of {@link Arrays#setAll(long[], IntToLongFunction)}
     */
    public static HugeAtomicLongArray newArray(long size, LongUnaryOperator gen, AllocationTracker tracker) {
        if (size <= SINGLE_PAGE_SIZE) {
            return SingleHugeAtomicLongArray.of(size, gen, tracker);
        }
        return PagedHugeAtomicLongArray.of(size, gen, tracker);
    }

    public static long memoryEstimation(long size) {
        assert size >= 0;
        long instanceSize;
        long dataSize;
        if (size <= SINGLE_PAGE_SIZE) {
            instanceSize = sizeOfInstance(SingleHugeAtomicLongArray.class);
            dataSize = sizeOfLongArray((int) size);
        } else {
            instanceSize = sizeOfInstance(PagedHugeAtomicLongArray.class);
            dataSize = PagedHugeAtomicLongArray.memoryUsageOfData(size);
        }
        return instanceSize + dataSize;
    }

    /* test-only */
    static HugeAtomicLongArray newPagedArray(long size, final LongUnaryOperator gen, AllocationTracker tracker) {
        return PagedHugeAtomicLongArray.of(size, gen, tracker);
    }

    /* test-only */
    static HugeAtomicLongArray newSingleArray(int size, final LongUnaryOperator gen, AllocationTracker tracker) {
        return SingleHugeAtomicLongArray.of(size, gen, tracker);
    }

    /**
     * A {@link PropertyTranslator} for instances of {@link HugeAtomicLongArray}s.
     */
    public static class Translator implements PropertyTranslator.OfLong<HugeAtomicLongArray> {

        public static final Translator INSTANCE = new Translator();

        @Override
        public long toLong(final HugeAtomicLongArray data, final long nodeId) {
            return data.get(nodeId);
        }
    }

    private static final int base;
    private static final int shift;

    static {
        UnsafeUtil.assertHasUnsafe();
        base = UnsafeUtil.arrayBaseOffset(long[].class);
        int scale = UnsafeUtil.arrayIndexScale(long[].class);
        if (!BitUtil.isPowerOfTwo(scale)) {
            throw new Error("data type scale not a power of two");
        }
        shift = 31 - Integer.numberOfLeadingZeros(scale);
    }

    private static long byteOffset(int i) {
        return ((long) i << shift) + base;
    }

    private static final class SingleHugeAtomicLongArray extends HugeAtomicLongArray {

        private static HugeAtomicLongArray of(long size, LongUnaryOperator gen, AllocationTracker tracker) {
            assert size <= SINGLE_PAGE_SIZE;
            final int intSize = (int) size;
            tracker.add(sizeOfLongArray(intSize));
            long[] page = new long[intSize];
            if (gen != null) {
                Arrays.setAll(page, gen::applyAsLong);
            }
            return new SingleHugeAtomicLongArray(intSize, page);
        }

        private final int size;
        private long[] page;

        private SingleHugeAtomicLongArray(int size, long[] page) {
            this.size = size;
            this.page = page;
        }

        @Override
        public long get(long index) {
            assert index < size;
            return getRaw(byteOffset((int) index));
        }

        @Override
        public void set(long index, long value) {
            assert index < size;
            UnsafeUtil.putLongVolatile(page, byteOffset((int) index), value);
        }

        @Override
        public boolean compareAndSet(long index, long expect, long update) {
            assert index < size;
            return compareAndSetRaw(byteOffset((int) index), expect, update);
        }

        @Override
        public void update(long index, LongUnaryOperator updateFunction) {
            assert index < size;
            long offset = byteOffset((int) index);
            long prev, next;
            do {
                prev = getRaw(offset);
                next = updateFunction.applyAsLong(prev);
            } while (!compareAndSetRaw(offset, prev, next));
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public long sizeOf() {
            return sizeOfLongArray(size);
        }

        @Override
        public long release() {
            if (page != null) {
                page = null;
                return sizeOfLongArray(size);
            }
            return 0L;
        }

        private long getRaw(long offset) {
            return UnsafeUtil.getLongVolatile(page, offset);
        }

        private boolean compareAndSetRaw(long offset, long expect, long update) {
            return UnsafeUtil.compareAndSwapLong(page, offset, expect, update);
        }
    }

    private static final class PagedHugeAtomicLongArray extends HugeAtomicLongArray {

        private static HugeAtomicLongArray of(long size, LongUnaryOperator gen, AllocationTracker tracker) {
            int numPages = numberOfPages(size);
            int lastPage = numPages - 1;
            final int lastPageSize = exclusiveIndexOfPage(size);

            long[][] pages = new long[numPages][];
            for (int i = 0; i < lastPage; i++) {
                pages[i] = new long[PAGE_SIZE];
                if (gen != null) {
                    long base = ((long) i) << PAGE_SHIFT;
                    Arrays.setAll(pages[i], j -> gen.applyAsLong(base + j));
                }
            }
            pages[lastPage] = new long[lastPageSize];
            if (gen != null) {
                long base = ((long) lastPage) << PAGE_SHIFT;
                Arrays.setAll(pages[lastPage], j -> gen.applyAsLong(base + j));
            }

            long memoryUsed = memoryUsageOfData(size);
            tracker.add(memoryUsed);
            return new PagedHugeAtomicLongArray(size, pages, memoryUsed);
        }

        private static long memoryUsageOfData(long size) {
            int numberOfPages = numberOfPages(size);
            int numberOfFullPages = numberOfPages - 1;
            long bytesPerPage = sizeOfLongArray(PAGE_SIZE);
            int sizeOfLastPast = exclusiveIndexOfPage(size);
            long bytesOfLastPage = sizeOfLongArray(sizeOfLastPast);
            long memoryUsed = sizeOfObjectArray(numberOfPages);
            memoryUsed += (numberOfFullPages * bytesPerPage);
            memoryUsed += bytesOfLastPage;
            return memoryUsed;
        }

        private final long size;
        private long[][] pages;
        private final long memoryUsed;

        private PagedHugeAtomicLongArray(long size, long[][] pages, long memoryUsed) {
            this.size = size;
            this.pages = pages;
            this.memoryUsed = memoryUsed;
        }

        @Override
        public long get(long index) {
            assert index < size && index >= 0;
            int pageIndex = pageIndex(index);
            int indexInPage = indexInPage(index);
            return getRaw(pages[pageIndex], byteOffset(indexInPage));
        }

        @Override
        public void set(long index, long value) {
            assert index < size && index >= 0;
            int pageIndex = pageIndex(index);
            int indexInPage = indexInPage(index);
            UnsafeUtil.putLongVolatile(pages[pageIndex], byteOffset(indexInPage), value);
        }

        @Override
        public boolean compareAndSet(long index, long expect, long update) {
            assert index < size && index >= 0;
            int pageIndex = pageIndex(index);
            int indexInPage = indexInPage(index);
            return compareAndSetRaw(pages[pageIndex], byteOffset(indexInPage), expect, update);
        }

        @Override
        public void update(long index, LongUnaryOperator updateFunction) {
            assert index < size && index >= 0;
            int pageIndex = pageIndex(index);
            int indexInPage = indexInPage(index);
            long[] page = pages[pageIndex];
            long offset = byteOffset(indexInPage);
            long prev, next;
            do {
                prev = getRaw(page, offset);
                next = updateFunction.applyAsLong(prev);
            } while (!compareAndSetRaw(page, offset, prev, next));
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public long sizeOf() {
            return memoryUsed;
        }

        @Override
        public long release() {
            if (pages != null) {
                pages = null;
                return memoryUsed;
            }
            return 0L;
        }

        private long getRaw(long[] page, long offset) {
            return UnsafeUtil.getLongVolatile(page, offset);
        }

        private boolean compareAndSetRaw(long[] page, long offset, long expect, long update) {
            return UnsafeUtil.compareAndSwapLong(page, offset, expect, update);
        }
    }
}
