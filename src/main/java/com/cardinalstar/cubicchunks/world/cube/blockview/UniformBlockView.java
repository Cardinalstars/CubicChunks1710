package com.cardinalstar.cubicchunks.world.cube.blockview;

import net.minecraft.block.Block;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class UniformBlockView implements IBlockView {

    public final ImmutableBlockMeta bm;

    public UniformBlockView(ImmutableBlockMeta bm) {
        this.bm = bm;
    }

    @Override
    public @NotNull Block getBlock(int x, int y, int z) {
        return bm.getBlock();
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return bm.getBlockMeta();
    }
}
