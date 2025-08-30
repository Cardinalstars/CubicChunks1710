package com.cardinalstar.cubicchunks.world;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;

public interface ICubicWorldProvider {

    /**
     * Creates a new Cube generator
     *
     * @return a new Cube generator
     */
    @Nullable
    ICubeGenerator createCubeGenerator();

    int getOriginalActualHeight();

    World getWorld();
}
