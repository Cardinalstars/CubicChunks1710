package com.cardinalstar.cubicchunks.api.worldgen.decoration;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/// Generates the terrain for a cube. The cube is not in the world during generation and block operations must not be
/// performed.
public interface ICubeGenerator {

    /// Hints to any off-thread precachers to submit a generation request for a cube that will be generated in the
    /// near-future.
    /// Result may or may not be used, this is just an optimization to do as much computation in a background thread as
    /// possible.
    default void pregenerate(World world, CubePos pos) {

    }

    /// Fills a cube with various terrain features.
    void generate(World world, Cube cube);
}
