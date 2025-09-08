package com.cardinalstar.cubicchunks.world.cube.blockview;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;

import com.cardinalstar.cubicchunks.api.util.Box;

public class SubBlockView implements IBlockView {

    private final IBlockView blockView;
    protected final Box box;

    private Box effectiveBox;

    public SubBlockView(IBlockView blockView, Box box) {
        this.blockView = blockView;
        this.box = box;
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        validateCoords(x, y, z);

        x += box.getX1();
        y += box.getY1();
        z += box.getZ1();

        return blockView.getBlock(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        validateCoords(x, y, z);

        x += box.getX1();
        y += box.getY1();
        z += box.getZ1();

        return blockView.getBlockMetadata(x, y, z);
    }

    public Box getBox() {
        if (effectiveBox == null) {
            effectiveBox = new Box(0, 0, 0, box.getX2() - box.getX1(), box.getY2() - box.getY1(), box.getZ2() - box.getZ1());
        }

        return effectiveBox;
    }

    protected final void validateCoords(int x, int y, int z) {
        if (x < 0 || x >= (box.getX2() - box.getX1())) throw new IllegalArgumentException(String.format("illegal argument: x (x=%s, x1=%s, x2=%s)", x, box.getX1(), box.getX2()));
        if (y < 0 || y >= (box.getY2() - box.getY1())) throw new IllegalArgumentException(String.format("illegal argument: y (y=%s, y1=%s, y2=%s)", y, box.getY1(), box.getY2()));
        if (z < 0 || z >= (box.getZ2() - box.getZ1())) throw new IllegalArgumentException(String.format("illegal argument: z (z=%s, z1=%s, z2=%s)", z, box.getZ1(), box.getZ2()));
    }
}
