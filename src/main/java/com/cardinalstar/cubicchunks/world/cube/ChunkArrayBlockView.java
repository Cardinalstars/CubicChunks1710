package com.cardinalstar.cubicchunks.world.cube;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntIntPair;

/**
 * A block view for data with a chunk-like layout (y major followed by z then x).
 */
public class ChunkArrayBlockView implements IBlockView {

    private final int spanX;
    private final int spanY;
    private final int spanZ;
    /**
     * The section scissor. When set, the left value is the lower Y bound and the right value is the upper Y bound.
     * With a given Y, the effective Y value is Y+scissorY.leftInt(). This allows you to only examine one EBS within a
     * chunk's arrays without having any special index logic or needing to copy anything.
     */
    @Nullable
    private final IntIntPair scissorY;
    private final Int2ObjectFunction<Block> blocks;
    private final Int2IntFunction meta;

    public ChunkArrayBlockView(int spanX, int spanY, int spanZ, Int2ObjectFunction<Block> blocks, Int2IntFunction meta) {
        this.spanX = spanX;
        this.spanY = spanY;
        this.spanZ = spanZ;
        this.scissorY = null;
        this.blocks = blocks;
        this.meta = meta;
    }

    public ChunkArrayBlockView(int spanX, int spanY, int spanZ, int sectionIndex, int sectionHeight, Int2ObjectFunction<Block> blocks, Int2IntFunction meta) {
        this.spanX = spanX;
        this.spanY = spanY;
        this.spanZ = spanZ;
        this.scissorY = IntIntPair.of(sectionIndex * sectionHeight, (sectionIndex + 1) * sectionHeight);
        this.blocks = blocks;
        this.meta = meta;
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        int i = getIndex(x, y, z);

        if (i == -1) return Blocks.air;

        Block block = blocks.get(i);

        return block == null ? Blocks.air : block;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        int i = getIndex(x, y, z);

        if (i == -1) return 0;

        return meta.get(i);
    }

    private int getIndex(int x, int y, int z) {
        if (x < 0 || x >= spanX) return -1;
        if (y < 0 || y >= spanY) return -1;
        if (z < 0 || z >= spanZ) return -1;

        int i = y;

        if (scissorY != null) {
            if (y > (scissorY.rightInt() - scissorY.leftInt())) return -1;

            i += scissorY.leftInt();
        }

        i += z * spanY;
        i += x * spanY * spanZ;

        return i;
    }
}
