package com.cardinalstar.cubicchunks.world.worldgen;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubicPopulator;
import com.cardinalstar.cubicchunks.util.CubePos;

public class SurfacePatcherPopulator implements ICubicPopulator {

    @Override
    public void generate(World world, CubePos pos) {
        if (pos.getY() >= 0 && pos.getY() < 16) {

        }
    }
}
