package com.cardinalstar.cubicchunks.biomes.impl;

import static com.cardinalstar.cubicchunks.biomes.BiomeHumidity.ARID;
import static com.cardinalstar.cubicchunks.biomes.BiomeHumidity.NORMAL;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.COLD;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.FREEZING;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.between;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.biomes.BiomeHumidity;
import com.cardinalstar.cubicchunks.biomes.CCBiomeGenBase;
import com.cardinalstar.cubicchunks.common.blocks.stone.BlockCaveStone;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class FrozenCavesBiome extends CCBiomeGenBase {

    private final BlockMeta schist = new BlockMeta(BlockCaveStone.SCHIST, 0);

    public FrozenCavesBiome(int biomeID) {
        super(biomeID);
        setBiomeName("Frozen Caves");
        setInterestingness(0.5);
        register(between(FREEZING, COLD), BiomeHumidity.between(ARID, NORMAL));
    }

    @Override
    public @Nullable ImmutableBlockMeta getFiller(double noise) {
        return schist;
    }

    @Override
    public @Nullable ImmutableBlockMeta getSurface(double noise) {
        return schist;
    }
}
