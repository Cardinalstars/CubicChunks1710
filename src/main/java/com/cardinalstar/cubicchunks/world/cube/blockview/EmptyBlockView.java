package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public class EmptyBlockView implements IMutableBlockView {

    public static final EmptyBlockView INSTANCE = new EmptyBlockView();

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block) {

    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {

    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block, int meta) {

    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return 0;
    }
}
