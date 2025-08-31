package com.cardinalstar.cubicchunks.util;

public class ChunkStorageUtils {

    public static int getBlockIndex(int x, int y, int z) {
        return x << 8 | z << 4 | y;
    }
}
