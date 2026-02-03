package com.cardinalstar.cubicchunks.world.cube.blockview;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class SafeMutableBlockView implements IMutableBlockView {

    private final Box box;
    private final IMutableBlockView next;

    public SafeMutableBlockView(Box box, IMutableBlockView next) {
        this.box = box;
        this.next = next;
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block) {
        if (box.contains(x, y, z)) {
            next.setBlock(x, y, z, block);
        }
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        if (box.contains(x, y, z)) {
            next.setBlockMetadata(x, y, z, meta);
        }
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block, int meta) {
        if (box.contains(x, y, z)) {
            next.setBlock(x, y, z, block, meta);
        }
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull ImmutableBlockMeta bm) {
        if (box.contains(x, y, z)) {
            next.setBlock(x, y, z, bm);
        }
    }

    @Override
    public @NotNull Block getBlock(int x, int y, int z) {
        if (box.contains(x, y, z)) {
            return next.getBlock(x, y, z);
        } else {
            return Blocks.air;
        }
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        if (box.contains(x, y, z)) {
            return next.getBlockMetadata(x, y, z);
        } else {
            return 0;
        }
    }

    @Override
    public IBlockView subView(Box box) {
        return new SubBlockView(this, box);
    }

    @Override
    public IMutableBlockView subViewMutable(Box box) {
        return new SubMutableBlockView(this, box);
    }

    @Override
    public Box getBounds() {
        return box;
    }
}
