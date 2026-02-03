package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;

import com.cardinalstar.cubicchunks.api.util.Box;

public class SubMutableBlockView extends SubBlockView implements IMutableBlockView {

    private final IMutableBlockView blockView;

    public SubMutableBlockView(IMutableBlockView blockView, Box box) {
        super(blockView, box);
        this.blockView = blockView;
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block) {
        validateCoords(x, y, z);

        x += box.getX1();
        y += box.getY1();
        z += box.getZ1();

        blockView.setBlock(x, y, z, block);
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        validateCoords(x, y, z);

        x += box.getX1();
        y += box.getY1();
        z += box.getZ1();

        blockView.setBlockMetadata(x, y, z, meta);
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block, int meta) {
        validateCoords(x, y, z);

        x += box.getX1();
        y += box.getY1();
        z += box.getZ1();

        blockView.setBlock(x, y, z, block, meta);
    }
}
