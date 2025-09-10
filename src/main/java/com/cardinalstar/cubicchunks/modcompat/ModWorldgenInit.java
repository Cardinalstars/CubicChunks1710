package com.cardinalstar.cubicchunks.modcompat;

import com.cardinalstar.cubicchunks.util.Mods;
import cpw.mods.fml.common.Optional;

public class ModWorldgenInit {

    public static void init() {

    }

    @Optional.Method(modid = Mods.ModIDs.ET_FUTURUM_REQUIEM)
    private static void initEFR() {
//        CubeGeneratorsRegistry.registerVanillaGenerator();
    }

}
