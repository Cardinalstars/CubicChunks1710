package com.cardinalstar.cubicchunks.world.cube;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;

/**
 * A view into a world-like structure. This may be a World, a Chunk, an EBS, or anything else. The coordinate space is
 * implementation and context dependent.
 */
public interface IBlockView {

    @Nonnull
    Block getBlock(int x, int y, int z);

    int getBlockMetadata(int x, int y, int z);
}
