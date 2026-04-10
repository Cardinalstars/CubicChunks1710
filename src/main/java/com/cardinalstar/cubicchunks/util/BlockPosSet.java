package com.cardinalstar.cubicchunks.util;

import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.pack;

import java.util.Iterator;

import com.cardinalstar.cubicchunks.api.XYZAddressable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SuppressWarnings("unused")
public class BlockPosSet extends LongOpenHashSet {

    public boolean contains(int blockX, int blockY, int blockZ) {
        return super.contains(pack(blockX, blockY, blockZ));
    }

    public boolean contains(XYZAddressable xyz) {
        return contains(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public boolean remove(int blockX, int blockY, int blockZ) {
        return super.remove(pack(blockX, blockY, blockZ));
    }

    public boolean remove(XYZAddressable xyz) {
        return remove(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public boolean add(int blockX, int blockY, int blockZ) {
        return super.add(pack(blockX, blockY, blockZ));
    }

    public boolean add(XYZAddressable xyz) {
        return add(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public Iterator<XYZAddressable> fastIterator() {
        LongIterator iter = super.iterator();

        class MutableXYZ implements XYZAddressable {

            public int x, y, z;

            @Override
            public int getX() {
                return x;
            }

            @Override
            public int getY() {
                return y;
            }

            @Override
            public int getZ() {
                return z;
            }
        }

        MutableXYZ pos = new MutableXYZ();

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public XYZAddressable next() {
                long l = iter.nextLong();

                pos.x = Coords.x(l);
                pos.y = Coords.y(l);
                pos.z = Coords.z(l);

                return pos;
            }
        };
    }

    public Iterable<XYZAddressable> fastEntryIterable() {
        return this::fastIterator;
    }
}
