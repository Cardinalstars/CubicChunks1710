package com.cardinalstar.cubicchunks.api.worldgen.decoration;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/// A decorator is responsible for doing any special terrain generation or population. Typically you'll want to use a
/// [StandardWorldDecorator] instead of implementing this yourself, because this is just a wrapper type.
public interface IWorldDecorator {

    /// Runs prior to [#generateCube(World, Cube)], when a cube is in the eager-loading queue and needs to be generated.
    /// There is no guarantee pregenerated cubes will be eventually loaded. Cubes can be removed from the eager loading
    /// queue.
    void pregenerate(World world, CubePos pos);

    /// Runs terrain generation on the cube. The cube is not in the world at this point and block operations should not
    /// be performed on the world.
    void generate(World world, Cube cube);

    /// Runs prior to [#populateCube(World, CubePos)], when a cube is in the eager-loading queue but needs to be
    /// populated. There is no guarantee prepopulated cubes will be eventually populated. Cubes can be removed
    /// from the eager loading queue.
    void prepopulate(World world, CubePos pos);

    /// Populates the cube. The cube is in the world at this point, but be careful of causing cascading worldgen when
    /// accessing blocks outside the current cube. Note that by convention MC populates the 16x16x16 box around the
    /// +X,+Y,+Z corner, not whole cube.
    void populate(World world, Cube cube);
}
