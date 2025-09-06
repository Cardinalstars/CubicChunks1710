/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hash table implementation for objects in a 3-dimensional cartesian coordinate
 * system.
 *
 * @param <T> class of the objects to be contained in this map
 *
 * @see XYZAddressable
 */
@ParametersAreNonnullByDefault
public class XYZMap<T extends XYZAddressable> implements Iterable<T> {
    Long2ObjectOpenHashMap<T> items = new Long2ObjectOpenHashMap<>();

    private long key(int x, int y, int z) {
        return ((long)x << 42) | ((long)y << 21) | z;
    }

    public T remove(int x, int y, int z) {
        return items.remove(key(x, y, z));
    }

    public final T get(int x, int y, int z) {
        return items.get(key(x, y, z));
    }

    public final void put(T item) {
        items.put(key(item.getX(), item.getY(), item.getZ()), item);
    }

    public final void remove(T item) {
        remove(item.getX(), item.getY(), item.getZ());
    }

    @Override
    @Nonnull
    public Iterator<T> iterator() {
        return items.values().iterator();
    }

    public int getSize() {
        return items.size();
    }
}
