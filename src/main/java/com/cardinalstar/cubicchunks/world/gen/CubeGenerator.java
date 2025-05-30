package com.cardinalstar.cubicchunks.world.gen;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.core.world.cube.Cube;
import com.cardinalstar.cubicchunks.core.world.cube.ICubeProvider;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.Random;

public class CubeGenerator implements ICubeProvider
{
    private Random rand;
    private int worldHeightCubes;
    // Represents the biomes for generation within a cube
    // 4x4, leading to possible 3D biome generation.
    private BiomeGenBase[] biomesForGeneration;

    private final World world;
    private final IChunkProvider vanillaProvider;

    CubeGenerator(World world, IChunkProvider vanillaProvider)
    {
        this.world = world;
        this.vanillaProvider = vanillaProvider;

        int worldHeightBlocks = ((ICubicWorld) world).getMaxGenerationHeight();
        worldHeightCubes = worldHeightBlocks / Cube.SIZE;
    }

    public void generateCube(int cubeX, int cubeY, int cubeZ, Block[] blocks)
    {

    }

    public void generateColumn(Chunk column) {
        this.biomesForGeneration = this.world.getWorldChunkManager().
            getBiomesForGeneration(this.biomesForGeneration,
                column.xPosition << 4,
                column.zPosition << 4,
                Cube.SIZE,
                Cube.SIZE);
        byte[] abyte = column.getBiomeArray();
        for (int i = 0; i < abyte.length; ++i) {
            abyte[i] = (byte) biomesForGeneration[i].biomeID;
        }
    }
}
