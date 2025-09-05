/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.worldgen;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.worldgen.CubeGeneratorsRegistry;
import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.early.common.IGameRegistry;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.ChunkStorageUtils;
import com.cardinalstar.cubicchunks.util.CompatHandler;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * A cube generator that tries to mirror vanilla world generation. Cubes in the normal world range will be copied from a
 * vanilla chunk generator, cubes above and below that will be filled with the most common block in the
 * topmost/bottommost layers.
 */
@ParametersAreNonnullByDefault
public class VanillaCompatibilityGenerator implements ICubeGenerator {

    // TODO MAKE SURE THE BIOMES ARE SET CORRECTLY.
    private boolean isInit = false;
    private int worldHeightCubes;
    @Nonnull
    private final IChunkProvider vanilla;
    @Nonnull
    private final World world;
    /**
     * Last chunk that was generated from the vanilla world gen
     */
    private Chunk lastChunk;
    /**
     * We generate all the chunks in the vanilla range at once. This variable prevents infinite recursion
     */
    private boolean optimizationHack;
    private BiomeGenBase[] biomes;
    /**
     * Detected block for filling cubes below the world
     */
    @Nonnull
    private Block extensionBlockBottom = Blocks.stone;
    /**
     * Detected block for filling cubes above the world
     */
    @Nonnull
    private Block extensionBlockTop = Blocks.air;

    private boolean hasTopBedrock = false, hasBottomBedrock = true;

    /**
     * Create a new VanillaCompatibilityGenerator
     *
     * @param vanilla The vanilla generator to mirror
     * @param world   The world in which cubes are being generated
     */
    public VanillaCompatibilityGenerator(IChunkProvider vanilla, World world) {
        this.vanilla = vanilla;
        this.world = world;
    }

    // lazy initialization to avoid circular dependencies
    private void tryInit(IChunkProvider vanilla, World world) {
        if (isInit) {
            return;
        }
        isInit = true;
        // heuristics TODO: add a config that overrides this
        lastChunk = vanilla.provideChunk(0, 0); // lets scan the chunk at 0, 0

        int worldHeightBlocks = ((ICubicWorld) world).getMaxGenerationHeight();
        worldHeightCubes = worldHeightBlocks / Cube.SIZE;
        Map<Block, Integer> blockHistogramBottom = new HashMap<>();
        Map<Block, Integer> blockHistogramTop = new HashMap<>();

        ExtendedBlockStorage bottomEBS = lastChunk.getBlockStorageArray()[0];
        for (int x = 0; x < Cube.SIZE; x++) {
            for (int z = 0; z < Cube.SIZE; z++) {
                // Scan three layers top / bottom each to guard against bedrock walls
                for (int y = 0; y < 3; y++) {
                    Block blockState = bottomEBS == null ? Blocks.air : bottomEBS.getBlockByExtId(x, y, z);

                    int count = blockHistogramBottom.getOrDefault(blockState, 0);
                    blockHistogramBottom.put(blockState, count + 1);
                }

                for (int y = worldHeightBlocks - 1; y > worldHeightBlocks - 4; y--) {
                    int localY = Coords.blockToLocal(y);
                    ExtendedBlockStorage ebs = lastChunk.getBlockStorageArray()[Coords.blockToCube(y)];

                    Block blockState = ebs == null ? Blocks.air : ebs.getBlockByExtId(x, localY, z);

                    int count = blockHistogramTop.getOrDefault(blockState, 0);
                    blockHistogramTop.put(blockState, count + 1);
                }
            }
        }

        CubicChunks.LOGGER.debug("Block histograms: \nTop: " + blockHistogramTop + "\nBottom: " + blockHistogramBottom);

        int topcount = 0;
        for (Map.Entry<Block, Integer> entry : blockHistogramBottom.entrySet()) {
            if (entry.getValue() > topcount && entry.getKey() != Blocks.bedrock) {
                extensionBlockBottom = entry.getKey();
                topcount = entry.getValue();
            }
        }
        hasBottomBedrock = blockHistogramBottom.getOrDefault(Blocks.bedrock, 0) > 0;
        CubicChunks.LOGGER.info(
            "Detected filler block " + extensionBlockBottom.getLocalizedName()
                + " "
                + "from layers [0, 2], bedrock="
                + hasBottomBedrock);

        topcount = 0;
        for (Map.Entry<Block, Integer> entry : blockHistogramTop.entrySet()) {
            if (entry.getValue() > topcount && entry.getKey() != Blocks.bedrock) {
                extensionBlockTop = entry.getKey();
                topcount = entry.getValue();
            }
        }
        hasTopBedrock = blockHistogramTop.getOrDefault(Blocks.bedrock, 0) > 0;
        CubicChunks.LOGGER.info(
            "Detected filler block " + extensionBlockTop.getLocalizedName()
                + " from"
                + " layers ["
                + (worldHeightBlocks - 3)
                + ", "
                + (worldHeightBlocks - 1)
                + "], bedrock="
                + hasTopBedrock);
    }

