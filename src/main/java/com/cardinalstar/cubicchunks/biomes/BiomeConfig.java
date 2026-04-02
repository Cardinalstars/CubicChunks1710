package com.cardinalstar.cubicchunks.biomes;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.gtnewhorizon.gtnhlib.config.Config;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Config(modid = CubicChunks.MODID, category = "worldgen", configSubDirectory = "cubicchunks", filename = "biomes")
public class BiomeConfig {

    @Config.Comment("Generic underground biome")
    @Config.Name("Underground")
    public static SpecificBiomeConfig underground = new SpecificBiomeConfig(true, 260);

    @Config.Comment("Generic cave biome")
    @Config.Name("Cave")
    public static SpecificBiomeConfig cave = new SpecificBiomeConfig(true, 261);

    @Config.Comment("Dripstone-like caves")
    @Config.Name("Travertine Caves")
    public static SpecificBiomeConfig travertineCaves = new SpecificBiomeConfig(true, 262);

    @Config.Name("Frozen Caves")
    public static SpecificBiomeConfig frozenCaves = new SpecificBiomeConfig(true, 263);

    @Config.Name("Glow Caves")
    public static SpecificBiomeConfig glowCaves = new SpecificBiomeConfig(true, 264);

    @Config.Name("Volcanic Caves")
    public static SpecificBiomeConfig volcanicCaves = new SpecificBiomeConfig(true, 265);

    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecificBiomeConfig {
        @Config.Name("Enabled")
        public boolean enabled;

        @Config.Name("Biome ID")
        public int biomeID;
    }
}
