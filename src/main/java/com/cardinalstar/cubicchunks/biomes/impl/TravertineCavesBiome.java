package com.cardinalstar.cubicchunks.biomes.impl;

import static com.cardinalstar.cubicchunks.biomes.BiomeHumidity.FOGGY;
import static com.cardinalstar.cubicchunks.biomes.BiomeHumidity.HUMID;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.HOT;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.TEMPERATE;
import static com.cardinalstar.cubicchunks.biomes.BiomeTemperature.between;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.biomes.BiomeHumidity;
import com.cardinalstar.cubicchunks.biomes.CCBiomeGenBase;
import com.cardinalstar.cubicchunks.common.blocks.stone.BlockCaveStone;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class TravertineCavesBiome extends CCBiomeGenBase {

    private final BlockMeta travertine = new BlockMeta(BlockCaveStone.TRAVERTINE, 0);
    private final BlockMeta mossyTravertine = new BlockMeta(BlockCaveStone.TRAVERTINE, 1);

    public TravertineCavesBiome(int id) {
        super(id);
        setBiomeName("Travertine Caves");
        setInterestingness(0.5);
        register(between(TEMPERATE, HOT), BiomeHumidity.between(HUMID, FOGGY));
    }

    @Override
    public @Nullable ImmutableBlockMeta getFiller(double noise) {
        return travertine;
    }

    @Override
    public @Nullable ImmutableBlockMeta getSurface(double noise) {
        return noise < 0.5 ? travertine : mossyTravertine;
    }
}
