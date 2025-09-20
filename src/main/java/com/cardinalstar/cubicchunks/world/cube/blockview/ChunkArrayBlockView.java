package com.cardinalstar.cubicchunks.world.cube.blockview;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.cardinalstar.cubicchunks.api.util.Box;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * A block view for data with a chunk-like layout (y major followed by z then x).
 */
public class ChunkArrayBlockView implements IMutableBlockView {

    private final int spanX;
    private final int spanY;
    private final int spanZ;
    private final Int2ObjectFunction<Block> blocks;
    private final Int2IntFunction metas;

    private Box bounds;

    public ChunkArrayBlockView(int spanX, int spanY, int spanZ, List<Block> blocks, IntList meta) {
        this.spanX = spanX;
        this.spanY = spanY;
        this.spanZ = spanZ;

        this.blocks = new Int2ObjectFunction<>() {

            @Override
            public Block get(int key) {
                return blocks.get(key);
            }

            @Override
            public Block put(int key, Block value) {
                return blocks.set(key, value);
            }
        };

        this.metas = new Int2IntFunction() {

            @Override
            public int get(int key) {
                return meta.getInt(key);
            }

            @Override
            public int put(int key, int value) {
                return meta.set(key, value);
            }
        };
    }

    public ChunkArrayBlockView(int spanX, int spanY, int spanZ, Int2ObjectFunction<Block> blocks,
        Int2IntFunction meta) {
        this.spanX = spanX;
        this.spanY = spanY;
        this.spanZ = spanZ;
        this.blocks = blocks;
        this.metas = meta;
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

        return metas.get(i);
    }

    private int getIndex(int x, int y, int z) {
        if (x < 0 || x >= spanX) return -1;
        if (y < 0 || y >= spanY) return -1;
        if (z < 0 || z >= spanZ) return -1;

        int i = y;

        i += z * spanY;
        i += x * spanY * spanZ;

        return i;
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block) {
        int i = getIndex(x, y, z);

        if (i == -1) return;

        blocks.put(i, block);
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        int i = getIndex(x, y, z);

        if (i == -1) return;

        metas.put(i, meta);
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block, int meta) {
        int i = getIndex(x, y, z);

        if (i == -1) return;

        blocks.put(i, block);
        metas.put(i, meta);
    }

    @Nullable
    @Override
    public Box getBounds() {
        if (bounds == null) {
            bounds = new Box(0, 0, 0, spanX, spanY, spanZ);
        }

        return bounds;
    }
}
