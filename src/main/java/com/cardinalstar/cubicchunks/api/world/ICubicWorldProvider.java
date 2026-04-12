package com.cardinalstar.cubicchunks.api.world;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;

/// Implemented on a [WorldProvider] so that it can directly generate its cubes, instead of going through
/// [VanillaWorldGenerator].
public interface ICubicWorldProvider {

    @NotNull
    IWorldGenerator createWorldGenerator(World world);

}
