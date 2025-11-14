package com.cardinalstar.cubicchunks.world.worldgen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubeGenerator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.ObjectPooler;
import com.cardinalstar.cubicchunks.util.TimedCache;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.worldgen.data.FastCubePosMap;
import com.cardinalstar.cubicchunks.world.worldgen.data.MutableCubePos;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;

public abstract class SeedBasedCubicGenerator<TSeed, TGen extends IWorldGenerator> implements ICubeGenerator {

    private final XSTR rng = new XSTR(0);

    protected final int range;

    private final ObjectPooler<ArrayList<TSeed>> listPool = new ObjectPooler<>(ArrayList::new, ArrayList::clear, 512);

    private final MutableCubePos cubePos = new MutableCubePos();

    private final TimedCache<CubePos, List<TSeed>> seedCache = new TimedCache<>(
        new FastCubePosMap<>(),
        this::getSeedImpl,
        new Duration[] { Duration.ofSeconds(10), Duration.ofSeconds(25), Duration.ofSeconds(100) },
        (pos, list) -> {
            // noinspection rawtypes
            if (list instanceof ArrayList arrayList) {
                // noinspection unchecked
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
     *
     * @param rng An RNG that is seeded to a deterministic value for this cube.
     */
    protected abstract void getSeeds(Random rng, int cubeX, int cubeY, int cubeZ, List<TSeed> seeds);

    /**
     * Populates the current chunk (determined by {@code pos}) with the given feature seed.
     *
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
    public void generate(World world, Cube cube) {
        CubeWorldView worldView = null;

        setWorldSeed(world.getSeed());

        for (int x = cube.getX() - range; x <= cube.getX() + range; x++) {
            cubePos.x = x;

            for (int y = cube.getY() - range; y <= cube.getY() + range; y++) {
                cubePos.y = y;

                for (int z = cube.getZ() - range; z <= cube.getZ() + range; z++) {
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
}
