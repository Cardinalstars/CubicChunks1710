package com.cardinalstar.cubicchunks.world.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public abstract class FeatureCubicGenerator<TSeed, TGen extends IWorldGenerator>
    extends SeedBasedCubicGenerator<WorldgenFeature<TSeed>, TGen> {

    protected FeatureCubicGenerator(int range) {
        super(range);
    }

    private final ArrayList<TSeed> seeds = new ArrayList<>();

    @Override
    protected final void getSeeds(Random rng, int cubeX, int cubeY, int cubeZ, List<WorldgenFeature<TSeed>> features) {
        getSeedsImpl(rng, cubeX, cubeY, cubeZ, seeds);

        for (TSeed seed : seeds) {
            generateFeature(features, seed);
        }

        seeds.clear();
    }

    private void generateFeature(List<WorldgenFeature<TSeed>> features, TSeed seed) {
        WorldgenFeature<TSeed> feature = new WorldgenFeature<>(getSeedX(seed), getSeedY(seed), getSeedZ(seed), seed);

        generateSeed(seed, feature);

        features.add(feature);

        // Must be done after the seed is generated
        Collection<TSeed> branches = getSeedBranches(seed);

        if (!branches.isEmpty()) {
            for (TSeed branch : branches) {
                generateFeature(features, branch);
            }
        }
    }

    /**
     * Checks if the given cube has any features, and adds them to the list. The seed is stored in a timed cache, so
     * they must be immutable.
     * 
     * @param rng An RNG that is seeded to a deterministic value for this cube.
     */
    protected abstract void getSeedsImpl(Random rng, int cubeX, int cubeY, int cubeZ, List<TSeed> seeds);

    /**
     * Walks through the feature's blocks and inserts the operations into the feature. This is only done once per seed.
     */
    protected abstract void generateSeed(TSeed seed, WorldgenFeature<TSeed> feature);

    @Override
    protected final void generate(Random rng, WorldgenFeature<TSeed> feature, WorldView worldView) {
        Box box = worldView.getBounds();

        CubePos pos = worldView.getCube();

        if (feature.affects(pos.getX(), pos.getY(), pos.getZ()) && shouldGenerate(worldView, feature)) {
            for (var p : feature.getOperations(pos.getX(), pos.getY(), pos.getZ())) {
                int x = p.left()
                    .x() + box.getX1();
                int y = p.left()
                    .y() + box.getY1();
                int z = p.left()
                    .z() + box.getZ1();

                place(worldView, feature, x, y, z, p.right());
            }
        }

        for (WorldgenFeature<TSeed> branch : feature.branches) {
            generate(rng, branch, worldView);
        }
    }

    protected boolean shouldGenerate(WorldView worldView, WorldgenFeature<TSeed> feature) {
        return true;
    }

    protected void place(WorldView worldView, WorldgenFeature<TSeed> feature, int blockX, int blockY, int blockZ,
        ImmutableBlockMeta bm) {
        worldView.setBlock(blockX, blockY, blockZ, bm);
    }

    protected abstract int getSeedX(TSeed seed);

    protected abstract int getSeedY(TSeed seed);

    protected abstract int getSeedZ(TSeed seed);

    /** Gets a seeds recursions/branches, if any. */
    protected Collection<TSeed> getSeedBranches(TSeed seed) {
        return Collections.emptyList();
    }
}
