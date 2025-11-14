package com.cardinalstar.cubicchunks.api.worldgen.impl;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.DependencyRegistry;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.IWorldDecorator;
import com.cardinalstar.cubicchunks.api.worldgen.WorldgenRegistry;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubeGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public class StandardWorldDecorator implements WorldgenRegistry, IWorldDecorator {

    private final StandardDependencyRegistry<ICubeGenerator> terrain = new StandardDependencyRegistry<>();

    private final StandardDependencyRegistry<ICubePopulator> population = new StandardDependencyRegistry<>();

    @Override
    public DependencyRegistry<ICubeGenerator> terrain() {
        return terrain;
    }

    @Override
    public DependencyRegistry<ICubePopulator> population() {
        return population;
    }

    @Override
    public void pregenerate(World world, CubePos pos) {
        for (ICubeGenerator generator : terrain.sorted()) {
            generator.pregenerate(world, pos);
        }
    }

    @Override
    public void generate(World world, Cube cube) {
        for (ICubeGenerator generator : terrain.sorted()) {
            generator.generate(world, cube);
        }
    }

    @Override
    public void prepopulate(World world, CubePos pos) {
        for (ICubePopulator populator : population.sorted()) {
            populator.prepopulate(world, pos);
        }
    }

    @Override
    public void populate(World world, Cube cube) {
        for (ICubePopulator populator : population.sorted()) {
            populator.populate(world, cube);
        }
    }
}
