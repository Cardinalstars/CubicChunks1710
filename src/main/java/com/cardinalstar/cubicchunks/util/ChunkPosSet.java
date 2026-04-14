package com.cardinalstar.cubicchunks.util;

import java.util.Iterator;

import net.minecraft.world.ChunkCoordIntPair;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SuppressWarnings("unused")
public class ChunkPosSet extends LongOpenHashSet {

    public boolean contains(int blockX, int blockZ) {
        return super.contains(ChunkCoordIntPair.chunkXZ2Int(blockX, blockZ));
    }

    public boolean contains(XZAddressable xyz) {
        return contains(xyz.getX(), xyz.getZ());
    }

    public boolean remove(int blockX, int blockZ) {
        return super.remove(ChunkCoordIntPair.chunkXZ2Int(blockX, blockZ));
    }

    public boolean remove(XZAddressable xyz) {
        return remove(xyz.getX(), xyz.getZ());
    }

    public boolean add(int blockX, int blockZ) {
        return super.add(ChunkCoordIntPair.chunkXZ2Int(blockX, blockZ));
    }

    public boolean add(XZAddressable xyz) {
        return add(xyz.getX(), xyz.getZ());
    }

    public Iterator<XZAddressable> fastIterator() {
        LongIterator iter = super.iterator();

        class MutableXZ implements XZAddressable {

            public int x, z;

            @Override
            public int getX() {
                return x;
            }

            @Override
            public int getZ() {
                return z;
            }
        }

        MutableXZ pos = new MutableXZ();

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public XZAddressable next() {
                long l = iter.nextLong();

                pos.x = (int) (l);
                pos.z = (int) (l >> 32);

                return pos;
            }
        };
    }

    public Iterable<XZAddressable> fastEntryIterable() {
        return this::fastIterator;
    }
}
