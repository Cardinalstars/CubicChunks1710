package com.cardinalstar.cubicchunks.world.worldgen.noise;

public interface NoiseSampler {

    double sample(double x, double y);
    double sample(double x, double y, double z);

}
