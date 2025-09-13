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

import java.util.Comparator;
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
import com.cardinalstar.cubicchunks.util.CompatHandler;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.ChunkArrayBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.ChunkBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.IBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.IMutableBlockView;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A cube generator that tries to mirror vanilla world generation. Cubes in the normal world range will be copied from a
 * vanilla chunk generator, cubes above and below that will be filled with the most common block in the
 * topmost/bottommost layers.
 */
@ParametersAreNonnullByDefault
public class VanillaCompatibilityGenerator implements ICubeGenerator {

    private static final Box BOTTOM_BEDROCK_LAYER = Box.horizontalChunkSlice(0, 8);

    @Desugar
    record FillerInfo(ImmutableBlockMeta filler) {}

    private final int worldHeightBlocks;
    private final int worldHeightCubes;
    private final Box topBedrockLayer;

    @Nonnull
    private final IChunkProvider vanilla;
    @Nonnull
    private final World world;
    /**
     * Last chunk that was generated from the vanilla world gen
     */
    private Chunk lastChunk;
    private IMutableBlockView lastChunkView;
    /**
     * We generate all the chunks in the vanilla range at once. This variable prevents infinite recursion
     */
    private boolean optimizationHack;
    private BiomeGenBase[] biomes;

    private FillerInfo bottom, top;

    /**
     * Create a new VanillaCompatibilityGenerator
     *
     * @param vanilla The vanilla generator to mirror
     * @param world   The world in which cubes are being generated
     */
    public VanillaCompatibilityGenerator(IChunkProvider vanilla, World world) {
        this.vanilla = vanilla;
        this.world = world;

        worldHeightBlocks = ((ICubicWorld) this.world).getMaxGenerationHeight();
        worldHeightCubes = Coords.blockCeilToCube(worldHeightBlocks);
        topBedrockLayer = Box.horizontalChunkSlice(worldHeightBlocks - 8, 8);
    }

    private ICubeLoader getCubeLoader() {
        return ((ICubicWorldInternal.Server) world).getCubeCache()
            .getCubeLoader();
    }

    private FillerInfo getBottomFillerInfo() {
        if (bottom != null) return bottom;

        Chunk chunk = vanilla.provideChunk(0, 0);

        ((IColumnInternal) chunk).setColumn(false);

        bottom = analyzeBottomFiller(new ChunkBlockView(chunk));

        return bottom;
    }

