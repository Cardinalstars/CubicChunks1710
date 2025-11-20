package com.cardinalstar.cubicchunks.util;

import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.pack;

import java.util.Iterator;

import org.joml.Vector3i;
import org.joml.Vector3ic;

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

    public Iterator<Vector3ic> fastIterator() {
        LongIterator iter = super.iterator();

        Vector3i v = new Vector3i();

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Vector3ic next() {
                long l = iter.nextLong();

                v.x = Coords.x(l);
                v.y = Coords.y(l);
                v.z = Coords.z(l);

                return v;
            }
        };
    }

    public Iterable<Vector3ic> fastEntryIterable() {
        return this::fastIterator;
    }
}
