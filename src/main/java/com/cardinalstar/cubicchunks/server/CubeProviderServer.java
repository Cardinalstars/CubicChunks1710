package com.cardinalstar.cubicchunks.core.server;

import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.util.CubeCoordIntTriple;
import com.cardinalstar.cubicchunks.world.column.IColumn;
import com.cardinalstar.cubicchunks.world.cube.ICube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.cardinalstar.cubicchunks.core.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.core.world.cube.Cube;
import com.cardinalstar.cubicchunks.core.world.cube.ICubeProvider;
import com.cardinalstar.cubicchunks.core.server.chunkio.async.forge.CubicIOExecutor;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CubeProviderServer extends ChunkProviderServer implements ICubeProviderServer, ICubeProviderInternal.Server
{
    public LongHashMap loadedCubesHashMap = new LongHashMap();
    public List<Cube> loadedCubes = new ArrayList<>();
    public ICubeProvider cubeProvider;
    private Chunk currentlyLoadingColumn;
    private final CubicAnvilChunkLoader cubeIO;

    public CubeProviderServer(WorldServer world, IChunkLoader loader, IChunkProvider provider) {
        super(world, loader, provider);

        File file = null;
        cubeIO = new CubicAnvilChunkLoader(file); // TODO
    }

    public Cube originalLoadCube(int x, int y, int z)
    {
        return null;
    }

    public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
        return (Cube) loadedCubesHashMap.getValueByKey(CubeCoordIntTriple.cubeXYZToLong(cubeX, cubeY, cubeZ));
    }

    @Nullable
    @Override
    public Cube getLoadedCube(CubeCoordIntTriple coords) {
        return null;
    }

    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ) {
        return null;
    }

    @Override
    public Cube getCube(CubeCoordIntTriple coords) {
        return null;
    }

    public void asyncGetCube(int cubeX, int cubeY, int cubeZ, Requirement req, Consumer<Cube> callback) {
        Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
        if (req == Requirement.GET_CACHED || (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
            callback.accept(cube);
            return;
        }

        if (cube == null) {
            CubicIOExecutor.queueCubeLoad(worldObj, cubeIO, this, cubeX, cubeY, cubeZ, loaded -> {
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

    public Chunk getLoadedColumn(int columnX, int columnZ) {
        long chunkHash = ChunkCoordIntPair.chunkXZ2Int(columnX, columnZ);
        Chunk chunk = (Chunk)this.loadedChunkHashMap.getValueByKey(chunkHash);
        return chunk == null ? currentlyLoadingColumn : chunk;
    }

    @Override
    public Chunk provideColumn(int x, int z) {
        return null;
    }

    public void asyncGetColumn(int columnX, int columnZ, Requirement req, Consumer<Chunk> callback) {
        Chunk column = getLoadedColumn(columnX, columnZ);
        if (column != null || req == Requirement.GET_CACHED) {
            callback.accept(column);
            return;
        }

        CubicIOExecutor.queueChunkLoad(worldObj, cubeIO, this, columnX, columnZ, callback);
    }

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
            column.lastSaveTime = this.worldObj.getTotalWorldTime(); // the column was just loaded // TODO: might need to be world server?
            column.onChunkLoad();
            return column;
        } else if (req == Requirement.LOAD) {
            return null;
        }

        if (!force && cubeGen.pollAsyncColumnGenerator(columnX, columnZ) != ICubeGenerator.GeneratorReadyState.READY) {
            return emptyColumn;
        }
        column = cubeGen.tryGenerateColumn(worldObj, columnX, columnZ, new ChunkPrimer(), force).orElse(null);
        if (column == null) {
            return emptyColumn;
        }

        loadedChunks.put(ChunkPos.asLong(columnX, columnZ), column);
        column.lastSaveTime = this.worldObj.getTotalWorldTime(); // the column was just generated
        column.onChunkLoad();
        return column;
    }

    /**
     * After successfully loading a cube, add it to it's column and the lookup table
     *
     * @param cube The cube that was loaded
     * @param column The column of the cube
     */
    private void onCubeLoaded(@Nullable Cube cube, Chunk column) {
        if (cube != null) {
            loadedCubes.add(cube); // cache the Cube
            //synchronous loading may cause it to be called twice when async loading has been already queued
            //because AsyncWorldIOExecutor only executes one task for one cube and because only saving a cube
            //can modify one that is being loaded, it's impossible to end up with 2 versions of the same cube
            //This is only to prevents multiple callbacks for the same queued load from adding the same cube twice.
            if (!((IColumn) column).getLoadedCubes().contains(cube)) {
                ((IColumn) column).addCube(cube);
                cube.onCubeLoad(); // init the Cube
            }
        }
    }

    @Nullable
    @Override
    public Chunk getColumn(int columnX, int columnZ, Requirement req) {
        return null;
    }

    @Nullable
    @Override
    public ICube getCube(int cubeX, int cubeY, int cubeZ, Requirement req) {
        return null;
    }

    @Nullable
    @Override
    public ICube getCubeNow(int cubeX, int cubeY, int cubeZ, Requirement req) {
        return null;
    }

    @Override
    public boolean isCubeGenerated(int cubeX, int cubeY, int cubeZ) {
        return false;
    }

    @Override
    public ICubeIO getCubeIO() {
        return null;
    }
}