    private FillerInfo analyzeBottomFiller(IBlockView blockView) {
        Object2IntOpenHashMap<ImmutableBlockMeta> histogram = new Object2IntOpenHashMap<>();

        // Scan three layers for top and bottom cubes to guard against bedrock walls
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < Cube.SIZE; x++) {
                for (int z = 0; z < Cube.SIZE; z++) {
                    histogram.addTo(new BlockMeta(blockView.getBlock(x, y, z), blockView.getBlockMetadata(x, y, z)), 1);
                }
            }
        }

        var bottomBlock = histogram.object2IntEntrySet().stream()
            .filter(e -> e.getKey().getBlock() != Blocks.bedrock)
            .max(Comparator.comparingInt(Object2IntMap.Entry::getIntValue));

        ImmutableBlockMeta filler = bottomBlock.map(Map.Entry::getKey).orElse(new BlockMeta(Blocks.air, 0));

        return new FillerInfo(filler);
    }

    private FillerInfo getTopFillerInfo() {
        if (top != null) return top;

        Chunk chunk = vanilla.provideChunk(0, 0);

        ((IColumnInternal) chunk).setColumn(false);

        top = analyzeTopFiller(new ChunkBlockView(chunk));

        return top;
    }

    private FillerInfo analyzeTopFiller(IBlockView blockView) {
        Object2IntOpenHashMap<ImmutableBlockMeta> histogram = new Object2IntOpenHashMap<>();

        int top = blockView.getBounds().getY2();

        // Scan three layers for top and bottom cubes to guard against bedrock walls
        for (int y = top - 1; y > top - 4; y--) {
            for (int x = 0; x < Cube.SIZE; x++) {
                for (int z = 0; z < Cube.SIZE; z++) {
                    histogram.addTo(new BlockMeta(blockView.getBlock(x, y, z), blockView.getBlockMetadata(x, y, z)), 1);
                }
            }
        }

        var topBlock = histogram.object2IntEntrySet().stream()
            .filter(e -> e.getKey().getBlock() != Blocks.bedrock)
            .max(Comparator.comparingInt(Object2IntMap.Entry::getIntValue));

        ImmutableBlockMeta filler = topBlock.map(Map.Entry::getKey).orElse(new BlockMeta(Blocks.air, 0));

        return new FillerInfo(filler);
    }

    @Override
    public void generateColumn(Chunk column) {

        this.biomes = this.world.getWorldChunkManager().loadBlockGeneratorData(
            this.biomes,
            column.xPosition * 16, column.zPosition * 16, 16, 16);

        byte[] biomeArray = column.getBiomeArray();
        for (int i = 0; i < biomeArray.length; ++i) {
            biomeArray[i] = (byte) this.biomes[i].biomeID;
        }
    }

    @Override
    public void recreateStructures(Chunk column) {
        vanilla.recreateStructures(column.xPosition, column.zPosition); // TODO WATCH
    }

    @Override
    public Cube provideCube(Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        try {
            WorldgenHangWatchdog.startWorldGen();

            IBlockView cubeData;

            if (cubeY < 0) {
                FillerInfo fillerInfo = getBottomFillerInfo();

                Block[] blocks = new Block[4096];
                int[] blockMeta = new int[4096];

                cubeData = new ChunkArrayBlockView(
                    16, 16, 16,
                    ObjectArrayList.wrap(blocks),
                    IntArrayList.wrap(blockMeta));

                // Fill with bottom block
                ((IMutableBlockView) cubeData).fill(fillerInfo.filler);
            } else if (cubeY >= worldHeightCubes) {
                FillerInfo fillerInfo = getTopFillerInfo();

                Block[] blocks = new Block[4096];
                int[] blockMeta = new int[4096];

                cubeData = new ChunkArrayBlockView(
                    16, 16, 16,
                    ObjectArrayList.wrap(blocks),
                    IntArrayList.wrap(blockMeta));

                // Fill with top block
                ((IMutableBlockView) cubeData).fill(fillerInfo.filler);
            } else {
                cubeData = getVanillaChunkSlice(cubeX, cubeY, cubeZ);
            }

            Cube cube = new Cube(chunk, cubeY,cubeData);

            CubeGeneratorsRegistry.generateVanillaCube(this, world, cube);

            return cube;
        } finally {
            WorldgenHangWatchdog.endWorldGen();
        }
    }

    private IBlockView getVanillaChunkSlice(int cubeX, int cubeY, int cubeZ) {
        // Make vanilla generate a chunk for us to copy
        if (lastChunk == null || lastChunk.xPosition != cubeX || lastChunk.zPosition != cubeZ) {
            long hash = Fnv1a64.initialState();
            hash = Fnv1a64.hashStep(hash, world.getSeed());
            hash = Fnv1a64.hashStep(hash, cubeX);
            hash = Fnv1a64.hashStep(hash, cubeZ);

            XSTR rand = new XSTR(hash);

            generateVanillaChunk(cubeX, cubeZ, rand);
        }

        // Generate all cubes in the current vanilla chunk, since we've already done the work to generate their terrain
        // (vanilla generators can only generate a whole chunk at a time)
        if (!optimizationHack) {
            optimizationHack = true;
            for (int y = worldHeightCubes - 1; y >= 0; y--) {
                if (y == cubeY) {
                    continue;
                }

                getCubeLoader().getCube(cubeX, y, cubeZ, ICubeProviderServer.Requirement.GENERATE);
            }
            optimizationHack = false;
        }

        return lastChunkView.subView(Box.horizontalChunkSlice(cubeY * 16, 16));
    }

    private void generateVanillaChunk(int cubeX, int cubeZ, Random rand) {
        if (CubicChunksConfig.optimizedCompatibilityGenerator) {
            try (ICubicWorldInternal.CompatGenerationScope ignored = ((ICubicWorldInternal.Server) world).doCompatibilityGeneration()) {
                lastChunk = vanilla.provideChunk(cubeX, cubeZ);

                Block[] compatBlocks = ((IColumnInternal) lastChunk).getCompatGenerationBlockArray();
                byte[] compatBlockMeta = ((IColumnInternal) lastChunk).getCompatGenerationByteArray();

                if (compatBlocks == null || compatBlockMeta == null) {
                    CubicChunks.LOGGER.error("Optimized compatibility generation failed, disabling...");
                    CubicChunksConfig.optimizedCompatibilityGenerator = false;
                } else {
                    lastChunkView = new ChunkArrayBlockView(
                        16, worldHeightBlocks, 16,
                        wrapBlockArray(compatBlocks),
                        wrapByteArray(compatBlockMeta));

                    removeBedrock(lastChunkView, rand);

                    return;
                }
            }
        }

        lastChunk = vanilla.provideChunk(cubeX, cubeZ);

        ((IColumnInternal) lastChunk).setColumn(false);

        lastChunkView = new ChunkBlockView(lastChunk);

        removeBedrock(lastChunkView, rand);
    }

    private static Int2ObjectFunction<Block> wrapBlockArray(Block[] compatBlocks) {
        return new Int2ObjectFunction<>() {

            @Override
            public Block get(int key) {
                return compatBlocks[key];
            }

            @Override
            public Block put(int key, Block value) {
                compatBlocks[key] = value;
                return null;
            }

            @Override
            public int size() {
                return compatBlocks.length;
            }
        };
    }

    private static Int2IntFunction wrapByteArray(byte[] compatBlockMeta) {
        return new Int2IntFunction() {

            @Override
            public int get(int key) {
                return compatBlockMeta[key];
            }

            @Override
            public int put(int key, int value) {
                compatBlockMeta[key] = (byte) value;
                return 0;
            }

            @Override
            public int size() {
                return compatBlockMeta.length;
            }
        };
    }

    private void removeBedrock(IMutableBlockView chunk, Random rand) {
        FillerInfo bottom = analyzeBottomFiller(chunk);
        FillerInfo top = analyzeTopFiller(chunk);

        for (Vector3ic v : BOTTOM_BEDROCK_LAYER) {
            if (chunk.getBlock(v.x(), v.y(), v.z()) == Blocks.bedrock) {
                boolean isBottomLayer = v.y() == 0;

                // Remove 1 in 3 blocks of bedrock to create holes
                // Increase to 1 in 2 for the bottom layer because it's too dense
                if (rand.nextInt(isBottomLayer ? 2 : 3) == 0) {
                    chunk.setBlock(v.x(), v.y(), v.z(), bottom.filler);
                }
            }
        }

        for (Vector3ic v : topBedrockLayer) {
            if (chunk.getBlock(v.x(), v.y(), v.z()) == Blocks.bedrock) {
                boolean isTopLayer = v.y() == worldHeightBlocks - 1;

                // Remove 1 in 3 blocks of bedrock to create holes
                // Increase to 1 in 2 for the top layer because it's too dense
                if (rand.nextInt(isTopLayer ? 2 : 3) == 0) {
                    chunk.setBlock(v.x(), v.y(), v.z(), top.filler);
                }
            }
        }
    }

    @Override
    public void populate(Cube cube) {
        try {
            getCubeLoader().pauseLoadCalls();

            WorldgenHangWatchdog.startWorldGen();

            CubeGeneratorsRegistry.populateVanillaCube(world, cube.getCoords());

            Cube withinVanillaChunk = cube;

            // Cubes outside this range are only filled with their respective block
            // No population takes place
            if (!isWithinVanillaWorld(cube)) {
                withinVanillaChunk = getCubeLoader().getCube(cube.getX(), 0, cube.getZ(), ICubeProviderServer.Requirement.GENERATE);
            }

            // Populate the vanilla chunk if it isn't already
            if (withinVanillaChunk != null && !withinVanillaChunk.isFullyPopulated()) populateChunk(getCubeLoader(), cube);

            // Always set the requested cube to populated, even if it's outside of the vanilla chunk (and therefore had
            // no work done on it).
            cube.setPopulated(true);
            cube.setFullyPopulated(true);
        } finally {
            WorldgenHangWatchdog.endWorldGen();

            getCubeLoader().unpauseLoadCalls();
        }
    }

    private boolean isWithinVanillaWorld(Cube cube) {
        return cube.getY() >= 0 && cube.getY() < worldHeightCubes;
    }

    private void populateChunk(ICubeLoader loader, Cube cube) {
        // First we have to generate all surrounding cubes
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y < 16; y++) {
                    loader.getCube(cube.getX() + x, y, cube.getZ() + z, ICubeProviderServer.Requirement.GENERATE);
                }
            }
        }

        // Second, we regenerate the heightmap of all horizontally adjacent cubes
        for (int x = -1;  x <=1; x++) {
            for (int z = -1; z <=1; z++) {
                Cube cube2 = loader.getCube(cube.getX() + x, cube.getY(), cube.getZ() + z, ICubeProviderServer.Requirement.GENERATE);
                ((IColumnInternal) cube2.getColumn()).recalculateStagingHeightmap();
            }
        }

        // Third, we mark the cubes in the current vanilla chunk as populated
        for (int y = 0; y < worldHeightCubes; y++) {
            Cube inColumn = loader.getCube(cube.getX(), y, cube.getZ(), ICubeProviderServer.Requirement.GENERATE);

            inColumn.setPopulated(true);
            inColumn.setFullyPopulated(true);
        }

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
