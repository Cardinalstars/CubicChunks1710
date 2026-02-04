package com.cardinalstar.cubicworlds.world.cube;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;

public interface ICubeProvider
{
    boolean CubeExists(int x, int y, int z);

    Cube ProvideCube(int x, int y, int z);

    Cube LoadCube(int x, int y, int z);

    void Populate(ICubeProvider provider, int x, int y, int z);

    boolean saveCubes(boolean saveChunksInOneGo, IProgressUpdate progressUpdate);

    boolean unloadQueuedCubes();

    boolean canSave();

    String makeString();

    List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType p_73155_1_, int x, int y, int z);

    ChunkPosition findClosestStructure(World world, String type, int x, int y, int z);

    int getLoadedCubeCount();

    void recreateStructures(int x, int y, int z);

    void saveExtraData();
}