    @Override
    public void generateColumn(Chunk column) {

        this.biomes = this.world.getWorldChunkManager()
            .getBiomesForGeneration(
                this.biomes,
                Coords.cubeToMinBlock(column.xPosition),
                Coords.cubeToMinBlock(column.zPosition),
                Cube.SIZE,
                Cube.SIZE);

        byte[] abyte = column.getBiomeArray();
        for (int i = 0; i < abyte.length; ++i) {
            abyte[i] = (byte) this.biomes[i].biomeID;
        }
    }

    @Override
    public void recreateStructures(Chunk column) {
        vanilla.recreateStructures(column.xPosition, column.zPosition); // TODO WATCH
    }

    private Random getCubeSpecificRandom(int cubeX, int cubeY, int cubeZ) {
        Random rand = new Random(world.getSeed());
        rand.setSeed(rand.nextInt() ^ cubeX);
        rand.setSeed(rand.nextInt() ^ cubeZ);
        rand.setSeed(rand.nextInt() ^ cubeY);
        return rand;
    }

    @Override
    public Cube provideCube(Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        try {
            WorldgenHangWatchdog.startWorldGen();
            tryInit(vanilla, world);

            Random rand = new Random(world.getSeed());
            rand.setSeed(rand.nextInt() ^ cubeX);
            rand.setSeed(rand.nextInt() ^ cubeZ);
            Block[] blocks = new Block[4096];
            byte[] blockMeta = new byte[4096];
            if (cubeY < 0 || cubeY >= worldHeightCubes) {
                // Fill with bottom block
                for (int y = 0; y < Cube.SIZE; y++) {
                    for (int z = 0; z < Cube.SIZE; z++) {
                        for (int x = 0; x < Cube.SIZE; x++) {
                            Block block = cubeY < 0 ? extensionBlockBottom : extensionBlockTop;
                            int blockY = Coords.localToBlock(cubeY, y);
                            block = WorldGenUtils.getRandomBedrockReplacement(
                                world,
                                rand,
                                block,
                                blockY,
                                5,
                                hasTopBedrock,
                                hasBottomBedrock);
                            int storageIndex = ChunkStorageUtils.getBlockIndex(x, y, z);
                            blocks[storageIndex] = block;
                            blockMeta[storageIndex] = 0;
                        }
                    }
                }
            } else {
                // Make vanilla generate a chunk for us to copy
                if (lastChunk.xPosition != cubeX || lastChunk.zPosition != cubeZ) {
                    if (CubicChunksConfig.optimizedCompatibilityGenerator) {
                        try (ICubicWorldInternal.CompatGenerationScope ignored = ((ICubicWorldInternal.Server) world)
                            .doCompatibilityGeneration()) { // TODO MAKE THIS STORE THE BLOCK + META ARRAY FOR
                                                            // GENERATION
                            lastChunk = vanilla.provideChunk(cubeX, cubeZ);
                            Block[] compatBlocks = ((IColumnInternal) lastChunk).getCompatGenerationBlockArray();
                            byte[] compatBlockMeta = ((IColumnInternal) lastChunk).getCompatGenerationByteArray();
                            if (compatBlocks == null || compatBlockMeta == null) {
                                CubicChunks.LOGGER.error("Optimized compatibility generation failed, disabling...");
                                CubicChunksConfig.optimizedCompatibilityGenerator = false;
                            } else {
                                replaceBedrock(compatBlocks, compatBlockMeta, rand);
                            }
                        }
                    } else {
                        lastChunk = vanilla.provideChunk(cubeX, cubeZ);
                    }
                }

                if (!optimizationHack) {
                    optimizationHack = true;
                    // Recusrive generation
                    for (int y = worldHeightCubes - 1; y >= 0; y--) {
                        if (y == cubeY) {
                            continue;
                        }
                        ((ICubicWorld) world).getCubeFromCubeCoords(cubeX, y, cubeZ);
                    }
                    optimizationHack = false;
                }

                // Copy from vanilla, replacing bedrock as appropriate
                Block[] compatBlocks = ((IColumnInternal) lastChunk).getCompatGenerationBlockArray();
                byte[] compatBlockMeta = ((IColumnInternal) lastChunk).getCompatGenerationByteArray();
                if (compatBlocks != null && compatBlockMeta != null) {
                    return new Cube(chunk, cubeY, compatBlocks, compatBlockMeta, true);
                }
                ExtendedBlockStorage storage = lastChunk.getBlockStorageArray()[cubeY];
                if (((ICubicWorld) world).getMaxHeight() == 16) {
                    if (cubeY != 0) {
                        storage = null;
                    } else {
                        storage = lastChunk.getBlockStorageArray()[4];
                    }
                }
                if (storage != null && !storage.isEmpty()) {
                    for (int y = 0; y < Cube.SIZE; y++) {
                        int blockY = Coords.localToBlock(cubeY, y);
                        for (int z = 0; z < Cube.SIZE; z++) {
                            for (int x = 0; x < Cube.SIZE; x++) {
                                Block block = storage.getBlockByExtId(x, y, z);
                                if (block == Blocks.bedrock) {
                                    if (y < Cube.SIZE / 2) {
                                        block = extensionBlockBottom;
                                    } else {
                                        block = extensionBlockTop;
                                    }
                                    block = WorldGenUtils.getRandomBedrockReplacement(
                                        world,
                                        rand,
                                        block,
                                        blockY,
                                        5,
                                        hasTopBedrock,
                                        hasBottomBedrock);
                                    blocks[ChunkStorageUtils.getBlockIndex(x, y, z)] = block;
                                    blockMeta[ChunkStorageUtils.getBlockIndex(x, y, z)] = 0;
                                } else {
                                    block = WorldGenUtils.getRandomBedrockReplacement(
                                        world,
                                        rand,
                                        block,
                                        blockY,
                                        5,
                                        hasTopBedrock,
                                        hasBottomBedrock);
                                    blocks[ChunkStorageUtils.getBlockIndex(x, y, z)] = block;
                                    blockMeta[ChunkStorageUtils.getBlockIndex(x, y, z)] = 0;
                                }
                            }
                        }
                    }
                }
            }

            return new Cube(chunk, cubeY, blocks, blockMeta, false);
        } finally {
            WorldgenHangWatchdog.endWorldGen();
        }
    }

