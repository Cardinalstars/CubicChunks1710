package com.cardinalstar.cubicchunks.world;

import net.minecraft.world.World;

import javax.annotation.Nullable;

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
