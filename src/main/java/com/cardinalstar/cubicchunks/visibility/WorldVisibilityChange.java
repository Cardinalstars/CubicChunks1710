package com.cardinalstar.cubicchunks.visibility;

import java.util.HashSet;

import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.util.CubePos;

public class WorldVisibilityChange {

    public final HashSet<CubePos> cubesToUnload = new HashSet<>();
    public final HashSet<CubePos> cubesToLoad = new HashSet<>();
    public final HashSet<ChunkCoordIntPair> columnsToUnload = new HashSet<>();
    public final HashSet<ChunkCoordIntPair> columnsToLoad = new HashSet<>();
}
