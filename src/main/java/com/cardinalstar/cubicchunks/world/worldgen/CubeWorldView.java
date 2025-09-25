package com.cardinalstar.cubicchunks.world.worldgen;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public class CubeWorldView implements WorldView {

    private final World world;
    private final Cube cube;

    private Box box;

    private final int startX, startY, startZ, endX, endY, endZ;

    public CubeWorldView(World world, Cube cube) {
        this.world = world;
        this.cube = cube;

        startX = cube.getX() * 16;
        startY = cube.getY() * 16;
        startZ = cube.getZ() * 16;

        endX = startX + 16;
        endY = startY + 16;
        endZ = startZ + 16;
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block) {
        validateCoords(x, y, z);

        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        ebs.func_150818_a(x & 0xF, y & 0xF, z & 0xF, block);
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        validateCoords(x, y, z);

        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        ebs.setExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF, meta);
    }

    @Override
    public void setBlock(int x, int y, int z, @Nonnull Block block, int meta) {
        validateCoords(x, y, z);

        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        ebs.func_150818_a(x & 0xF, y & 0xF, z & 0xF, block);
        ebs.setExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF, meta);
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        validateCoords(x, y, z);

        ExtendedBlockStorage ebs = cube.getStorage();

        return ebs == null ? Blocks.air : ebs.getBlockByExtId(x & 0xF, y & 0xF, z & 0xF);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        validateCoords(x, y, z);

        ExtendedBlockStorage ebs = cube.getStorage();

        return ebs == null ? 0 : ebs.getExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF);
    }

    @Override
    public Box getBounds() {
        if (box == null) {
            box = new Box(startX, startY, startZ, endX, endY, endZ);
        }

        return box;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public CubePos getCube() {
        return cube.getCoords();
    }

    @Override
    public BiomeGenBase getBiomeGenForBlock(int x, int y, int z) {
        validateCoords(x, y, z);

        return cube.getBiome(x & 0xF, y & 0xF, z & 0xF);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return x >= startX && x < endX && y >= startY && y < endY && z >= startZ && z < endZ;
    }

    protected final void validateCoords(int x, int y, int z) {
        if (x < startX || x >= endX) throw new IllegalArgumentException(
            String.format("illegal argument: x must fulfill min <= x < max (x=%s, min=%s, max=%s)", x, startX, endX));
        if (y < startY || y >= endY) throw new IllegalArgumentException(
            String.format("illegal argument: y must fulfill min <= y < max (y=%s, min=%s, max=%s)", y, startY, endY));
        if (z < startZ || z >= endZ) throw new IllegalArgumentException(
            String.format("illegal argument: z must fulfill min <= z < max (z=%s, min=%s, max=%s)", z, startZ, endZ));
    }
}
