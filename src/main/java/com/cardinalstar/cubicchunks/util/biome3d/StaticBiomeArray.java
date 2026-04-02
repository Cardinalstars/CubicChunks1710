package com.cardinalstar.cubicchunks.util.biome3d;

import net.minecraft.world.biome.BiomeGenBase;

public class StaticBiomeArray implements BiomeArray {

    private BiomeGenBase biome;

    @Override
    public boolean isEmpty() {
        return biome == null;
    }

    @Override
    public BiomeGenBase defaultReturnValue() {
        return biome;
    }

    @Override
    public void defaultReturnValue(BiomeGenBase rv) {
        biome = rv;
    }

    @Override
    public BiomeGenBase put(int key, BiomeGenBase value) {
        return biome;
    }

    @Override
    public BiomeGenBase get(int i) {
        return biome;
    }
}
