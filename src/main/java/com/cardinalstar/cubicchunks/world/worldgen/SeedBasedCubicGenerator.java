package com.cardinalstar.cubicchunks.world.worldgen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubeTerrainGenerator;
import com.cardinalstar.cubicchunks.util.Bits;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.ObjectPooler;
import com.cardinalstar.cubicchunks.util.TimedCache;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;

public abstract class SeedBasedCubicGenerator<TSeed, TGen extends ICubeGenerator> implements ICubeTerrainGenerator<TGen> {

    private final XSTR rng = new XSTR(0);

    protected final int range;

    private final ObjectPooler<ArrayList<TSeed>> listPool = new ObjectPooler<>(ArrayList::new, ArrayList::clear, 512);

    private final MutableCubePos cubePos = new MutableCubePos();


    private final TimedCache<CubePos, List<TSeed>> seedCache = new TimedCache<>(
        this::getSeedImpl,
        Duration.ofSeconds(10),
        (pos, list) -> {
            //noinspection rawtypes
            if (list instanceof ArrayList arrayList) {
                //noinspection unchecked
                listPool.releaseInstance(arrayList);
            }
        },
        MutableCubePos::clone);

    private long worldSeed;

    /**
     * @param range The range (in cubes) to scan for feature seeds.
     */
    protected SeedBasedCubicGenerator(int range) {
        this.range = range;
    }

    private void setWorldSeed(long worldSeed) {
        if (this.worldSeed != worldSeed) {
            this.worldSeed = worldSeed;
            seedCache.clear();
        }
    }

    private List<TSeed> getSeedImpl(CubePos pos) {
        ArrayList<TSeed> list = listPool.getInstance();

        rng.setSeed(getCubeSeed(pos.getX(), pos.getY(), pos.getZ()));

        getSeeds(rng, pos.getX(), pos.getY(), pos.getZ(), list);

        if (list.isEmpty()) {
            listPool.releaseInstance(list);
            return Collections.emptyList();
        } else {
            return list;
        }
    }

    /**
     * Checks if the given cube has any features, and adds them to the list. The seed is stored in a timed cache, so
     * they must be immutable.
     * @param rng An RNG that is seeded to a deterministic value for this cube.
     */
    protected abstract void getSeeds(Random rng, int cubeX, int cubeY, int cubeZ, List<TSeed> seeds);

    /**
     * Populates the current chunk (determined by {@code pos}) with the given feature seed.
     * @param rng An RNG that is seeded to a deterministic value for this cube.
     */
    protected abstract void generate(Random rng, TSeed seed, WorldView worldView);

    private long getCubeSeed(int cubeX, int cubeY, int cubeZ) {
        long hash = Fnv1a64.initialState();
        hash = Fnv1a64.hashStep(hash, worldSeed);
        hash = Fnv1a64.hashStep(hash, cubeX);
        hash = Fnv1a64.hashStep(hash, cubeY);
        hash = Fnv1a64.hashStep(hash, cubeZ);
        return hash;
    }

    @Override
    public void generate(TGen generator, World world, Cube cube) {
        CubeWorldView worldView = null;

        setWorldSeed(world.getSeed());

        for (int x = cube.getX() - range; x <= cube.getX() + range; x++)
        {
            cubePos.x = x;

            for (int y = cube.getY() - range; y <= cube.getY() + range; y++)
            {
                cubePos.y = y;

                for (int z = cube.getZ() - range; z <= cube.getZ() + range; z++)
                {
                    cubePos.z = z;

                    List<TSeed> seedList = seedCache.get(cubePos);

                    if (seedList.isEmpty()) continue;

                    if (worldView == null) {
                        worldView = new CubeWorldView(world, cube);
                    }

                    long rngSeed = getCubeSeed(cube.getX(), cube.getY(), cube.getZ());

                    for (int i = 0, seedListSize = seedList.size(); i < seedListSize; i++) {
                        TSeed seed = seedList.get(i);

                        rng.setSeed(rngSeed);

                        generate(rng, seed, worldView);
                    }
                }
            }
        }
    }

    /**
     * This is a hacky subclass of CubePos. It's only meant for accessing the TimedCache, and nothing else. The CubePos
     * fields aren't initialized properly, since they're all final, but the various get... methods work properly.
     * This will always return the same hash as a CubePos, and it will always equal a CubePos with the same coord. The
     * opposite is not true - a CubePos will never equal a MutableCubePos.
     */
    private static class MutableCubePos extends CubePos {

        public int x;
        public int y;
        public int z;

        public MutableCubePos() {
            super(0, 0, 0);
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public static CubePos clone(CubePos pos) {
            if (pos instanceof MutableCubePos mutable) {
                return new CubePos(mutable.x, mutable.y, mutable.z);
            } else {
                return pos.clone();
            }
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof CubePos other)) return false;

            return other.getX() == x && other.getY() == y && other.getZ() == z;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(
                Bits.packSignedToLong(x, Y_BITS, Y_BIT_OFFSET) | Bits.packSignedToLong(y, X_BITS, X_BIT_OFFSET)
                    | Bits.packSignedToLong(z, Z_BITS, Z_BIT_OFFSET));
        }
    }
}
