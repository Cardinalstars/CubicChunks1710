package com.cardinalstar.cubicchunks.biomes.impl;

import static com.cardinalstar.cubicchunks.biomes.BiomeHumidity.ARID;
import static com.cardinalstar.cubicchunks.biomes.BiomeHumidity.DRY;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.BURNING;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.LAVA;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.between;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.biomes.BiomeHumidity;
import com.cardinalstar.cubicchunks.biomes.CCBiomeGenBase;
import com.cardinalstar.cubicchunks.common.blocks.stone.BlockCaveStone;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class VolcanicCavesBiome extends CCBiomeGenBase {

    private final BlockMeta gneiss = new BlockMeta(BlockCaveStone.GNEISS, 0);

    public VolcanicCavesBiome(int biomeID) {
        super(biomeID);
        setBiomeName("Volcanic Caves");
        setInterestingness(0.5);
        register(between(BURNING, LAVA), BiomeHumidity.between(ARID, DRY));
    }

    @Override
    public @Nullable ImmutableBlockMeta getFiller(double noise) {
        return gneiss;
    }

    @Override
    public @Nullable ImmutableBlockMeta getSurface(double noise) {
        return gneiss;
    }
}
