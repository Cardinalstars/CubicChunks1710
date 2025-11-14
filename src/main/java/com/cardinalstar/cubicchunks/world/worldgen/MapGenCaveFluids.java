package com.cardinalstar.cubicchunks.world.worldgen;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.CubeBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.IMutableBlockView;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class MapGenCaveFluids implements ICubePopulator {

    private static final ForgeDirection[] BLOCK_MASK = {
        ForgeDirection.NORTH,
        ForgeDirection.SOUTH,
        ForgeDirection.EAST,
        ForgeDirection.WEST
    };

    private final XSTR rng = new XSTR();

    private final ImmutableBlockMeta fluid;

    private final int chances;

    public MapGenCaveFluids(ImmutableBlockMeta fluid) {
        this(fluid, 10);
    }

    public MapGenCaveFluids(ImmutableBlockMeta fluid, int chances) {
        this.fluid = fluid;
        this.chances = chances;
    }

    @Override
    public void populate(World world, Cube cube) {
        if (cube.getY() >= 0) return;

        IMutableBlockView cubeView = new CubeBlockView(cube);

        long seed = Fnv1a64.initialState();

        seed = Fnv1a64.hashStep(seed, world.getSeed());
        seed = Fnv1a64.hashStep(seed, cube.getX());
        seed = Fnv1a64.hashStep(seed, cube.getY());
        seed = Fnv1a64.hashStep(seed, cube.getZ());

        rng.setSeed(seed);

        outer: for (int i = 0; i < chances; i++) {
            int x = rng.nextInt(14) + 1;
            int y = rng.nextInt(14) + 1;
            int z = rng.nextInt(14) + 1;

            if (cubeView.getBlock(x, y, z) != Blocks.stone) continue;
            if (cubeView.getBlock(x, y - 1, z) != Blocks.stone) continue;
            if (cubeView.getBlock(x, y + 1, z) != Blocks.stone) continue;

            int valid = 0;

            for (ForgeDirection dir : BLOCK_MASK) {
                Block block = cubeView.getBlock(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);

                if (block == Blocks.stone) {
                    valid++;
                } else if (block != Blocks.air) {
                    continue outer;
                }
            }

            if (valid != 3) continue;

            cubeView.setBlock(x, y, z, fluid);

            int globalX = x + cube.getX() * 16;
            int globalY = y + cube.getY() * 16;
            int globalZ = z + cube.getZ() * 16;

            fluid.getBlock().onNeighborBlockChange(world, globalX, globalY, globalZ, Blocks.air);

            world.scheduledUpdatesAreImmediate = true;
            fluid.getBlock().updateTick(world, globalX, globalY, globalZ, world.rand);
            world.scheduledUpdatesAreImmediate = false;

            break;
        }
    }
}
