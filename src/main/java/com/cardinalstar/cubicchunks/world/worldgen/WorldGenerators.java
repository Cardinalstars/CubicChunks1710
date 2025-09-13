package com.cardinalstar.cubicchunks.world.worldgen;

import net.minecraft.init.Blocks;

import com.cardinalstar.cubicchunks.api.worldgen.CubeGeneratorsRegistry;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.worldgen.compat.DeepslateCubePopulator;
import com.gtnewhorizon.gtnhlib.util.data.LazyBlock;
import cpw.mods.fml.common.Optional;

public class WorldGenerators {

    private static final LazyBlock WATER_STILL = new LazyBlock(Mods.Minecraft, () -> Blocks.water);
    private static final LazyBlock LAVA_STILL = new LazyBlock(Mods.Minecraft, () -> Blocks.lava);

    public static void init() {
        initVanilla();

        if (Mods.EtFuturumRequiem.isModLoaded()) {
            initEFR();
        }
    }

    private static void initVanilla() {
        CubeGeneratorsRegistry.registerVanillaGenerator("caves", new MapGenCavesCubic());

        CubeGeneratorsRegistry.registerVanillaPopulator(
            "water-spouts",
            new MapGenCaveFluids(WATER_STILL));

        CubeGeneratorsRegistry.registerVanillaPopulator(
            "lava-spouts",
            new MapGenCaveFluids(LAVA_STILL));
    }

    @Optional.Method(modid = Mods.ModIDs.ET_FUTURUM_REQUIEM)
    private static void initEFR() {
        CubeGeneratorsRegistry.registerVanillaPopulator("low-deepslate", new DeepslateCubePopulator(), "water-spouts", "lava-spouts");
    }
}
