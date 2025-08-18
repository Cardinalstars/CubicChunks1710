package com.cardinalstar.cubicchunks.util;

public class ChunkStorageUtils
{
    public static int getBlockIndex(int x, int y, int z)
    {
        return x << 12 | z << 8 | y;
    }

    public static int getCubeBlockIndexFromChunkData(int x, int y, int z, int cubeY)
    {
        return getBlockIndex(x, y | cubeY, z);
    }
}