    private void replaceBedrock(Block[] blocks, byte[] blockMetadata, Random rand) {
        for (int y = 0; y < 8; y++) {
            replaceBedrockAtLayer(blocks, blockMetadata, rand, y);
        }
        int startY = Coords.localToBlock(worldHeightCubes - 1, 8);
        int endY = Coords.cubeToMinBlock(worldHeightCubes);
        for (int y = startY; y < endY; y++) {
            replaceBedrockAtLayer(blocks, blockMetadata, rand, y);
        }
    }

    private void replaceBedrockAtLayer(Block[] blocks, byte[] blockMetadata, Random rand, int y) {
        for (int z = 0; z < Cube.SIZE; z++) {
            for (int x = 0; x < Cube.SIZE; x++) {
                Block block = blocks[ChunkStorageUtils.getBlockIndex(x, y, z)];
                if (block == Blocks.bedrock) {
                    if (y < 64) {
                        blocks[ChunkStorageUtils.getBlockIndex(x, y, z)] = WorldGenUtils.getRandomBedrockReplacement(
                            world,
                            rand,
                            extensionBlockBottom,
                            y,
                            5,
                            hasTopBedrock,
                            hasBottomBedrock);
                    } else {
                        blocks[ChunkStorageUtils.getBlockIndex(x, y, z)] = WorldGenUtils.getRandomBedrockReplacement(
                            world,
                            rand,
                            extensionBlockTop,
                            y,
                            5,
                            hasTopBedrock,
                            hasBottomBedrock);
                    }
                }
            }
        }
    }

    @Override
    public void populate(ICubeLoader loader, Cube cube) {
        try {
            loader.pauseLoadCalls();

            WorldgenHangWatchdog.startWorldGen();

            tryInit(vanilla, world);

            Random rand = getCubeSpecificRandom(cube.getX(), cube.getY(), cube.getZ());

            CubeGeneratorsRegistry.populateVanillaCubic(world, rand, cube);

            Cube withinVanillaChunk = cube;

            // Cubes outside this range are only filled with their respective block
            // No population takes place
            if (cube.getY() < 0 || cube.getY() >= worldHeightCubes) {
                try {
                    withinVanillaChunk = loader.getCube(cube.getX(), 0, cube.getZ(), ICubeProviderServer.Requirement.GENERATE);
                } catch (IOException e) {
                    CubicChunks.LOGGER.error("Could not load cube at y=0 within vanilla chunk {},{} for vanilla chunk population", cube.getX(), cube.getZ(), e);
                    withinVanillaChunk = null;
                }
            }

            if (withinVanillaChunk != null && !withinVanillaChunk.isFullyPopulated()) populateChunk(loader, cube);

            cube.setPopulated(true);
            cube.setFullyPopulated(true);
        } finally {
            WorldgenHangWatchdog.endWorldGen();

            loader.unpauseLoadCalls();
        }
    }

