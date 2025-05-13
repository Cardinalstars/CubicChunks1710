package com.cardinalstar.cubicchunks.util;

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
     * Gets the z position of the cube in the world.
     *
     * @return The z position.
     */
    public int getZ() {
        return this.cubeZ;
    }

    public static CubeCoordIntTriple fromBlockCoords(int x, int y, int z) {
        return new CubeCoordIntTriple(blockToCube(x), blockToCube(y), blockToCube(z));
    }

}
