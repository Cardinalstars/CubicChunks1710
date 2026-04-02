package com.cardinalstar.cubicchunks.api.worldgen.decoration;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface ICubicBiome {

    /// @see ICubePopulator#prepopulate(World, CubePos)
    default void predecorate(World world, CubePos pos) {

    }

    void decorate(World world, Cube cube);
}
