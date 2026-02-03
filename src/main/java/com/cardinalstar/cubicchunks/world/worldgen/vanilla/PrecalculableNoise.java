package com.cardinalstar.cubicchunks.world.worldgen.vanilla;

/// A noise layer that can be precalculated
public interface PrecalculableNoise {

    void precalculate(int blockX, int blockY, int blockZ);

}
