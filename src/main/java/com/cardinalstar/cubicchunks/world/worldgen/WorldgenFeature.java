package com.cardinalstar.cubicchunks.world.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.util.Coords;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectObjectMutablePair;

public class WorldgenFeature<TSeed> {

    private final TSeed seed;

    private final XYZMap<BlockOperationSet> pendingOps = new XYZMap<>();

    private final Box.Mutable aabb;

    public final List<WorldgenFeature<TSeed>> branches = new ArrayList<>();

    public WorldgenFeature(int centerX, int centerY, int centerZ, TSeed seed) {
        this.seed = seed;

        centerX = Coords.blockToCube(centerX);
        centerY = Coords.blockToCube(centerY);
        centerZ = Coords.blockToCube(centerZ);

        aabb = new Box.Mutable(centerX, centerY, centerZ, centerX, centerY, centerZ);
    }

    public TSeed getSeed() {
        return seed;
    }

    public void setBlock(int blockX, int blockY, int blockZ, ImmutableBlockMeta bm) {
        aabb.expand(Coords.blockToLocal(blockX), Coords.blockToLocal(blockY), Coords.blockToLocal(blockZ));

        BlockOperationSet ops = pendingOps
            .get(Coords.blockToCube(blockX), Coords.blockToCube(blockY), Coords.blockToCube(blockZ));

        if (ops == null) {
            pendingOps.put(
                ops = new BlockOperationSet(
                    Coords.blockToCube(blockX),
                    Coords.blockToCube(blockY),
                    Coords.blockToCube(blockZ)));
        }

        ops.setBlock(Coords.blockToLocal(blockX), Coords.blockToLocal(blockY), Coords.blockToLocal(blockZ), bm);
    }

    public boolean affects(int cubeX, int cubeY, int cubeZ) {
        return aabb.contains(cubeX, cubeY, cubeZ);
    }

    public Iterable<Pair<Vector3ic, ImmutableBlockMeta>> getOperations(int cubeX, int cubeY, int cubeZ) {
        BlockOperationSet ops = pendingOps.get(cubeX, cubeY, cubeZ);

        return ops == null ? Collections.emptyList() : ops;
    }

    protected static class BlockOperationSet implements Iterable<Pair<Vector3ic, ImmutableBlockMeta>>, XYZAddressable {

        private final int x, y, z;

        private final LongLinkedOpenHashSet coords = new LongLinkedOpenHashSet();
        private final ObjectArrayList<ImmutableBlockMeta> blocks = new ObjectArrayList<>();

        public BlockOperationSet(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

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

        public void setBlock(int x, int y, int z, ImmutableBlockMeta bm) {
            if (coords.add(CoordinatePacker.pack(x, y, z))) {
                blocks.add(bm);
            }
        }

        @Override
        @Nonnull
        public Iterator<Pair<Vector3ic, ImmutableBlockMeta>> iterator() {
            return new Iterator<>() {

                private final LongIterator coordIter = coords.iterator();
                private final ObjectIterator<ImmutableBlockMeta> blockIter = blocks.iterator();

                private final Vector3i v = new Vector3i();
                private final ObjectObjectMutablePair<Vector3ic, ImmutableBlockMeta> pair = ObjectObjectMutablePair
                    .of(v, null);

                @Override
                public boolean hasNext() {
                    return coordIter.hasNext() && blockIter.hasNext();
                }

                @Override
                public Pair<Vector3ic, ImmutableBlockMeta> next() {
                    long k = coordIter.nextLong();

                    v.x = CoordinatePacker.unpackX(k);
                    v.y = CoordinatePacker.unpackY(k);
                    v.z = CoordinatePacker.unpackZ(k);

                    pair.right(blockIter.next());

                    return pair;
                }
            };
        }
    }
}
