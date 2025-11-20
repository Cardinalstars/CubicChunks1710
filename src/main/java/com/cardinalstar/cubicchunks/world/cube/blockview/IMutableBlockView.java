package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/**
 * A view into a world-like structure. This may be a World, a Chunk, an EBS, or anything else. The coordinate space is
 * implementation and context dependent.
 */
public interface IMutableBlockView extends IBlockView {

    default IMutableBlockView subViewMutable(Box box) {
        Box thisBox = getBounds();

        if (thisBox != null && !thisBox.contains(box))
            throw new IllegalArgumentException("sub view box must be completely contained within parent view's bounds");

        return new SubMutableBlockView(this, box);
    }

    void setBlock(int x, int y, int z, @Nonnull Block block);

    void setBlockMetadata(int x, int y, int z, int meta);

    void setBlock(int x, int y, int z, @Nonnull Block block, int meta);

    default void setBlock(int x, int y, int z, @Nonnull ImmutableBlockMeta bm) {
        setBlock(x, y, z, bm.getBlock(), bm.getBlockMeta());
    }

    default void fill(@Nonnull ImmutableBlockMeta bm) {
        for (Vector3ic v : getBounds()) {
            setBlock(v.x(), v.y(), v.z(), bm);
        }
    }
}
