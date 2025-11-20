package com.cardinalstar.cubicchunks.server.chunkio;

import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.ChunkDataEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.MetaKey;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.event.events.ColumnEvent;
import com.cardinalstar.cubicchunks.event.events.CubeEvent;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.util.Array3D;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Setter;

public class CubeLoaderServer implements ICubeLoader {

    private static final MetaKey<CubeInfo> CUBE_INFO = new MetaKey<>() { };

    private final WorldServer world;
    private final ICubeIO cubeIO;
    private final IWorldGenerator generator;
    private final CubeLoaderCallback callback;

    private final XYZMap<CubeInfo> cubes = new XYZMap<>();
    private final XZMap<ColumnInfo> columns = new XZMap<>();

    private int pauseLoadCalls;
    private final List<Cube> pendingCubeLoads = new ArrayList<>();
    private final List<Chunk> pendingColumnLoads = new ArrayList<>();

    private Array3D<Cube> cache;
    @Setter
    private long now;

    public CubeLoaderServer(WorldServer world, ICubicStorage storage, IWorldGenerator generator,
        CubeLoaderCallback callback) {
        this.world = world;
        this.cubeIO = new CubeIO(storage, generator instanceof IPreloadFailureDelegate delegate ? delegate : null);
        this.generator = generator;
        this.callback = callback;
    }

    @Override
    public void pauseLoadCalls() {
        pauseLoadCalls++;
    }

    @Override
    public void unpauseLoadCalls() {
        if (pauseLoadCalls <= 0) {
            pauseLoadCalls = 0;
            return;
        }

        if (--pauseLoadCalls == 0) {
            for (Chunk column : pendingColumnLoads) {
                callback.onColumnLoaded(column);
            }

            pendingColumnLoads.clear();

            for (Cube cube : pendingCubeLoads) {
                callback.onCubeLoaded(cube);
            }

            pendingCubeLoads.clear();
        }
    }

    @Override
    public Chunk getColumn(int x, int z, Requirement effort) {
        ColumnInfo column = getColumnInfo(x, z, effort);

        return column != null ? column.column : null;
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

        if (column == null) {
            columns.put(column = new ColumnInfo(x, z));
        }

        boolean success;

        try {
            success = column.initialize(effort);
        } catch (Throwable throwable) {
            throw new RuntimeException(String.format("Could not generate column at %d,%d", x, z), throwable);
        }

        if (column.column == null) {
            columns.remove(column);
        }

        return success ? column : null;
    }

    private Cube lastCube;

    @Override
    public Cube getLoadedCube(int x, int y, int z) {
        if (cache != null) {
            Cube cube = cache.get(x, y, z);

            if (cube != null) return cube;
        } else {
            if (lastCube != null && lastCube.getX() == x && lastCube.getY() == y && lastCube.getZ() == z) {
                return lastCube;
            }
        }

        CubeInfo info = cubes.get(x, y, z);

        Cube cube = info == null ? null : info.cube;

        if (cache == null) {
            lastCube = cube;
        } else {
            cache.set(x, y, z, cube);
        }

        return cube;
    }

    @Override
    public boolean cubeExists(int x, int y, int z) {
        if (getLoadedCube(x, y, z) != null) return true;

        if (cubeIO.cubeExists(new CubePos(x, y, z))) return true;

        return false;
    }

    @Override
    public Cube getCube(int x, int y, int z, Requirement effort) {
        if (cache != null) {
            Cube cube = cache.get(x, y, z);

            if (cube != null && cube.getInitLevel()
                .ordinal()
                >= CubeInitLevel.fromRequirement(effort)
                    .ordinal()) {
                return cube;
            }
        }

        CubeInfo cubeInfo = cubes.get(x, y, z);

        Cube loaded = cubeInfo != null ? cubeInfo.cube : null;

        // Don't need to do anything because the cube is already initialized to the requested level
        if (loaded != null && cubeInfo.isInitedTo(effort)) return loaded;

        if (effort == Requirement.GET_CACHED) return null;

        if (cubeInfo == null) {
            cubes.put(cubeInfo = new CubeInfo(x, y, z));
        }

        boolean changed = cubeInfo.updateInitLevel();

        boolean success;

        try {
            success = cubeInfo.initialize(effort);
        } catch (Throwable throwable) {
            throw new RuntimeException(String.format("Could not generate cube at %d,%d,%d", x, y, z), throwable);
        }

        changed |= cubeInfo.updateInitLevel();

        if (cubeInfo.cube == null) {
            cubes.remove(cubeInfo);
        } else {
            if (success && changed) {
                callback.onCubeGenerated(cubeInfo.cube, cubeInfo.getInitLevel());
            }
        }

        if (success && cache != null) {
            cache.set(x, y, z, cubeInfo.cube);
        }

        if (success) {
            cubeInfo.lastAccess = now;
        }

        return success ? cubeInfo.cube : null;
    }

