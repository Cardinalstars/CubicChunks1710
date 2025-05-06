package com.cardinalstar.cubicworlds.util;
public class CubeCoordIntTriple
{
    public static long cubeXYZToLong(int x, int y, int z) {
        return ((long)x & 0x3FFFFF) << 42 | ((long)y & 0xFFFFF) << 22 | ((long)z & 0x3FFFFF);
    }
}