    private void populateChunk(ICubeLoader loader, Cube cube) {
        // First we have to generate all surrounding cubes
        for (Vector3ic v : getPopulationPregenerationRequirements(cube)) {
            if (v.equals(0, 0, 0)) continue;

            int x = v.x() + cube.getX();
            int y = v.y() + cube.getY();
            int z = v.z() + cube.getZ();

            try {
                loader.getCube(x, y, z, ICubeProviderServer.Requirement.GENERATE);
            } catch (IOException e) {
                CubicChunks.LOGGER.error("Could not generate cube {},{},{}", x, y, z, e);
            }
        }

        // Second, we mark the cubes in the current chunk as populated
        for (int y = 0; y < worldHeightCubes; y++) {
            try {
                Cube inColumn = loader.getCube(cube.getX(), y, cube.getZ(), ICubeProviderServer.Requirement.GENERATE);

                inColumn.setPopulated(true);
                inColumn.setFullyPopulated(true);
            } catch (IOException e) {
                CubicChunks.LOGGER.error("Could not mark cube {},{},{} as populated", cube.getX(), y, cube.getZ(), e);
            }
        }

        ((IColumnInternal) cube.getColumn()).recalculateStagingHeightmap();

        try {
            CompatHandler.beforePopulate(world, vanilla);

            // Then we can populate this cube
            vanilla.populate(vanilla, cube.getX(), cube.getZ());

            GameRegistry.generateWorld(
                cube.getX(),
                cube.getZ(),
                world,
                vanilla,
                world.getChunkProvider());

            applyModGenerators(cube.getX(), cube.getZ(), world, vanilla, world.getChunkProvider());
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("Could not populate cube {},{},{}", cube.getX(), cube.getY(), cube.getZ(), t);
            ((IColumnInternal) cube.getColumn()).recalculateStagingHeightmap();
        } finally {
            CompatHandler.afterPopulate(world);
        }

    }

    // First proider is the ChunkProviderGenerate/Hell/End/Flat second is the serverChunkProvider
    private void applyModGenerators(int x, int z, World world, IChunkProvider vanillaGen, IChunkProvider provider) {
        List<IWorldGenerator> generators = IGameRegistry.getSortedGeneratorList();
        if (generators == null) {
            IGameRegistry.computeGenerators();
            generators = IGameRegistry.getSortedGeneratorList();
            assert generators != null;
        }
        long worldSeed = world.getSeed();
        Random fmlRandom = new Random(worldSeed);
        long xSeed = fmlRandom.nextLong() >> 2 + 1L;
        long zSeed = fmlRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * x + zSeed * z) ^ worldSeed;

        for (IWorldGenerator generator : generators) {
            fmlRandom.setSeed(chunkSeed);
            try {
                CompatHandler.beforeGenerate(world, generator);
                generator.generate(fmlRandom, x, z, world, vanillaGen, provider);
            } finally {
                CompatHandler.afterGenerate(world);
            }
        }
    }

    private Box getChunkBoxForCube(ICube cube) {
        if (cube.getY() >= 0 && cube.getY() < worldHeightCubes) {
            return new Box(0, -cube.getY(), 0, 0, worldHeightCubes - cube.getY() - 1, 0);
        }
        return NO_REQUIREMENT;
    }

    public Box getFullPopulationRequirements(ICube cube) {
        if (cube.getY() >= 0 && cube.getY() < worldHeightCubes) {
            return new Box(-1, -cube.getY(), -1, 0, worldHeightCubes - cube.getY() - 1, 0);
        }
        return NO_REQUIREMENT;
    }

    public Box getPopulationPregenerationRequirements(ICube cube) {
        if (cube.getY() >= 0 && cube.getY() < worldHeightCubes) {
            return new Box(-1, -cube.getY(), -1, 1, worldHeightCubes - cube.getY() - 1, 1);
        }
        return NO_REQUIREMENT;
    }

    @Override
    public void recreateStructures(ICube cube) {}

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
        return vanilla.getPossibleCreatures(creatureType, x, y, z);
    }

    @Override
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return vanilla.func_147416_a(world, name, x, y, z);
    }
}
