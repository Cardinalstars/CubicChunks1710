package com.cardinalstar.cubicchunks.world.worldgen.data;

import java.util.Random;

import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;

public interface SamplerFactory {

    NoiseSampler createSampler(Random rng);
}
