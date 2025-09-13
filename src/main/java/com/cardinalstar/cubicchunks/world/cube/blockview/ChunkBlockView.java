package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.world.ICubicWorld;

public class ChunkBlockView implements IMutableBlockView {

    private final Chunk chunk;

    private Box bounds;

    public ChunkBlockView(Chunk chunk) {
        this.chunk = chunk;
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block) {
        setBlock(x, y, z, block, getBlockMetadata(x, y, z));
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        chunk.setBlockMetadata(x, y, z, meta);
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block, int meta) {
        chunk.func_150807_a(x, y, z, block, meta);
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        return chunk.getBlock(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return chunk.getBlockMetadata(x, y, z);
    }

    @Override
    public Box getBounds() {
        if (bounds == null) {
            bounds = Box.horizontalChunkSlice(0, ((ICubicWorld) chunk.worldObj).getMaxGenerationHeight());
        }

        return bounds;
    }
}
