package com.cardinalstar.cubicchunks.util.biome3d;

import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.util.AddressTools;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

public interface BiomeArray extends Int2ObjectFunction<BiomeGenBase> {

    boolean isEmpty();

    default BiomeGenBase put(int x, int y, int z, BiomeGenBase value) {
        return put(AddressTools.getLocalAddress(x, y, z), value);
    }

    @Nullable
    default BiomeGenBase get(int x, int y, int z) {
        return get(AddressTools.getLocalAddress(x, y, z));
    }
}
