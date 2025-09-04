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
package com.cardinalstar.cubicchunks.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatProviderBase;
import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.chunkio.AsyncBatchingCubeIO;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.server.chunkio.async.forge.CubeIOExecutor;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.WorldSavedCubicChunksData;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.column.EmptyColumn;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;

import cpw.mods.fml.common.StartupQuery;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * This is CubicChunks equivalent of ChunkProviderServer, it loads and unloads Cubes and Columns.
 * <p>
 * There are a few necessary changes to the way vanilla methods work:
 * * Because loading a Chunk (Column) doesn't make much sense with CubicChunks,
 * all methods that load Chunks, actually load an empry column with no blocks in it
 * (there may be some entities that are not in any Cube yet).
 * * dropChunk method is not supported. Columns are unloaded automatically when the last cube is unloaded
 */
@ParametersAreNonnullByDefault
public class CubeProviderServer extends ChunkProviderServer
    implements ICubeProviderServer, ICubeProviderInternal.Server {

    @Nonnull
    private final EmptyColumn emptyColumn;
    @Nonnull
    private final BlankCube emptyCube;

    @Nonnull
    private final WorldServer worldServer;
    @Nonnull
    private final ICubeIO cubeIO;

    // TODO: Use a better hash map!
    @Nonnull
    private final XYZMap<Cube> cubeMap = new XYZMap<>(0.7f, 8000);

    // @Nonnull private final CubePrimer cubePrimer;
    @Nonnull
    private final ICubeGenerator cubeGen;
    @Nonnull
    private final Profiler profiler;
    // some mods will try to access blocks in ChunkDataEvent.Load
    // this needs the column to be already known by the chunk provider so that it can load cubes without trying to load
    // the column again
    public Chunk currentlyLoadingColumn;

    public CubeProviderServer(WorldServer worldServer, ICubeGenerator cubeGen) {
        super(
            worldServer,
            worldServer.getSaveHandler()
                .getChunkLoader(worldServer.provider), // forge uses this in
            worldServer.provider.createChunkGenerator()); // let's create the chunk generator, for now the vanilla one
                                                          // may be enough

        // this.cubePrimer = new CubePrimer();
        this.cubeGen = cubeGen;
        this.worldServer = worldServer;
        this.profiler = worldServer.theProfiler;
        try {
            Path path = worldServer.getSaveHandler()
                .getWorldDirectory()
                .toPath();
            if (worldServer.provider.getSaveFolder() != null) {
                path = path.resolve(worldServer.provider.getSaveFolder());
            }

            // use the save format stored in the server's default world as the global world storage type
            // TODO THIS IS DEFINITELY WRONG RIGHT NOW
            World overworld = worldServer.provider.worldObj;
            // World overworld = worldServer.func_73046_m().getEntityWorld();

            WorldSavedCubicChunksData savedData = (WorldSavedCubicChunksData) overworld.perWorldStorage
                .loadData(WorldSavedCubicChunksData.class, "cubicChunksData");

            StorageFormatProviderBase format = StorageFormatProviderBase.REGISTRY.get(savedData.storageFormat);
            if (format == null) {
                StartupQuery.notify("unsupported storage format \"" + savedData.storageFormat + '"');
                StartupQuery.abort();
            }

            this.cubeIO = new AsyncBatchingCubeIO(worldServer, format.provideStorage(worldServer, path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.emptyColumn = new EmptyColumn(worldServer, 0, 0);
        this.emptyCube = new BlankCube(emptyColumn);
    }

    @Override
    @Detainted
    public void unloadChunksIfNotNearSpawn(int x, int z) {
        // ignore, ChunkGc unloads cubes
    }

    @Override
    @Detainted
    public void unloadAllChunks() {
        // ignore, ChunkGc unloads cubes
    }

    /**
     * Vanilla method, returns a Chunk (Column) only of it's already loaded.
     */
    @Nullable
    @Override
    public Chunk getLoadedColumn(int columnX, int columnZ) {
        Chunk chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(columnX, columnZ));
        return chunk == null ? currentlyLoadingColumn : chunk;
    }

    /**
     * Loads Chunk (Column) if it can be loaded from disk, or returns already loaded one.
     * Doesn't generate new Columns.
     */
    @Nullable
    @Override
    @Deprecated
    public Chunk loadChunk(int columnX, int columnZ) {
        return this.loadChunk(columnX, columnZ, null);
    }

    /**
     * Load chunk asynchronously. Currently, CubicChunks only loads synchronously.
     */
    @Nullable
    @Override
    @Deprecated
    public Chunk loadChunk(int columnX, int columnZ, @Nullable Runnable runnable) {
        if (runnable == null) {
            return getColumn(columnX, columnZ, Requirement.LOAD);
        }
        asyncGetColumn(columnX, columnZ, Requirement.LOAD, col -> runnable.run());
        return null;
    }

    /**
     * If this Column is already loaded - returns it.
     * Loads from disk if possible, otherwise generates new Column.
     */
    @Override
    public Chunk provideColumn(int cubeX, int cubeZ) {
        return getColumn(cubeX, cubeZ, Requirement.GENERATE);
    }

    @Override
    @Deprecated
    public Chunk provideChunk(int cubeX, int cubeZ) {
        return (Chunk) provideColumn(cubeX, cubeZ);
    }

    @Override
    public boolean saveChunks(boolean alwaysTrue, IProgressUpdate progressUpdater) {
        for (Cube cube : cubeMap) { // save cubes
            if (cube.needsSaving(true)) {
                this.cubeIO.saveCube(cube);
            }
        }
        for (Chunk chunk : loadedChunks) { // save columns
            // save the column
            if (chunk.needsSaving(alwaysTrue)) {
                this.cubeIO.saveColumn(chunk);
            }
        }

        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        // NOTE: the return value is completely ignored
        profiler.startSection("providerTick");
        long i = System.currentTimeMillis();
        Random rand = this.worldObj.rand;
        CubicPlayerManager playerCubeMap = ((CubicPlayerManager) this.worldObj.getPlayerManager());
        Iterator<Cube> watchersIterator = playerCubeMap.getCubeIterator();
        BooleanSupplier tickFaster = () -> System.currentTimeMillis() - i > 40;
        while (watchersIterator.hasNext()) {
            watchersIterator.next()
                .tickCubeServer(tickFaster, rand);
        }
        profiler.endSection();
        return false;
    }

    @Override
    public String makeString() {
        return "CubeProviderServer: " + this.loadedChunkHashMap.getNumHashElements()
            + " columns, "
            + this.cubeMap.getSize()
            + " cubes";
    }

    @Override
    public List<net.minecraft.world.biome.BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type,
        int x, int y, int z) {
        return cubeGen.getPossibleCreatures(type, x, y, z);
    }

    // @Nullable @Override
    // public BlockPos getNearestStructurePos(World worldIn, String name, BlockPos pos, boolean findUnexplored) {
    // return cubeGen.getClosestStructure(name, pos, findUnexplored);
    // }

    // getLoadedChunkCount() in ChunkProviderServer is fine - CHECKED: 1.10.2-12.18.1.2092

    @Override
    public boolean chunkExists(int cubeX, int cubeZ) {
        return this.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(cubeX, cubeZ)) != null;
    }

    // @Override // TODO: What it does? implement it
    // public boolean isInsideStructure(World p_193413_1_, String p_193413_2_, BlockPos p_193413_3_) {
    // return false;
    // }

    // ==============================
    // =====CubicChunks methods======
    // ==============================

    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ) {
        return getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE);
    }

    @Override
    public Cube getCube(CubePos coords) {
        return getCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @Nullable
    @Override
    public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
        return cubeMap.get(cubeX, cubeY, cubeZ);
    }

    @Nullable
    @Override
    public Cube getLoadedCube(CubePos coords) {
        return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
    }

    /**
     * Load a cube, asynchronously. The work done to retrieve the column is specified by the
     * {@link Requirement} <code>req</code>
     *
     * @param cubeX    Cube x position
     * @param cubeY    Cube y position
     * @param cubeZ    Cube z position
     * @param req      Work done to retrieve the column
     * @param callback Callback to be called when the load finishes. Note that <code>null</code> can be passed to the
     *                 callback if the work specified by <code>req</code> is not sufficient to provide a cube
     *
     * @see #getCube(int, int, int, Requirement) for the synchronous equivalent to this method
     */
    public void asyncGetCube(int cubeX, int cubeY, int cubeZ, Requirement req, Consumer<Cube> callback) {
        Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
        if (req == Requirement.GET_CACHED || (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
            callback.accept(cube);
            return;
        }

        if (cube == null) {
            CubeIOExecutor.queueCubeLoad(worldServer, cubeIO, this, cubeX, cubeY, cubeZ, loaded -> {
                Chunk col = getLoadedColumn(cubeX, cubeZ);
                if (col != null) {
                    assert !col.isEmpty();
                    onCubeLoaded(loaded, col);
                    // TODO: async loading in asyncGetCube?
                    loaded = postCubeLoadAttempt(cubeX, cubeY, cubeZ, loaded, col, req, false);
                }
                callback.accept(loaded);
            });
        }
    }

    @Nullable
    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement req) {
        return getCube(cubeX, cubeY, cubeZ, req, false);
    }

    @Nullable
    @Override
    public Cube getCubeNow(int cubeX, int cubeY, int cubeZ, Requirement req) {
        return getCube(cubeX, cubeY, cubeZ, req, true);
    }

    @Nullable
    private Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement req, boolean forceNow) {
        Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
        if (req == Requirement.GET_CACHED || (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
            return cube;
        }

        // try to get the Column
        Chunk column = getColumn(cubeX, cubeZ, req, forceNow);
        if (column == null) {
            return cube; // Column did not reach req, so Cube also does not
        }
        if (column.isEmpty()) {
            return emptyCube;
        }

        if (cube == null) {
            // loading a column may cause loading a cube when a mod accesses blocks in chunk data load event, check if
            // the cube became loaded
            cube = getLoadedCube(cubeX, cubeY, cubeZ);
        }

        if (cube == null) {
            // a little hack to fix StackOverflowError when loading TileEntities, as Cube methods are now redirected
            // into IColumn
            // Column needs cube to be loaded to add TileEntity, so make CubeProvider contain it already
            cube = (Cube) CubeIOExecutor.syncCubeLoad(worldServer, cubeIO, this, cubeX, cubeY, cubeZ)
                .getObject(); // TODO WATCH
            onCubeLoaded(cube, column);
        }

        return postCubeLoadAttempt(cubeX, cubeY, cubeZ, cube, column, req, forceNow);
    }

    @Override
    public boolean isCubeGenerated(int cubeX, int cubeY, int cubeZ) {
        return getLoadedCube(cubeX, cubeY, cubeZ) != null || cubeIO.cubeExists(cubeX, cubeY, cubeZ);
    }

    /**
     * After successfully loading a cube, add it to it's column and the lookup table
     *
     * @param cube   The cube that was loaded
     * @param column The column of the cube
     */
    private void onCubeLoaded(@Nullable Cube cube, Chunk column) {
        if (cube != null) {
            cubeMap.put(cube); // cache the Cube
            // synchronous loading may cause it to be called twice when async loading has been already queued
            // because AsyncWorldIOExecutor only executes one task for one cube and because only saving a cube
            // can modify one that is being loaded, it's impossible to end up with 2 versions of the same cube
            // This is only to prevents multiple callbacks for the same queued load from adding the same cube twice.
            if (!((IColumn) column).getLoadedCubes()
                .contains(cube)) {
                ((IColumn) column).addCube(cube);
                cube.onCubeLoad(); // init the Cube // TODO WATCH I THINK THIS HAPPENS ELSEWHERE IN IONBTREADER
            }
        }
    }

    /**
     * Process a recently loaded cube as per the specified effort level.
     *
     * @param cubeX  Cube x position
     * @param cubeY  Cube y position
     * @param cubeZ  Cube z positon
     * @param cube   The loaded cube, if loaded, else <code>null</code>
     * @param column The column of the cube
     * @param req    Work done on the cube
     *
     * @return The processed cube, or <code>null</code> if the effort level is not sufficient to provide a cube
     */
    @Nullable
    private Cube postCubeLoadAttempt(int cubeX, int cubeY, int cubeZ, @Nullable Cube cube, Chunk column,
        Requirement req, boolean forceNow) {
        // when async load+generate request is immediately followed by sync request, the async one will generate the
        // cube in callback, but it won't
        // change the async load request result, so the cube here will still be null. Just to make sure, get the cube
        // here
        // otherwise we may end up generating the same cube twice
        if (cube == null) {
            cube = getLoadedCube(cubeX, cubeY, cubeZ);
        }
        // Fast path - Nothing to do here
        if (req == Requirement.LOAD) {
            return cube;
        }
        if (req == Requirement.GENERATE && cube != null) {
            return cube;
        }
        if (cube == null) {
            if (!forceNow
                && cubeGen.pollAsyncCubeGenerator(cubeX, cubeY, cubeZ) != ICubeGenerator.GeneratorReadyState.READY) {
                return emptyCube;
            }
            // generate the Cube
            cube = generateCube(cubeX, cubeY, cubeZ, column, forceNow).orElse(null);
            if (cube == null) {
                return emptyCube;
            }
            if (req == Requirement.GENERATE) {
                return cube;
            }
        }

        if (!cube.isFullyPopulated()) {
            if (!forceNow
                && cubeGen.pollAsyncCubePopulator(cubeX, cubeY, cubeZ) != ICubeGenerator.GeneratorReadyState.READY) {
                return emptyCube;
            }
            // forced full population of this cube
            if (!populateCube(cube, forceNow)) {
                return cube;
            }
            if (req == Requirement.POPULATE) {
                return cube;
            }
        }

        if (!cube.isInitialLightingDone() || !cube.isSurfaceTracked()) {
            calculateDiffuseSkylight(cube);
        }
         if (!cube.isSurfaceTracked()) {
            cube.trackSurface();
         }

        return cube;
    }

    /**
     * Generate a cube at the specified position
     *
     * @param cubeX  Cube x position
     * @param cubeY  Cube y position
     * @param cubeZ  Cube z position
     * @param column Column of the cube
     *
     * @return The generated cube
     */
    private Optional<Cube> generateCube(int cubeX, int cubeY, int cubeZ, Chunk column, boolean forceGenerate) {
        return cubeGen.tryGenerateCube(column, cubeX, cubeY, cubeZ, forceGenerate)
            .map(cube -> {
                onCubeLoaded(cube, column);
                // don't bother resetting the primer if it wasn't used by the generator.
                // if the generator modifies the primer and then returns a different one it's
                // not implementing generateCube correctly, so we don't account for that case
                // if (primer == this.cubePrimer) {
                // primer.reset();
                // } // TODO MAKE SURE I DON'T NEED TO USE THIS. I don't think I have to since we make our own
                // byte/block arrays but it might be faster to keep an array here.
                return cube;
            });
    }

    /**
     * Populate a cube at the specified position, generating surrounding cubes as necessary
     *
     * @param cube The cube to populate
     */
    private boolean populateCube(Cube cube, boolean forceNow) {
        int cubeX = cube.getX();
        int cubeY = cube.getY();
        int cubeZ = cube.getZ();

        // for all cubes needed for full population - generate their population requirements
        Box fullPopulation = cubeGen.getFullPopulationRequirements(cube);
        boolean useVanillaGenerators = ((ICubicWorld) worldServer).isCubicWorld()
            && CubicChunksConfig.useVanillaChunkWorldGenerators
            && cube.getY() >= 0
            && cube.getY() < 16;
        if (useVanillaGenerators) {
            fullPopulation = new Box(0, -cube.getY(), 0, 0, 16 - cube.getY() - 1, 0).add(fullPopulation);
        }
        Set<Cube> newlyPopulatedCubes = new HashSet<>();
        boolean success = fullPopulation.allMatch((x, y, z) -> {
            // this also generates the cube
            Cube fullPopulationCube = getCube(x + cubeX, y + cubeY, z + cubeZ);
            Box newBox = cubeGen.getPopulationPregenerationRequirements(fullPopulationCube);
            if (useVanillaGenerators) {
                newBox = new Box(0, -cube.getY(), 0, 0, 16 - cube.getY() - 1, 0).add(newBox);
            }
            boolean generated = newBox.allMatch((nx, ny, nz) -> {
                int genX = cubeX + x + nx;
                int genY = cubeY + y + ny;
                int genZ = cubeZ + z + nz;
                return !(getCube(genX, genY, genZ, Requirement.GENERATE, forceNow) instanceof BlankCube);
            });
            if (!generated) {
                return false;
            }
            // a check for populators that populate more than one cube (vanilla compatibility generator)
            if (!fullPopulationCube.isPopulated()) {
                cubeGen.populate(fullPopulationCube);
                newlyPopulatedCubes.add(fullPopulationCube);
            }
            return true;
        });
        if (!success) {
            for (Cube newlyPopulatedCube : newlyPopulatedCubes) {
                newlyPopulatedCube.setPopulated(true);
            }
            return false;
        }
        if (useVanillaGenerators) {
            Box.Mutable box = fullPopulation.asMutable();
            box.setY1(0);
            box.setY2(0);
            box.forEachPoint((x, y, z) -> {
                Cube columnCube = getCube(cubeX + x, 0, cubeZ + z);
                if (!columnCube.isPopulated()) {
                    GameRegistry.generateWorld(
                        cubeX + x,
                        cubeZ + z,
                        worldObj,
                        currentChunkProvider,
                        worldObj.getChunkProvider());
                    columnCube.setFullyPopulated(true);
                }
            });
        }
        for (Cube newlyPopulatedCube : newlyPopulatedCubes) {
            newlyPopulatedCube.setPopulated(true);
        }
        cube.setFullyPopulated(true);
        return true;
    }

    /**
     * Initialize skylight for the cube at the specified position, generating surrounding cubes as needed.
     *
     * @param cube The cube to light up
     */
    private void calculateDiffuseSkylight(Cube cube) {
        ((ICubicWorldInternal) this.worldServer).getLightingManager()
            .doFirstLight(cube);
        cube.setInitialLightingDone(true);
    }

    /**
     * Retrieve a column, asynchronously. The work done to retrieve the column is specified by the
     * {@link Requirement} <code>req</code>
     *
     * @param columnX  Column x position
     * @param columnZ  Column z position
     * @param req      Work done to retrieve the column
     * @param callback Callback to be called when the column has finished loading. Note that the returned column is not
     *                 guaranteed to be non-null
     *
     * @see CubeProviderServer#getColumn(int, int, Requirement) for the synchronous variant of this method
     */
    public void asyncGetColumn(int columnX, int columnZ, Requirement req, Consumer<Chunk> callback) {
        Chunk column = getLoadedColumn(columnX, columnZ);
        if (column != null || req == Requirement.GET_CACHED) {
            callback.accept(column);
            return;
        }

        CubeIOExecutor.queueColumnLoad(worldServer, cubeIO, this, columnX, columnZ, chunk -> {
            chunk = postProcessColumn(columnX, columnZ, chunk, req, false);
            callback.accept(chunk);
        });
    }

    @Nullable
    @Override
    public Chunk getColumn(int columnX, int columnZ, Requirement req) {
        return getColumn(columnX, columnZ, req, false);
    }

    @Nullable
    private Chunk getColumn(int columnX, int columnZ, Requirement req, boolean forceNow) {
        Chunk column = getLoadedColumn(columnX, columnZ);
        if (column != null || req == Requirement.GET_CACHED) {
            return column;
        }

        column = CubeIOExecutor.syncColumnLoad(worldServer, cubeIO, this, columnX, columnZ)
            .getObject();
        column = postProcessColumn(columnX, columnZ, column, req, forceNow);

        return column;
    }

    /**
     * After loading a column, do work on it, where the work required is specified by <code>req</code>
     *
     * @param columnX X position of the column
     * @param columnZ Z position of the column
     * @param column  The loaded column, or <code>null</code> if the column couldn't be loaded
     * @param req     The amount of work to be done on the cube
     *
     * @return The postprocessed column, or <code>null</code>
     */
    @Nullable
    private Chunk postProcessColumn(int columnX, int columnZ, @Nullable Chunk column, Requirement req, boolean force) {
        Chunk loaded = getLoadedColumn(columnX, columnZ);
        if (loaded != null) {
            if (column != null && loaded != column) {
                throw new IllegalStateException("Duplicate column at " + columnX + ", " + columnZ + "!");
            }
            return loaded;
        }
        if (column != null) {
            loadedChunkHashMap.add(ChunkCoordIntPair.chunkXZ2Int(columnX, columnZ), column);
            column.lastSaveTime = this.worldServer.getTotalWorldTime(); // the column was just loaded
            column.onChunkLoad();
            return column;
        } else if (req == Requirement.LOAD) {
            return null;
        }

        if (!force && cubeGen.pollAsyncColumnGenerator(columnX, columnZ) != ICubeGenerator.GeneratorReadyState.READY) {
            return emptyColumn;
        }
        column = cubeGen.tryGenerateColumn(worldObj, columnX, columnZ, null, null, force)
            .orElse(null); // TODO WATCH
        if (column == null) {
            return emptyColumn;
        }

        loadedChunkHashMap.add(ChunkCoordIntPair.chunkXZ2Int(columnX, columnZ), column);
        column.lastSaveTime = this.worldServer.getTotalWorldTime(); // the column was just generated
        column.onChunkLoad();
        return column;
    }

    public String dumpLoadedCubes() {
        StringBuilder sb = new StringBuilder(10000).append("\n");
        for (Chunk chunk : this.loadedChunks) {
            if (chunk == null) {
                sb.append("column = null\n");
                continue;
            }
            sb.append("Column[")
                .append(chunk.xPosition)
                .append(", ")
                .append(chunk.zPosition)
                .append("] {");
            boolean isFirst = true;
            for (ICube cube : ((IColumn) chunk).getLoadedCubes()) {
                if (!isFirst) {
                    sb.append(", ");
                }
                isFirst = false;
                if (cube == null) {
                    sb.append("cube = null");
                    continue;
                }
                sb.append("Cube[")
                    .append(cube.getY())
                    .append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    @Nonnull
    public ICubeIO getCubeIO() {
        return cubeIO;
    }

    Iterator<Cube> cubesIterator() {
        return cubeMap.iterator();
    }

    @SuppressWarnings("unchecked")
    Iterator<Chunk> columnsIterator() {
        return loadedChunks.iterator();
    }

    boolean tryUnloadCube(Cube cube) {
        if (ForgeChunkManager.getPersistentChunksFor(worldObj)
            .containsKey(
                cube.getColumn()
                    .getChunkCoordIntPair())) {
            return false; // it will be unloaded later by ChunkGC
        }
        if (!cube.getTickets()
            .canUnload()) {
            return false; // There are tickets
        }

        // unload the Cube!
        cube.onCubeUnload();

        if (cube.needsSaving(true)) { // save the Cube, if it needs saving
            this.cubeIO.saveCube(cube);
        }

        if (cube.getColumn()
            .removeCube(cube.getY()) == null) {
            throw new RuntimeException();
        }
        return true;
    }

    boolean tryUnloadColumn(Chunk column) {
        if (ForgeChunkManager.getPersistentChunksFor(worldObj)
            .containsKey(column.getChunkCoordIntPair())) {
            return false; // it will be unloaded later by ChunkGC
        }
        if (((IColumn) column).hasLoadedCubes()) {
            return false; // It has loaded Cubes in it (Cubes are to Columns, as tickets are to Cubes... in a way)
        }
        // PlayerChunkMap may contain reference to a column that for a while doesn't yet have any cubes generated
        if ((worldObj.getPlayerManager()
            .func_152621_a(column.xPosition, column.zPosition))) {
            return false;
        }
        // ask async loader if there are currently any cubes being loaded for this column
        // this should prevent hard to debug issues with columns being unloaded while cubes have reference to them
        if (!CubeIOExecutor.canDropColumn(worldServer, cubeIO, this, column.xPosition, column.zPosition)) {
            return false;
        }
        // column.unloadQueued = true;

        // unload the Column!
        column.onChunkUnload();

        if (column.needsSaving(true)) { // save the Column, if it needs saving
            this.cubeIO.saveColumn(column);
        }
        return true;
    }

    public ICubeGenerator getCubeGenerator() {
        return cubeGen;
    }

    public int getLoadedCubeCount() {
        return cubeMap.getSize();
    }
}
