package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.MinecraftForge;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.async.TaskPool;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskExecutor;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskFuture;
import com.cardinalstar.cubicchunks.event.events.CubeEvent;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.DataUtils;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.Pair;

public class CubeIO implements ICubeIO, IThreadedFileIO {

    private final ICubicStorage storage;
    private final IPreloadFailureDelegate preloadFailures;
    private final ITaskExecutor<ChunkCoordIntPair, NBTTagCompound> columnLoadExecutor;
    private final ITaskExecutor<CubePos, NBTTagCompound> cubeLoadExecutor;

    private final LinkedTransferQueue<Pair<ChunkCoordIntPair, NBTTagCompound>> columnQueue = new LinkedTransferQueue<>();
    private final LinkedTransferQueue<Pair<CubePos, NBTTagCompound>> cubeQueue = new LinkedTransferQueue<>();

    private final Map<ChunkCoordIntPair, NBTTagCompound> pendingColumns = new ConcurrentHashMap<>();
    private final Map<CubePos, NBTTagCompound> pendingCubes = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private final Cache<ChunkCoordIntPair, NBTTagCompound> columnCache = ((CacheBuilder<ChunkCoordIntPair, NBTTagCompound>) (Object) CacheBuilder
        .newBuilder()).expireAfterAccess(60, TimeUnit.SECONDS)
            .softValues()
            .initialCapacity(512)
            .maximumSize(4096)
            .build();

    @SuppressWarnings("unchecked")
    private final Cache<CubePos, NBTTagCompound> cubeCache = ((CacheBuilder<CubePos, NBTTagCompound>) (Object) CacheBuilder
        .newBuilder()).expireAfterAccess(60, TimeUnit.SECONDS)
            .softValues()
            .initialCapacity(1024)
            .maximumSize(8192)
            .build();

    public CubeIO(ICubicStorage storage, IPreloadFailureDelegate preloadFailures) {
        this.storage = storage;
        this.preloadFailures = preloadFailures;

        columnLoadExecutor = tasks -> {
            try {
                var result = storage.readBatch(
                    new PosBatch(
                        DataUtils.mapToList(tasks, ITaskFuture::getTask),
                        Collections.emptyList()));

                for (var task : tasks) {
                    NBTTagCompound tag = result.columns.get(task.getTask());

                    task.finish(tag);
                }
            } catch (IOException e) {
                for (var task : tasks) {
                    task.fail(e);
                }
            }
        };

        cubeLoadExecutor = tasks -> {
            try {
                var result = storage.readBatch(
                    new PosBatch(
                        Collections.emptyList(),
                        DataUtils.mapToList(tasks, ITaskFuture::getTask)));

                for (var task : tasks) {
                    NBTTagCompound tag = result.cubes.get(task.getTask());

                    task.finish(tag);
                }
            } catch (IOException e) {
                for (var task : tasks) {
                    task.fail(e);
                }
            }
        };
    }

    @Override
    public boolean columnExists(ChunkCoordIntPair pos) {
        if (pendingColumns.containsKey(pos)) return true;
        if (columnCache.asMap()
            .containsKey(pos)) return true;

        try {
            if (storage.columnExists(pos)) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if column {} exists", pos, e);
        }

        return false;
    }

    @Override
    public boolean cubeExists(CubePos pos) {
        if (pendingCubes.containsKey(pos)) return true;
        if (cubeCache.asMap()
            .containsKey(pos)) return true;

        try {
            if (storage.cubeExists(pos)) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if cube {} exists", pos, e);
        }

        return false;
    }

    @Override
    public NBTTagCompound loadColumn(ChunkCoordIntPair pos) throws LoadFailureException {
        NBTTagCompound tag = pendingColumns.get(pos);

        if (tag != null) return (NBTTagCompound) tag.copy();

        tag = columnCache.getIfPresent(pos);

        if (tag != null) return (NBTTagCompound) tag.copy();

        try {
            tag = storage.readColumn(pos);

            if (tag == null) return null;

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read column {}", pos, e);
            throw new LoadFailureException("Could not read column", e);
        }
    }

    @Override
    public NBTTagCompound loadCube(CubePos pos) throws LoadFailureException {
        NBTTagCompound tag = pendingCubes.get(pos);

        if (tag != null) return (NBTTagCompound) tag.copy();

        tag = cubeCache.getIfPresent(pos);

        if (tag != null) return (NBTTagCompound) tag.copy();

        try {
            tag = storage.readCube(pos);

            if (tag == null) return null;

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read cube {}", pos, e);
            throw new LoadFailureException("Could not read cube", e);
        }
    }

    public void saveColumn(ChunkCoordIntPair pos, Chunk column) {
        // NOTE: this function blocks the world thread
        // make it as fast as possible by offloading processing to the IO thread
        // except we have to write the NBT in this thread to avoid problems
        // with concurrent access to world data structures

        // add the column to the save queue
        NBTTagCompound tag = IONbtWriter.write(column);

        this.pendingColumns.put(pos, tag);
        this.columnQueue.add(Pair.of(pos, tag));

        column.isModified = false;

        ThreadedFileIOBase.threadedIOInstance.queueIO(this);
    }

    public void saveCube(CubePos pos, Cube cube) {
        cube.markSaved();

        NBTTagCompound tag = IONbtWriter.write(cube);

        CubeEvent.SaveNBT event = new CubeEvent.SaveNBT(cube.getWorld(), cube.getCoords(), tag);

        MinecraftForge.EVENT_BUS.post(event);

        tag = event.tag;

        this.pendingCubes.put(pos, tag);
        this.cubeQueue.add(Pair.of(pos, tag));

        ThreadedFileIOBase.threadedIOInstance.queueIO(this);
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

    @Override
    public void preloadColumn(ChunkCoordIntPair pos) {
        TaskPool.submit(columnLoadExecutor, pos, tag -> {
            if (tag == null) {
                if (preloadFailures != null) preloadFailures.onColumnPreloadFailed(pos);
            } else {
                columnCache.put(pos, tag);
            }
        });
    }

    @Override
    public void preloadCube(CubePos pos, CubeInitLevel wanted) {
        TaskPool.submit(cubeLoadExecutor, pos, tag -> {
            CubeInitLevel actual = tag == null ? CubeInitLevel.None : IONbtReader.getCubeInitLevel(tag);

            if (tag == null || actual.ordinal() < wanted.ordinal()) {
                if (preloadFailures != null) {
                    preloadFailures.onCubePreloadFailed(pos, actual, wanted);
                }
            }

            if (tag != null) {
                cubeCache.put(pos, tag);
            }
        });
    }
}