    @Override
    public void onCubeGenerated(Cube cube) {
        CubeInfo cubeInfo = cube.getMeta(CUBE_INFO);

        if (cubeInfo == null) return;

        if (cubeInfo.updateInitLevel()) {
            callback.onCubeGenerated(cubeInfo.cube, cubeInfo.getInitLevel());
        }
    }

    @Override
    public void cacheCubes(int x, int y, int z, int spanx, int spany, int spanz) {
        cache = new Array3D<>(spanx, spany, spanz, x, y, z, new Cube[spanx * spany * spanz]);
    }

    @Override
    public void uncacheCubes() {
        cache = null;
    }

    public void preloadColumn(ChunkCoordIntPair pos) {
        cubeIO.preloadColumn(pos);
    }

    public void preloadCube(CubePos pos, CubeInitLevel level) {
        cubeIO.preloadCube(pos, level);
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
            if (cube.cube != null && cube.cube.needsSaving(saveAll)) {
                if (!processedLighting) {
                    // make sure all light updates are processed
                    ((ICubicWorldInternal) world).getLightingManager()
                        .processUpdates();

                    processedLighting = true;
                }

                cubeIO.saveCube(cube.pos, cube.cube);
            }
        }

        for (ColumnInfo column : columns) {
            if (column.column != null && column.column.needsSaving(saveAll)) {
                cubeIO.saveColumn(column.pos, column.column);
            }
        }
    }

    @Override
    public void saveColumn(Chunk column) {
        if (column == null) return;

        ColumnInfo columnInfo = columns.get(column.xPosition, column.zPosition);

        if (columnInfo == null) return;

        if (columnInfo.column != column) {
            CubicChunks.LOGGER.error(
                "Tried to save Chunk in the wrong CubeLoaderServer (tried to save {}, but had {}).",
                column,
                columnInfo.column);
            return;
        }

        cubeIO.saveColumn(columnInfo.pos, column);
    }

    @Override
    public void saveCube(Cube cube) {
        if (cube == null || cube instanceof BlankCube) return;

        CubeInfo cubeInfo = cubes.get(cube.getX(), cube.getY(), cube.getZ());

        if (cubeInfo == null) return;

        if (cubeInfo.cube != cube) {
            CubicChunks.LOGGER.error(
                "Tried to save Cube in the wrong CubeLoaderServer (tried to save {}, but had {}).",
                cube,
                cubeInfo.cube);
            return;
        }

        cubeIO.saveCube(cubeInfo.pos, cube);
    }

    private static final int CUBE_GC_EXPIRY = 20 * 5;

    @Override
    public void doGC() {
        var persistentChunks = ForgeChunkManager.getPersistentChunksFor(world);
        CubicPlayerManager playerManager = (CubicPlayerManager) world.getPlayerManager();

        List<CubePos> pendingCubeUnloads = new ArrayList<>();

        int startCubes = cubes.getSize();
        int startCols = columns.getSize();

        final long expiry = now - CUBE_GC_EXPIRY;

        for (CubeInfo cubeInfo : cubes) {
            Cube cube = cubeInfo.cube;

            if (cube == null || cubeInfo.lastAccess > expiry) continue;

            if (persistentChunks.containsKey(
                cube.getColumn()
                    .getChunkCoordIntPair()))
                continue;

            if (playerManager.isCubeWatched(cube.getX(), cube.getY(), cube.getZ())) continue;

            if (cube.getTickets()
                .canUnload()) {
                pendingCubeUnloads.add(cubeInfo.pos);
            }
        }

        for (CubePos pos : pendingCubeUnloads) {
            unloadCube(pos.getX(), pos.getY(), pos.getZ());
        }

        int autoCols = columns.getSize();

        List<ColumnInfo> pendingColumnUnloads = new ArrayList<>();

        for (ColumnInfo columnInfo : columns) {
            Chunk column = columnInfo.column;

            if (column == null) continue;

            if (persistentChunks.containsKey(columnInfo.pos)) continue;

            // It has loaded Cubes in it (Cubes are to Columns, as tickets are to Cubes... in a way)
            if (!columnInfo.containedCubes.isEmpty()) continue;;

            // PlayerChunkMap may contain reference to a column that for a while doesn't yet have any cubes generated
            if (playerManager.func_152621_a(column.xPosition, column.zPosition)) continue;

            pendingColumnUnloads.add(columnInfo);
        }

        for (ColumnInfo column : pendingColumnUnloads) {
            unloadColumn(column);
        }

        CubicChunks.LOGGER.info("Garbage collected {} columns ({} -> {}) and {} cubes ({} -> {}). Removed {} columns automatically because they were empty.",
            pendingColumnUnloads.size(), startCols, columns.getSize(),
            pendingCubeUnloads.size(), startCubes, cubes.getSize(),
            startCols - autoCols);
    }

    @Override
    public void flush() throws IOException {
        cubeIO.flush();
    }

    @Override
    public void close() throws IOException {
        cubeIO.close();
    }

    private void handleSideEffects(GenerationResult<?> result) {
        for (Chunk column : result.columnSideEffects) {
            ColumnInfo info = columns.get(column.xPosition, column.zPosition);

            if (info != null && info.column != null) {
                CubicChunks.LOGGER.warn("Worldgen side-effect replaced column at {},{}!", column.xPosition, column.zPosition);
            }

            if (info == null) {
                info = new ColumnInfo(column.xPosition, column.zPosition);
                columns.put(info);
            }

            info.source = ObjectSource.GeneratedSideEffect;
            info.column = column;

            info.onColumnLoaded();
        }

        for (Cube cube : result.cubeSideEffects) {
            CubeInfo info = cubes.get(cube.getX(), cube.getY(), cube.getZ());

            if (info != null && info.cube != null) {
                CubicChunks.LOGGER.warn("Worldgen side-effect replaced cube at {},{},{}!", cube.getX(), cube.getY(), cube.getZ());
            }

            if (info == null) {
                info = new CubeInfo(cube.getX(), cube.getY(), cube.getZ());
                info.column = columns.get(cube.getX(), cube.getZ());
                cubes.put(info);
            }

            info.source = ObjectSource.GeneratedSideEffect;
            info.cube = cube;
            cube.setMeta(CUBE_INFO, info);

            info.onCubeLoaded();
            callback.onCubeGenerated(cube, cube.getInitLevel());
        }
    }

    private enum ObjectSource {
        None,
        Disk,
        Generated,
        GeneratedSideEffect
    }

    private class ColumnInfo implements XZAddressable {

        public final ChunkCoordIntPair pos;

        public NBTTagCompound tag;
        public Chunk column;
        public ObjectSource source;

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

            if (loadNBT()) {
                if (!loadColumn()) return false;
            }

            if (column != null) return true;
            if (effort == Requirement.LOAD) return false;

            GenerationResult<Chunk> result = generator.provideColumn(world, pos.chunkXPos, pos.chunkZPos);

            if (result == null) return false;

            this.column = result.object;
            source = ObjectSource.Generated;

            onColumnLoaded();

            cubeIO.saveColumn(pos, column);

            handleSideEffects(result);

            return true;
        }

        private boolean loadNBT() {
            if (tag != null) return true;

            tag = cubeIO.loadColumn(pos);

            if (tag == null) return false;

            source = ObjectSource.Disk;

            ColumnEvent.LoadNBT event = new ColumnEvent.LoadNBT(world, pos, tag);

            EVENT_BUS.post(event);

            tag = event.tag;

            return true;
        }

        private boolean loadColumn() {
            this.column = IONbtReader.readColumn(world, getX(), getZ(), tag);

            if (column == null) return false;

            EVENT_BUS.post(new ChunkDataEvent.Load(column, tag));

            onColumnLoaded();

            this.tag = null;

            return true;
        }

        public void onColumnLoaded() {
            column.lastSaveTime = world.getTotalWorldTime();

            ((IColumnInternal) column).setColumn(true);

            column.onChunkLoad();

            CubeLoaderServer.this.generator.recreateStructures(column);

            if (pauseLoadCalls == 0) {
                callback.onColumnLoaded(column);
            } else {
                pendingColumnLoads.add(column);
            }
        }

        public void onColumnUnloaded() {
            if (column.isModified) {
                cubeIO.saveColumn(pos, column);
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

        private ObjectSource source = ObjectSource.None;

        public boolean generating = false;
        public long lastAccess = 0;

        private CubeInitLevel lastKnownLevel = CubeInitLevel.None;

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

        public boolean initialize(Requirement effort) throws IOException {
            if (effort == Requirement.GET_CACHED) {
                return cube != null;
            }

            ensureColumn();

            // If we haven't already loaded the NBT tag from disk, try to load it
            if (tag == null) {
                loadNBT();
            }

            // If we only want to load the NBT, return whether it was successful or not.
            if (effort == Requirement.NBT) {
                return tag != null;
            }

            // If we loaded the NBT from disk successfully and we don't already have a cube loaded, try to load it
            if (tag != null && source == ObjectSource.None) {
                loadCube();

                if (effort == Requirement.LOAD) return cube != null;
            }

            CubeInitLevel requestedInitLevel = CubeInitLevel.fromRequirement(effort);

            // We may have loaded a cube, but it wasn't to the required initialization level
            // Do some more work on it, to whatever level is required
            return generate(requestedInitLevel);
        }

        private boolean loadNBT() {
            if (tag != null) return true;

            tag = cubeIO.loadCube(pos);

            if (tag == null) return false;

            CubeEvent.LoadNBT event = new CubeEvent.LoadNBT(world, pos, tag);

            EVENT_BUS.post(event);

            tag = event.tag;

            return true;
        }

        private void loadCube() throws IOException {
            ensureColumn();

            this.cube = IONbtReader.readCube(column.column, getX(), getY(), getZ(), tag);
            cube.setMeta(CUBE_INFO, this);

            source = ObjectSource.Disk;

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
            if (source == ObjectSource.None) {
                ensureColumn();

                // Column had a side effect that initialized this CubeInfo
                if (isInitedTo(requestedInitLevel)) return true;

                if (generating) {
                    throw new IllegalStateException(
                        "Cannot recursively generate a cube that is already being generated");
                }

                GenerationResult<Cube> result;
                try {
                    this.generating = true;

                    result = generator.provideCube(column.column, pos.getX(), pos.getY(), pos.getZ());
                } finally {
                    this.generating = false;
                }

                if (result == null) return false;

                this.cube = result.object;
                cube.setMeta(CUBE_INFO, this);

                source = ObjectSource.Generated;

                onCubeLoaded();

                handleSideEffects(result);
            }

            boolean generated = isInitedTo(CubeInitLevel.Generated);

            // We were only asked to generate it and we did so successfully
            if (requestedInitLevel == CubeInitLevel.Generated) return generated;
            if (!generated) return false;

            // If this cube hasn't been populated at all, generate the required cubes and populate this cube.
            generator.populate(cube);

            boolean populated = isInitedTo(CubeInitLevel.Populated);

            if (requestedInitLevel == CubeInitLevel.Populated) return populated;
            if (!populated) return false;

            if (!cube.isInitialLightingDone() || !cube.isSurfaceTracked()) {
                ((ICubicWorldInternal) world).getLightingManager()
                    .doFirstLight(cube);
                cube.setInitialLightingDone(true);
            }

            if (!cube.isSurfaceTracked()) {
                cube.trackSurface();
            }

            return cube.getInitLevel() == CubeInitLevel.Lit;
        }

        public void onCubeLoaded() {
            ensureColumn();

            updateInitLevel();

            ((IColumn) column.column).addCube(cube);
            column.containedCubes.add(this);

            cube.onCubeLoad();

            if (pauseLoadCalls == 0) {
                callback.onCubeLoaded(cube);
            } else {
                pendingCubeLoads.add(cube);
            }
        }

        public boolean updateInitLevel() {
            CubeInitLevel prev = lastKnownLevel;
            lastKnownLevel = getInitLevel();
            return prev != lastKnownLevel;
        }

        public void onCubeUnloaded() {
            if (this.isInitedTo(CubeInitLevel.Generated)) {
                cubeIO.saveCube(pos, cube);
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
}
