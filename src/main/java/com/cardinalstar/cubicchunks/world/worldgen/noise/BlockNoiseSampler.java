package com.cardinalstar.cubicchunks.world.worldgen.noise;

public interface BlockNoiseSampler {

    double sample(int x, int y);

    double sample(int x, int y, int z);

}
