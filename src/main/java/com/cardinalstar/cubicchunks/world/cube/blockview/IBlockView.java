package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;

import com.cardinalstar.cubicchunks.api.util.Box;

/**
 * A view into a world-like structure. This may be a World, a Chunk, an EBS, or anything else. The coordinate space is
 * implementation and context dependent.
 */
public interface IBlockView {

    @Nonnull
    Block getBlock(int x, int y, int z);

    int getBlockMetadata(int x, int y, int z);

    default IBlockView subView(Box box) {
        Box thisBox = getBounds();

        if (thisBox != null && !thisBox.contains(box))
            throw new IllegalArgumentException("sub view box must be completely contained within parent view's bounds");

        return new SubBlockView(this, box);
    }

    /**
     * Gets the bounds of this view, if possible. Returns null when the view is unbounded.
     */
    default Box getBounds() {
        return null;
    }
}
