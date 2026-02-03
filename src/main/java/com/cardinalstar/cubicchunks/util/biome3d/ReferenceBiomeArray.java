package com.cardinalstar.cubicchunks.util.biome3d;

import java.util.Arrays;

import net.minecraft.world.biome.BiomeGenBase;

public class ReferenceBiomeArray implements BiomeArray {

    private BiomeGenBase def = null;

    private final BiomeGenBase[] data = new BiomeGenBase[16 * 16 * 16];

    @Override
    public boolean isEmpty() {
        for (BiomeGenBase b : data) {
            if (b != null) return false;
        }

        return true;
    }

    @Override
    public int size() {
        return 16 * 16 * 16;
    }

    @Override
    public void clear() {
        Arrays.fill(data, null);
    }

    @Override
    public BiomeGenBase put(int key, BiomeGenBase value) {
        data[key] = value;

        return null;
    }

    @Override
    public BiomeGenBase get(int key) {
        BiomeGenBase biome = data[key];

        return biome == null ? def : biome;
    }

    @Override
    public void defaultReturnValue(BiomeGenBase rv) {
        def = rv;
    }

    @Override
    public BiomeGenBase defaultReturnValue() {
        return def;
    }
}
