package com.cardinalstar.cubicchunks.world.cube.blockview;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.util.Box;

public class SafeBlockView implements IBlockView {

    private final Box box;
    private final IBlockView next;

    public SafeBlockView(Box box, IBlockView next) {
        this.box = box;
        this.next = next;
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
    public Box getBounds() {
        return box;
    }
}
