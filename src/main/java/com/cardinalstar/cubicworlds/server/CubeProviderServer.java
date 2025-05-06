package com.cardinalstar.cubicworlds.server;

import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.ArrayList;
import java.util.List;

public class CubeProviderServer extends ChunkProviderServer
{
    public LongHashMap loadedCubesHashMap = new LongHashMap();
    public List<Cube> loadedCubes = new ArrayList<>();

    public CubeProviderServer(WorldServer world, IChunkLoader loader, IChunkProvider provider) {
        super(world, loader, provider);
    }

    public Cube originalLoadCube(int x, int y, int z)
    {
        return null;
    }


}
