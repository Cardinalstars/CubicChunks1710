package com.cardinalstar.cubicchunks.util;

import com.cardinalstar.cubicchunks.api.XYZAddressable;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;

public class CubeCoordIntTriple
{
    private final int cubeX;
    private final int cubeY;
    private final int cubeZ;

    public CubeCoordIntTriple(int cubeX, int cubeY, int cubeZ) {
        this.cubeX = cubeX;
        this.cubeY = cubeY;
        this.cubeZ = cubeZ;
    }

    public CubeCoordIntTriple(XYZAddressable addressable) {
        this.cubeX = addressable.getX();
        this.cubeY = addressable.getY();
        this.cubeZ = addressable.getZ();
    }
    public static long cubeXYZToLong(int x, int y, int z) {
        return ((long)x & 0x3FFFFF) << 42 | ((long)y & 0xFFFFF) << 22 | ((long)z & 0x3FFFFF);
    }

    /**
     * Gets the x position of the cube in the world.
     *
     * @return The x position.
     */
    public int getX() {
        return this.cubeX;
    }

    /**
     * Gets the y position of the cube in the world.
     *
     * @return The y position.
     */
    public int getY() {
        return this.cubeY;
    }

    /**
     * Gets the zPosition position of the cube in the world.
     *
     * @return The zPosition position.
     */
    public int getZ() {
        return this.cubeZ;
    }

    public static CubeCoordIntTriple fromBlockCoords(int x, int y, int z) {
        return new CubeCoordIntTriple(blockToCube(x), blockToCube(y), blockToCube(z));
    }

    public int getMinBlockX()
    {
        return Coords.cubeToMinBlock(cubeX);
    }

    public int getMinBlockY() {
        return Coords.cubeToMinBlock(cubeY);
    }

    public int getMinBlockZ()
    {
        return Coords.cubeToMinBlock(cubeZ);
    }
    public CubeCoordIntTriple getMinBlockPos()
    {
        return new CubeCoordIntTriple(getMinBlockX(), getMinBlockY(), getMinBlockZ());
    }

    public int getMaxBlockX()
    {
        return Coords.cubeToMaxBlock(cubeX);
    }

    public int getMaxBlockY()
    {
        return Coords.cubeToMaxBlock(cubeY);
    }

    public int getMaxBlockZ()
    {
        return Coords.cubeToMaxBlock(cubeZ);
    }

    public CubeCoordIntTriple getMaxBlockPos()
    {
        return new CubeCoordIntTriple(getMaxBlockX(), getMaxBlockY(), getMaxBlockZ());
    }

    public CubeCoordIntTriple add(int x, int y, int z)
    {
        return x == 0 && y == 0 && z == 0 ? this : new CubeCoordIntTriple(this.getX() + x, this.getY() + y, this.getZ() + z);
    }
}
