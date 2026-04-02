package com.cardinalstar.cubicchunks.biomes;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class CubicBiomeRegistry {

    private static final Object2ObjectOpenHashMap<Pair<BiomeTemperature, BiomeHumidity>, CCBiomeGenBase> BIOMES = new Object2ObjectOpenHashMap<>();

    public static void register(@NotNull BiomeTemperature @NotNull [] temperature, @NotNull BiomeHumidity @NotNull [] humidity, CCBiomeGenBase biome) {
        for (BiomeTemperature temp : temperature) {
            for (BiomeHumidity h : humidity) {
                CCBiomeGenBase existing = BIOMES.get(Pair.of(temp, h));

                if (existing != null && existing.getInterestingness() > biome.getInterestingness()) continue;

                BIOMES.put(Pair.of(temp, h), biome);
            }
        }
    }

    public static CCBiomeGenBase get(BiomeTemperature temperature, BiomeHumidity humidity) {
        return BIOMES.get(Pair.of(temperature, humidity));
    }
}
