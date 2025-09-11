package com.cardinalstar.cubicchunks.util;

import java.util.BitSet;
import java.util.Iterator;

import javax.annotation.Nonnull;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.google.common.collect.AbstractIterator;

public class CubeBitSet implements Iterable<Vector3ic> {

    private final BitSet bits = new BitSet(16 * 16 * 16);

    public boolean get(int x, int y, int z) {
        return bits.get(getIndex(x, y, z));
    }

    public void set(int x, int y, int z) {
        bits.set(getIndex(x, y, z));
    }

    public void clear(int x, int y, int z) {
        bits.clear(getIndex(x, y, z));
    }

    @Override
    @Nonnull
    public Iterator<Vector3ic> iterator() {
        return new AbstractIterator<>() {
            private int bit = -1;

            private final Vector3i v = new Vector3i();

            @Override
            protected Vector3ic computeNext() {
                bit = bits.nextSetBit(bit + 1);

                if (bit == -1) {
                    endOfData();
                    return null;
                }

                v.x = bit & 0xF;
                v.y = (bit >> 4) & 0xF;
                v.z = (bit >> 8) & 0xF;

                return v;
            }
        };
    }

    private static int getIndex(int x, int y, int z) {
        if ((x & 0xF) != x) throw new IllegalArgumentException("x: " + x + " must be between 0 to 16 (exclusive)");
        if ((y & 0xF) != y) throw new IllegalArgumentException("y: " + y + " must be between 0 to 16 (exclusive)");
        if ((z & 0xF) != z) throw new IllegalArgumentException("z: " + z + " must be between 0 to 16 (exclusive)");

        return x + (y << 4) + (z << 8);
    }
}
