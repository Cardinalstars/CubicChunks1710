package com.cardinalstar.cubicchunks.lighting;

import com.cardinalstar.cubicchunks.api.ICube;
import net.minecraft.world.EnumSkyBlock;

public interface ICubicChunkLightingEngine {
    void processLightUpdates();

    void scheduleLightUpdate(EnumSkyBlock lightType, int x, int y, int z);

    boolean hasLightUpdates();

    int getSavedLightValue(ICube cube, EnumSkyBlock type, int x, int y, int z);

    void onCubeLoad(ICube cube);

    default void onCubeUnload(ICube cube) {}
}
