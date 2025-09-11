package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.util.Box;

public class EBSBlockView implements IBlockView {

    public static final Box EBS_BOUNDS = new Box(0, 0, 0, 16, 16, 16);

    private final ExtendedBlockStorage ebs;

    public EBSBlockView(ExtendedBlockStorage ebs) {
        this.ebs = ebs;
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        return ebs == null ? Blocks.air : ebs.getBlockByExtId(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return ebs == null ? 0 : ebs.getExtBlockMetadata(x, y, z);
    }

    @Nullable
    @Override
    public Box getBounds() {
        return EBS_BOUNDS;
    }
}
