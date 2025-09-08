package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public class CubeBlockView implements IMutableBlockView {

    private final Cube cube;

    public CubeBlockView(Cube cube) {
        this.cube = cube;
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block) {
        cube.getOrCreateStorage().func_150818_a(x, y, z, block);
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        cube.getOrCreateStorage().setExtBlockMetadata(x, y, z, meta);
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block, int meta) {
        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        ebs.func_150818_a(x, y, z, block);
        ebs.setExtBlockMetadata(x, y, z, meta);
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        ExtendedBlockStorage ebs = cube.getStorage();
        return ebs == null ? Blocks.air : ebs.getBlockByExtId(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        ExtendedBlockStorage ebs = cube.getStorage();
        return ebs == null ? 0 : ebs.getExtBlockMetadata(x, y, z);
    }

    @Override
    public Box getBounds() {
        return EBSBlockView.EBS_BOUNDS;
    }
}
