package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.function.BiConsumer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.ForgeChunkManager;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.worldgen.CubeGeneratorsRegistry;
import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.LoadingData;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class CubeLoaderServer implements IThreadedFileIO, ICubeLoader {

    private final WorldServer world;
    private final ICubicStorage storage;
    private final ICubeGenerator generator;
    private final CubeLoaderCallback callback;

    private final XYZMap<CubeInfo> cubes = new XYZMap<>();
    private final XZMap<ColumnInfo> columns = new XZMap<>();

    private final LinkedTransferQueue<Pair<ChunkCoordIntPair, NBTTagCompound>> columnQueue = new LinkedTransferQueue<>();
    private final LinkedTransferQueue<Pair<CubePos, NBTTagCompound>> cubeQueue = new LinkedTransferQueue<>();

    private final Map<ChunkCoordIntPair, NBTTagCompound> pendingColumns = new ConcurrentHashMap<>();
    private final Map<CubePos, NBTTagCompound> pendingCubes = new ConcurrentHashMap<>();

    private boolean pauseLoadCalls = false;
    private final List<Cube> pendingCubeLoads = new ArrayList<>();
    private final List<Chunk> pendingColumnLoads = new ArrayList<>();

    public CubeLoaderServer(WorldServer world, ICubicStorage storage, ICubeGenerator generator, CubeLoaderCallback callback) {
        this.world = world;
        this.storage = storage;
        this.generator = generator;
        this.callback = callback;
    }

    public void pauseLoadCalls() {
        pauseLoadCalls = true;
    }

    public void unpauseLoadCalls() {
        pauseLoadCalls = false;

        for (Chunk column : pendingColumnLoads) {
            callback.onColumnLoaded(column);
        }

        pendingColumnLoads.clear();

        for (Cube cube : pendingCubeLoads) {
            callback.onCubeLoaded(cube);
        }

        pendingCubeLoads.clear();
    }

    @Override
    public Chunk getColumn(int x, int z, Requirement effort) {
        ColumnInfo column = getColumnInfo(x, z, effort);

        if (column != null && column.column != null) return column.column;
        if (effort == Requirement.GET_CACHED) return null;

        boolean created = false;

        if (column == null) {
            columns.put(column = new ColumnInfo(x, z));

            created = true;
        }

        if (column.initialize(effort)) {
            return column.column;
        } else {
            if (created) unloadColumn(column);
            return null;
        }
    }

    private void unloadColumn(ColumnInfo column) {
        if (!column.containedCubes.isEmpty()) {
            throw new IllegalStateException("Cannot unload column that still contains cubes");
        }

        columns.remove(column);

        column.onColumnUnloaded();
    }

    private ColumnInfo getColumnInfo(int x, int z, Requirement effort) {
        ColumnInfo column = columns.get(x, z);

        if (effort == Requirement.GET_CACHED) return column;

        boolean isNew = false;

        if (column == null) {
            column = new ColumnInfo(x, z);
            isNew = true;
        }

        if (!column.initialize(effort)) {
            return null;
        } else {
            if (isNew) columns.put(column);
            return column;
        }
    }

    @Override
    public Cube getLoadedCube(int x, int y, int z) {
        CubeInfo cube = cubes.get(x, y, z);

        return cube == null ? null : cube.cube;
    }

    @Override
    public boolean cubeExists(int x, int y, int z) {
        if (getLoadedCube(x, y, z) != null) return true;

        try {
            if (storage.cubeExists(new CubePos(x, y, z))) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if cube exists", e);
        }

        return false;
    }

    @Override
    public Cube getCube(int x, int y, int z, Requirement effort) {
        CubeInfo cubeInfo = cubes.get(x, y, z);

        Cube loaded = cubeInfo != null ? cubeInfo.cube : null;

        // Don't need to do anything because the cube is already initialized to the requested level
        if (loaded != null && cubeInfo.isInitedTo(effort)) return loaded;

        if (effort == Requirement.GET_CACHED) return null;

        if (cubeInfo == null) {
            cubes.put(cubeInfo = new CubeInfo(x, y, z));
        }

        boolean success = cubeInfo.initialize(effort);

        if (cubeInfo.cube == null) {
            cubes.remove(cubeInfo);
        }

        return success ? cubeInfo.cube : null;
    }

    @Override
    public void unloadCube(int x, int y, int z) {
        CubeInfo info = cubes.remove(x, y, z);

        if (info != null) info.onCubeUnloaded();
    }

    @Override
    public void save(boolean saveAll) {
        boolean processedLighting = false;

        for (CubeInfo cube : cubes) {
            if (cube.cube.needsSaving(saveAll)) {
                if (!processedLighting) {
                    // make sure all light updates are processed
                    ((ICubicWorldInternal) world).getLightingManager()
                        .processUpdates();

                    processedLighting = true;
                }

                saveCube(cube);
            }
        }

        for (ColumnInfo column : columns) {
            if (column.column.needsSaving(saveAll)) {
                saveColumn(column);
            }
        }

        ThreadedFileIOBase.threadedIOInstance.queueIO(this);
    }

    public void saveColumn(Chunk column) {
        if (column == null) return;

        ColumnInfo columnInfo = columns.get(column.xPosition, column.zPosition);

        if (columnInfo == null) return;

        if (columnInfo.column != column) {
            CubicChunks.LOGGER.error("Tried to save Chunk in the wrong CubeLoaderServer (tried to save {}, but had {}).", column, columnInfo.column);
            return;
        }

        saveColumn(columnInfo);
    }

    private void saveColumn(ColumnInfo column) {
        // NOTE: this function blocks the world thread
        // make it as fast as possible by offloading processing to the IO thread
        // except we have to write the NBT in this thread to avoid problems
        // with concurrent access to world data structures

        // add the column to the save queue
        NBTTagCompound tag = IONbtWriter.write(column.column);

        this.pendingColumns.put(column.pos, tag);
        this.columnQueue.add(Pair.of(column.pos, tag));

        column.column.isModified = false;

        // signal the IO thread to process the save queue
        ThreadedFileIOBase.threadedIOInstance.queueIO(this);
    }

    public void saveCube(Cube cube) {
        if (cube == null || cube instanceof BlankCube) return;

        CubeInfo cubeInfo = cubes.get(cube.getX(), cube.getY(), cube.getZ());

        if (cubeInfo == null) return;

        if (cubeInfo.cube != cube) {
            CubicChunks.LOGGER.error("Tried to save Cube in the wrong CubeLoaderServer (tried to save {}, but had {}).", cube, cubeInfo.cube);
            return;
        }

        saveCube(cubeInfo);
    }

    private void saveCube(CubeInfo cube) {
        if (cube.cube == null) return;

        // NOTE: this function blocks the world thread, so make it fast

        NBTTagCompound tag = IONbtWriter.write(cube.cube);

        cube.cube.markSaved();

        this.pendingCubes.put(cube.pos, tag);
        this.cubeQueue.add(Pair.of(cube.pos, tag));
    }

    public void doGC() {
        var persistentChunks = ForgeChunkManager.getPersistentChunksFor(world);

        List<CubePos> pendingCubeUnloads = new ArrayList<>();

        for (CubeInfo cubeInfo : cubes) {
            Cube cube = cubeInfo.cube;

            if (cube == null) continue;

            if (persistentChunks.containsKey(cube.getColumn().getChunkCoordIntPair())) continue;

            if (cube.getTickets().canUnload()) {
                pendingCubeUnloads.add(cubeInfo.pos);
            }
        }

        for (CubePos pos : pendingCubeUnloads) {
            unloadCube(pos.getX(), pos.getY(), pos.getZ());
        }

        List<ColumnInfo> pendingColumnUnloads = new ArrayList<>();

        for (ColumnInfo columnInfo : columns) {
            Chunk column = columnInfo.column;

            if (column == null) continue;

            if (persistentChunks.containsKey(columnInfo.pos)) continue;

            // It has loaded Cubes in it (Cubes are to Columns, as tickets are to Cubes... in a way)
            if (!columnInfo.containedCubes.isEmpty()) continue;;

            // PlayerChunkMap may contain reference to a column that for a while doesn't yet have any cubes generated
            if (world.getPlayerManager().func_152621_a(column.xPosition, column.zPosition)) continue;

            pendingColumnUnloads.add(columnInfo);
        }

        for (ColumnInfo column : pendingColumnUnloads) {
            unloadColumn(column);
        }
    }

    // only used by "/save-all flush" command
    @Override
    public void flush() throws IOException {
        try {
            this.drainQueueBlocking();

            this.storage.flush();
        } catch (InterruptedException e) {
            CubicChunks.LOGGER.catching(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.drainQueueBlocking();

            this.storage.close();
        } catch (InterruptedException e) {
            CubicChunks.LOGGER.catching(e);
        }
    }

    protected void drainQueueBlocking() throws InterruptedException {
        // This has to submit itself to the I/O thread again, and also run in a loop, in order to avoid a potential race
        // condition caused by the fact that ThreadedFileIOBase is incredibly stupid.
        // Don't you think that if you're going to make an ASYNCHRONOUS executor, that you'd ensure that the code is
        // ACTUALLY thread-safe? well, if you're mojang, apparently you don't.

        do {
            ThreadedFileIOBase.threadedIOInstance.queueIO(this);

            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
        } while (!this.pendingColumns.isEmpty() || !this.pendingCubes.isEmpty());
    }

    @Override
    public boolean writeNextIO() {
        try {
            ArrayList<Pair<ChunkCoordIntPair, NBTTagCompound>> columns = new ArrayList<>();
            ArrayList<Pair<CubePos, NBTTagCompound>> cubes = new ArrayList<>();

            // Consume all dirty cubes and columns
            this.columnQueue.drainTo(columns);
            this.cubeQueue.drainTo(cubes);

            Map<ChunkCoordIntPair, NBTTagCompound> columnMap = new ConcurrentHashMap<>();
            Map<CubePos, NBTTagCompound> cubeMap = new ConcurrentHashMap<>();

            // Put them back into a map
            columns.forEach(p -> columnMap.put(p.left(), p.right()));
            cubes.forEach(p -> cubeMap.put(p.left(), p.right()));

            // Forward all tasks to the storage at once
            this.storage.writeBatch(
                new ICubicStorage.NBTBatch(
                    Collections.unmodifiableMap(columnMap),
                    Collections.unmodifiableMap(cubeMap)));

            // Remove from queue using remove(key, value) in order to avoid removing entries which have been modified
            // since each request was queued.
            columnMap.forEach(this.pendingColumns::remove);
            cubeMap.forEach(this.pendingCubes::remove);
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not save chunks", e);
        }

        // false = ok?
        return false;
    }

    private class ColumnInfo implements XZAddressable {
        public final ChunkCoordIntPair pos;

        public NBTTagCompound tag;
        public Chunk column;

        public final ObjectOpenHashSet<CubeInfo> containedCubes = new ObjectOpenHashSet<>();

        public ColumnInfo(int x, int z) {
            this.pos = new ChunkCoordIntPair(x, z);
        }

        @Override
        public int getX() {
            return pos.chunkXPos;
        }

        @Override
        public int getZ() {
            return pos.chunkZPos;
        }

        public boolean initialize(Requirement effort) {
            if (column != null) return true;
            if (effort == Requirement.GET_CACHED) return false;

            try {
                if (loadNBT()) {
                    if (!loadColumn()) return false;
                }
            } catch (IOException e) {
                CubicChunks.LOGGER.error("Could not load chunk at x={},z={}", getX(), getZ(), e);
                return false;
            }

            if (column != null) return true;
            if (effort == Requirement.LOAD) return false;

            Optional<Chunk> generated = generator.tryGenerateColumn(world, pos.chunkXPos, pos.chunkZPos, null, null, true);

            if (!generated.isPresent()) return false;

            column = generated.get();

            onColumnLoaded();

            saveColumn(this);

            return true;
        }

        private boolean loadNBT() throws IOException {
            if (tag != null) return true;

            tag = pendingColumns.get(pos);

            if (tag == null) {
                tag = CubeLoaderServer.this.storage.readColumn(pos);
            }

            if (tag == null) return false;

            Collection<BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>>> asyncCallbacks = CubeGeneratorsRegistry.getColumnAsyncLoadingCallbacks();

            if (!asyncCallbacks.isEmpty()) {
                LoadingData<ChunkCoordIntPair> chunkLoadingData = new LoadingData<>(pos, tag);

                for (BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>> cons : asyncCallbacks) {
                    cons.accept(world, chunkLoadingData);
                }

                tag = chunkLoadingData.getNbt();
            }

            return true;
        }

        private boolean loadColumn() {
            this.column = IONbtReader.readColumn(world, getX(), getZ(), tag);

            if (column == null) return false;

            onColumnLoaded();

            this.tag = null;

            return true;
        }

        public void onColumnLoaded() {
            column.onChunkLoad();

            if (!pauseLoadCalls) {
                callback.onColumnLoaded(column);
            } else {
                pendingColumnLoads.add(column);
            }
        }

        public void onColumnUnloaded() {
            if (column.isModified) {
                saveColumn(this);
            }

            column.onChunkUnload();
            callback.onColumnUnloaded(column);
        }
    }

    private class CubeInfo implements XYZAddressable {
        public final CubePos pos;

        public NBTTagCompound tag;
        public Cube cube;

        public ColumnInfo column;

        private CubeSource source = CubeSource.None;

        enum CubeSource {
            None,
            Disk,
            Generated
        }

        public CubeInfo(int x, int y, int z) {
            this.pos = new CubePos(x, y, z);
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

        public boolean initialize(Requirement effort) {
            if (effort == Requirement.GET_CACHED) {
                return cube != null;
            }

            ensureColumn();

            // If we haven't already loaded the NBT tag from disk, try to load it
            if (tag == null) {
                try {
                    loadNBT();
                } catch (IOException e) {
                    CubicChunks.LOGGER.error("Could not load NBT for cube (x={},y={},z={})", getX(), getY(), getZ(), e);
                }
            }

            // If we only want to load the NBT, return whether it was successful or not.
            if (effort == Requirement.NBT) {
                return tag != null;
            }

            // If we loaded the NBT from disk successfully and we don't already have a cube loaded, try to load it
            if (tag != null && source == CubeSource.None) {
                try {
                    loadCube();
                } catch (IOException e) {
                    CubicChunks.LOGGER.error("Could not load cube into world (x={},y={},z={})", getX(), getY(), getZ(), e);
                }

                if (effort == Requirement.LOAD) return cube != null;
            }

            CubeInitLevel requestedInitLevel = CubeInitLevel.fromRequirement(effort);

            // We may have loaded a cube, but it wasn't to the required initialization level
            // Do some more work on it, to whatever level is required
            return generate(requestedInitLevel);
        }

        private boolean loadNBT() throws IOException {

            tag = pendingCubes.get(pos);
            if (tag == null) {
                tag = CubeLoaderServer.this.storage.readCube(pos);
            }

            if (tag == null) return false;

            Collection<BiConsumer<? super World, ? super LoadingData<CubePos>>> asyncCallbacks = CubeGeneratorsRegistry.getCubeAsyncLoadingCallbacks();

            if (!asyncCallbacks.isEmpty()) {
                LoadingData<CubePos> chunkLoadingData = new LoadingData<>(pos, tag);

                for (BiConsumer<? super World, ? super LoadingData<CubePos>> cons : asyncCallbacks) {
                    cons.accept(world, chunkLoadingData);
                }

                this.tag = chunkLoadingData.getNbt();
            }

            // TODO PROBABLY DON'T NEED TO DO THIS. WORLDS DON'T CHANGE VERSIONS.
            // if (nbt != null) { //fix column data
            // nbt = FMLCommonHandler.instance().getDataFixer().process(FixTypes.CHUNK, nbt);
            // }

            return true;
        }

        private void loadCube() throws IOException {
            ensureColumn();

            this.cube = IONbtReader.readCube(column.column, getX(), getY(), getZ(), tag);

            source = CubeSource.Disk;

            onCubeLoaded();

            this.tag = null;
        }

        public boolean isInitedTo(Requirement effort) {
            return isInitedTo(CubeInitLevel.fromRequirement(effort));
        }

        public boolean isInitedTo(CubeInitLevel initLevel) {
            return getInitLevel().ordinal() >= initLevel.ordinal();
        }

        public CubeInitLevel getInitLevel() {
            return cube == null ? CubeInitLevel.None : cube.getInitLevel();
        }

        private boolean generate(CubeInitLevel requestedInitLevel) {
            // The cube is already at the required init level, so we don't need to do any more work on it.
            if (isInitedTo(requestedInitLevel)) return true;

            // If this cube hasn't been generated at all (i.e. it was never on the disk), generate it
            if (source == CubeSource.None) {
                ensureColumn();

                Optional<Cube> generated = generator.tryGenerateCube(column.column, pos.getX(), pos.getY(), pos.getZ(), true);

                if (!generated.isPresent()) return false;

                cube = generated.get();
                source = CubeSource.Generated;

                onCubeLoaded();

                saveCube(this);
            }

            boolean generated = cube.getInitLevel() == CubeInitLevel.Generated;

            // We were only asked to generate it and we did so successfully
            if (requestedInitLevel == CubeInitLevel.Generated) return generated;

            // If this cube hasn't been populated at all, generate the required cubes and populate this cube.
            if (generated) {
                generator.populate(CubeLoaderServer.this, cube);
            }

            boolean populated = cube.getInitLevel() == CubeInitLevel.Populated;

            if (requestedInitLevel == CubeInitLevel.Populated) return populated;

            if (populated) {
                if (!cube.isInitialLightingDone() || !cube.isSurfaceTracked()) {
                    ((ICubicWorldInternal) world).getLightingManager().doFirstLight(cube);
                    cube.setInitialLightingDone(true);
                }

                if (!cube.isSurfaceTracked()) {
                    cube.trackSurface();
                }
            }

            return cube.getInitLevel() == CubeInitLevel.Lit;
        }

        public void onCubeLoaded() {
            ensureColumn();

            ((IColumn) column.column).addCube(cube);
            column.containedCubes.add(this);

            cube.onCubeLoad();

            if (!pauseLoadCalls) {
                callback.onCubeLoaded(cube);
            } else {
                pendingCubeLoads.add(cube);
            }
        }

        public void onCubeUnloaded() {
            if (this.isInitedTo(CubeInitLevel.Generated)) {
                saveCube(this);
            }

            ((IColumn) column.column).removeCube(getY());
            column.containedCubes.remove(this);

            cube.onCubeUnload();
            callback.onCubeUnloaded(cube);

            if (column.containedCubes.isEmpty()) {
                unloadColumn(column);
            }
        }

        private void ensureColumn() {
            if (column == null) {
                column = getColumnInfo(getX(), getZ(), Requirement.GENERATE);
            }
        }
    }

    public enum CubeSource {
        Unknown,
        Generated,
        Loaded
    }

    public enum CubeInitLevel {
        /**
         * The cube has been created, but not generated.
         */
        None,
        /**
         * The cube has been generated (terrain gen).
         * Corresponds to {@link Requirement#GENERATE}.
         */
        Generated,
        /**
         * The cube has been populated with structures.
         * Corresponds to {@link Requirement#POPULATE}.
         */
        Populated,
        /**
         * The cube's lighting has been calculated.
         * Corresponds to {@link Requirement#LIGHT}.
         */
        Lit;

        public static CubeInitLevel fromRequirement(Requirement effort) {
            return switch (effort) {
                case GET_CACHED, NBT, LOAD -> None;
                case GENERATE -> Generated;
                case POPULATE -> Populated;
                case LIGHT -> Lit;
            };
        }
    }
}
