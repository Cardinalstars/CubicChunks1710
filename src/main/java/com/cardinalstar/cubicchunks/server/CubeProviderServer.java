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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatProviderBase;
import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderCallback;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderServer;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.BucketSorterEntry;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.WatchersSortingList2D;
import com.cardinalstar.cubicchunks.util.WatchersSortingList3D;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.column.EmptyColumn;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

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
    private final CubeLoaderServer cubeLoader;

    // @Nonnull private final CubePrimer cubePrimer;
    @Nonnull
    private final ICubeGenerator cubeGen;
    @Nonnull
    private final Profiler profiler;

    private final WatchersSortingList3D<EagerCubeLoadRequest> eagerCubeLoads = new WatchersSortingList3D<>(
        0,
        this::getPlayers);

    private final WatchersSortingList2D<EagerColumnLoadRequest> eagerColumnLoads = new WatchersSortingList2D<>(
        0,
        this::getPlayers);

    private int columnsLoadedThisTick = 0;
    private int cubesLoadedThisTick = 0;

    private static final int MAX_NS_SPENT_LOADING = 10_000_000;

    private final ListMultimap<ChunkCoordIntPair, Runnable> pendingAsyncChunkLoads = MultimapBuilder.hashKeys()
        .arrayListValues()
        .build();

    private final ObjectLinkedOpenHashSet<CubeLoaderCallback> callbacks = new ObjectLinkedOpenHashSet<>();

    public CubeProviderServer(WorldServer worldServer, IChunkLoader chunkLoader, ICubeGenerator cubeGen) {
        super(
            worldServer,
            chunkLoader, // forge uses this in
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

            this.cubeLoader = new CubeLoaderServer(
                worldServer,
                StorageFormatProviderBase.REGISTRY.get(StorageFormatProviderBase.DEFAULT)
                    .provideStorage(worldServer, path),
                cubeGen,
                new LoadingCallbacks());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.emptyColumn = new EmptyColumn(worldServer, 0, 0);
        this.emptyCube = new BlankCube(emptyColumn);
    }

    class LoadingCallbacks implements CubeLoaderCallback {

        @Override
        public void onColumnLoaded(Chunk column) {
            long k = ChunkCoordIntPair.chunkXZ2Int(column.xPosition, column.zPosition);
            CubeProviderServer.this.loadedChunkHashMap.add(k, column);
            CubeProviderServer.this.loadedChunks.add(column);

            column.lastSaveTime = CubeProviderServer.this.worldServer.getTotalWorldTime();

            pendingAsyncChunkLoads.removeAll(new ChunkCoordIntPair(column.xPosition, column.zPosition))
                .forEach(Runnable::run);

            columnsLoadedThisTick++;

            callbacks.forEach(c -> c.onColumnLoaded(column));
        }

        @Override
        public void onColumnUnloaded(Chunk column) {
            long k = ChunkCoordIntPair.chunkXZ2Int(column.xPosition, column.zPosition);
            CubeProviderServer.this.loadedChunkHashMap.remove(k);
            CubeProviderServer.this.loadedChunks.remove(column);

            callbacks.forEach(c -> c.onColumnUnloaded(column));
        }

        @Override
        public void onCubeLoaded(Cube cube) {
            cubesLoadedThisTick++;

            callbacks.forEach(c -> c.onCubeLoaded(cube));
        }

        @Override
        public void onCubeGenerated(Cube cube, CubeLoaderServer.CubeInitLevel newLevel) {
            cubesLoadedThisTick++;

            callbacks.forEach(c -> c.onCubeGenerated(cube, newLevel));
        }

        @Override
        public void onCubeUnloaded(Cube cube) {
            callbacks.forEach(c -> c.onCubeUnloaded(cube));
        }
    }

    private List<EntityPlayer> getPlayers() {
        // worldServer == null when this provider is being constructed because the field isn't set before the sorting
        // lists call this method.
        // noinspection ConstantValue
        if (worldServer == null) return Collections.emptyList();

        return worldServer.playerEntities;
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
        return cubeLoader.getColumn(columnX, columnZ, Requirement.GET_CACHED);
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
        if (runnable != null) {
            pendingAsyncChunkLoads.put(new ChunkCoordIntPair(columnX, columnZ), runnable);
        }

        return getColumn(columnX, columnZ, Requirement.LOAD);
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
        return provideColumn(cubeX, cubeZ);
    }

    @Override
    public boolean saveChunks(boolean ignored, IProgressUpdate progressUpdater) {
        cubeLoader.save(true);

        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        tick();

        // NOTE: the return value is completely ignored
        return false;
    }

    public void registerCallback(CubeLoaderCallback callback) {
        if (callback != null) this.callbacks.add(callback);
    }

    public void removeCallback(CubeLoaderCallback callback) {
        if (callback != null) this.callbacks.remove(callback);
    }

    public void tick() {
        long start = System.currentTimeMillis();
        Random rand = this.worldObj.rand;
        BooleanSupplier tickFaster = () -> System.currentTimeMillis() - start > 40;

        profiler.startSection("Tick force loaded cubes");

        for (Cube cube : getForceLoadedCubes()) {
            cube.tickCubeServer(tickFaster, rand);
        }

        profiler.endStartSection("Tick watched cubes");

        for (Cube cube : ((CubicPlayerManager) worldServer.getPlayerManager()).getWatchedCubes()) {
            cube.tickCubeServer(tickFaster, rand);
        }

        profiler.endSection();

        if (columnsLoadedThisTick > 0) {
            CubicChunks.LOGGER.info("Loaded {} columns this tick", columnsLoadedThisTick);
        }

        if (cubesLoadedThisTick > 0) {
            CubicChunks.LOGGER.info("Loaded {} cubes this tick", cubesLoadedThisTick);
        }

        doEagerLoading();

        columnsLoadedThisTick = 0;
        cubesLoadedThisTick = 0;
    }

    private void doEagerLoading() {
        profiler.startSection("Eager object sorting");

        eagerColumnLoads.tick();
        eagerCubeLoads.tick();

        profiler.endStartSection("Eager object loading");

        int columns = 0, cubes = 0;

        Iterator<EagerColumnLoadRequest> colIter = eagerColumnLoads.iterator();

        long start = System.nanoTime();

        while ((System.nanoTime() - start) < MAX_NS_SPENT_LOADING && colIter.hasNext()) {
            EagerColumnLoadRequest request = colIter.next();
            colIter.remove();
            request.completed = true;

            cubeLoader.getColumn(request.pos.chunkXPos, request.pos.chunkZPos, request.effort);

            columns++;
        }

        Iterator<EagerCubeLoadRequest> cubeIter = eagerCubeLoads.iterator();

        while ((System.nanoTime() - start) < MAX_NS_SPENT_LOADING && cubeIter.hasNext()) {
            EagerCubeLoadRequest request = cubeIter.next();
            cubeIter.remove();
            request.completed = true;

            Cube cube = cubeLoader.getCube(request.pos.getX(), request.pos.getY(), request.pos.getZ(), request.effort);

            CubeLoaderServer.CubeInitLevel actual = cube == null ? CubeLoaderServer.CubeInitLevel.None
                : cube.getInitLevel();
            CubeLoaderServer.CubeInitLevel wanted = CubeLoaderServer.CubeInitLevel.fromRequirement(request.effort);

            if (actual.ordinal() < wanted.ordinal()) {
                CubicChunks.LOGGER.error(
                    "Could not init cube {},{},{} for eager request (wanted {}, returned {})",
                    request.pos.getX(),
                    request.pos.getY(),
                    request.pos.getZ(),
                    wanted,
                    actual);
            }

            cubes++;
        }

        if (columns > 0) {
            CubicChunks.LOGGER.info("Processed {} eager column loads this tick", columns);
        }

        if (cubes > 0) {
            CubicChunks.LOGGER.info("Processed {} eager cube loads this tick", cubes);
        }

        profiler.endSection();
    }

    private List<Cube> getForceLoadedCubes() {
        ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = ForgeChunkManager
            .getPersistentChunksFor(worldServer);

        worldServer.theProfiler.startSection("forcedChunkLoading");

        ArrayList<Cube> loadedCubes = new ArrayList<>();

        for (ChunkCoordIntPair pos : persistentChunks.keySet()) {
            @SuppressWarnings("unchecked")
            Collection<Cube> cubes = (Collection<Cube>) ((IColumn) worldServer
                .getChunkFromChunkCoords(pos.chunkXPos, pos.chunkZPos)).getLoadedCubes();

            for (Cube cube : cubes) {
                if (cube == null) continue;
                if (cube.isEmpty()) continue;
                if (!cube.isPopulated()) continue;

                loadedCubes.add(cube);
            }
        }

        worldServer.theProfiler.endSection();

        return loadedCubes;
    }

    @Override
    public String makeString() {
        return String.format("CubeProviderServer{loader=%s}", this.cubeLoader);
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return cubeGen.getPossibleCreatures(type, x, y, z);
    }

    // getLoadedChunkCount() in ChunkProviderServer is fine - CHECKED: 1.10.2-12.18.1.2092

    @Override
    public boolean chunkExists(int cubeX, int cubeZ) {
        return cubeLoader.getColumn(cubeX, cubeZ, Requirement.GET_CACHED) != null;
    }

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
        return cubeLoader.getLoadedCube(cubeX, cubeY, cubeZ);
    }

    @Nullable
    @Override
    public Cube getLoadedCube(CubePos coords) {
        return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @SuppressWarnings("unused")
    public class EagerColumnLoadRequest implements XZAddressable, BucketSorterEntry {

        public final ChunkCoordIntPair pos;
        private Requirement effort;
        private boolean completed;

        public EagerColumnLoadRequest(int cubeX, int cubeZ, Requirement effort) {
            this.pos = new ChunkCoordIntPair(cubeX, cubeZ);
            this.effort = effort;
        }

        public Requirement getEffort() {
            return effort;
        }

        public void setEffort(Requirement effort) {
            this.effort = effort;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void cancel() {
            CubeProviderServer.this.eagerColumnLoads.remove(this);
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof EagerColumnLoadRequest that)) return false;

            return pos.equals(that.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }

        @Override
        public String toString() {
            return "EagerCubeLoadRequest{" + "pos=" + pos + '}';
        }

        private long[] bucketDataEntry = null;

        @Override
        public long[] getBucketEntryData() {
            return bucketDataEntry;
        }

        @Override
        public void setBucketEntryData(long[] data) {
            bucketDataEntry = data;
        }

        @Override
        public int getX() {
            return pos.chunkXPos;
        }

        @Override
        public int getZ() {
            return pos.chunkZPos;
        }
    }

    @SuppressWarnings("unused")
    public class EagerCubeLoadRequest implements XYZAddressable, BucketSorterEntry {

        public final CubePos pos;
        private Requirement effort;
        private boolean completed;

        public EagerCubeLoadRequest(int cubeX, int cubeY, int cubeZ, Requirement effort) {
            this.pos = new CubePos(cubeX, cubeY, cubeZ);
            this.effort = effort;
        }

        public Requirement getEffort() {
            return effort;
        }

        public void setEffort(Requirement effort) {
            this.effort = effort;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void cancel() {
            CubeProviderServer.this.eagerCubeLoads.remove(this);
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof EagerCubeLoadRequest that)) return false;

            return pos.equals(that.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }

        @Override
        public String toString() {
            return "EagerCubeLoadRequest{" + "pos=" + pos + '}';
        }

        private long[] bucketDataEntry = null;

        @Override
        public long[] getBucketEntryData() {
            return bucketDataEntry;
        }

        @Override
        public void setBucketEntryData(long[] data) {
            bucketDataEntry = data;
        }

        @Override
        public int getX() {
            return pos.getX();
        }

        @Override
        public int getY() {
            return pos.getY();
        }

        @Override
        public int getZ() {
            return pos.getZ();
        }
    }

    public EagerColumnLoadRequest loadColumnEagerly(int x, int z, Requirement effort) {
        EagerColumnLoadRequest request = new EagerColumnLoadRequest(x, z, effort);

        eagerColumnLoads.add(request);

        return request;
    }

    public EagerCubeLoadRequest loadCubeEagerly(int x, int y, int z, Requirement effort) {
        EagerCubeLoadRequest request = new EagerCubeLoadRequest(x, y, z, effort);

        eagerCubeLoads.add(request);

        return request;
    }

    @Nullable
    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement effort) {
        Cube cube = cubeLoader.getCube(cubeX, cubeY, cubeZ, effort);

        return cube == null ? emptyCube : cube;
    }

    @Override
    public boolean cubeExists(int cubeX, int cubeY, int cubeZ) {
        return cubeLoader.cubeExists(cubeX, cubeY, cubeZ);
    }

    @Nullable
    @Override
    public Chunk getColumn(int columnX, int columnZ, Requirement effort) {
        return cubeLoader.getColumn(columnX, columnZ, effort);
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
    public ICubeLoader getCubeLoader() {
        return cubeLoader;
    }
}
