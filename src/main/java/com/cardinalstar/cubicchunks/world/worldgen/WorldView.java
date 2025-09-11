package com.cardinalstar.cubicchunks.world.worldgen;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.blockview.IMutableBlockView;

/**
 * A subsection of the world, used for feature population. All coordinates are in world-space.
 */
public interface WorldView extends IMutableBlockView {

    /**
     * Use very sparingly! Any operations to the world can cause cascading worldgen! Note that the cube that is being
     * currently generated isn't in the world.
     */
    World getWorld();

    CubePos getCube();

    BiomeGenBase getBiomeGenForBlock(int x, int y, int z);

    boolean contains(int blockX, int blockY, int blockZ);
}
