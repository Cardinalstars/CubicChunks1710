package com.cardinalstar.cubicchunks.biomes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.biomes.BiomeConfig.SpecificBiomeConfig;
import com.cardinalstar.cubicchunks.biomes.impl.CaveBiome;
import com.cardinalstar.cubicchunks.biomes.impl.FrozenCavesBiome;
import com.cardinalstar.cubicchunks.biomes.impl.GlowCavesBiome;
import com.cardinalstar.cubicchunks.biomes.impl.TravertineCavesBiome;
import com.cardinalstar.cubicchunks.biomes.impl.UndergroundBiome;
import com.cardinalstar.cubicchunks.biomes.impl.VolcanicCavesBiome;
import com.cardinalstar.cubicchunks.util.Mods;
import lombok.SneakyThrows;

public enum CCBiomes {
    UNDERGROUND(BiomeConfig.underground, UndergroundBiome.class),
    CAVE(BiomeConfig.cave, CaveBiome.class),
    TRAVERTINE_CAVES(BiomeConfig.travertineCaves, TravertineCavesBiome.class),
    FROZEN_CAVES(BiomeConfig.frozenCaves, FrozenCavesBiome.class),
    GLOW_CAVES(BiomeConfig.glowCaves, GlowCavesBiome.class),
    VOLCANIC_CAVES(BiomeConfig.volcanicCaves, VolcanicCavesBiome.class),
    //
    ;

    public final boolean enabled;
    public final int biomeID;

    private final CCBiomeGenBase biome;

    @SneakyThrows
    CCBiomes(SpecificBiomeConfig config, Class<? extends CCBiomeGenBase> clazz) {
        this.enabled = ((config.biomeID >= 0 && config.biomeID < 256) || Mods.EndlessIDs.isModLoaded()) && config.enabled;
        this.biomeID = config.biomeID;
        this.biome = enabled ? clazz.getConstructor(int.class).newInstance(config.biomeID) : null;
    }

    @NotNull
    public CCBiomeGenBase getBiome() {
        if (!enabled) throw new IllegalStateException("Tried to get reference to disabled biome: " + name());

        return biome;
    }

    @Nullable
    public CCBiomeGenBase getBiomeIfEnabled() {
        return !enabled ? null : biome;
    }

}
