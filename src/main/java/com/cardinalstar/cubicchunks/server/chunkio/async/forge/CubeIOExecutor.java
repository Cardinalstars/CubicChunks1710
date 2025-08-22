package com.cardinalstar.cubicchunks.server.chunkio.async.forge;


import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.CubicAnvilChunkLoader;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.util.AsynchronousExecutor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class CubeIOExecutor
{
    private static final int BASE_THREADS = 1;
    private static final int PLAYERS_PER_THREAD = 50;

    private static final AsynchronousExecutor<QueuedColumn, ICubeIO.PartialData<IColumn>, Consumer<Chunk>, RuntimeException> columnInstance = new AsynchronousExecutor<QueuedColumn, ICubeIO.PartialData<IColumn>, Consumer<Chunk>, RuntimeException>(new ColumnIOProvider(), BASE_THREADS);
    private static final AsynchronousExecutor<QueuedCube, ICubeIO.PartialData<ICube>, Consumer<Cube>, RuntimeException> cubeInstance = new AsynchronousExecutor<QueuedCube, ICubeIO.PartialData<ICube>, Consumer<Cube>, RuntimeException>(new CubeIOProvider(), BASE_THREADS);

    private static final Multimap<QueuedColumn, QueuedCube> loadingCubesColumnMap =
        Multimaps.newMultimap(new ConcurrentHashMap<>(), Sets::newConcurrentHashSet);

    public static ICubeIO.PartialData<IColumn> syncColumnLoad(World world, ICubeIO loader, CubeProviderServer provider, int x, int z) {
        return columnInstance.getSkipQueue(new QueuedColumn(x, z, loader, world, provider));
    }

    public static void queueColumnLoad(World world, ICubeIO loader, CubeProviderServer provider, int x, int z, Consumer<Chunk> runnable) {
        columnInstance.add(new QueuedColumn(x, z, loader, world, provider), runnable);
    }

    // Abuses the fact that hashCode and equals for QueuedChunk only use world and coords
    public static void dropQueuedColumnLoad(World world, int x, int z, Consumer<Chunk> runnable) {
        columnInstance.drop(new QueuedColumn(x, z, null, world, null), runnable);
    }

    public static void adjustColumnPoolSize(int players) {
        int size = Math.max(BASE_THREADS, (int) Math.ceil((double) players / PLAYERS_PER_THREAD));
        columnInstance.setActiveThreads(size);
    }

    public static ICubeIO.PartialData<ICube> syncCubeLoad(World world, ICubeIO loader, CubeProviderServer provider, int x, int y, int z) {
        return cubeInstance.getSkipQueue(new QueuedCube(x, y, z, loader, world, provider));
    }

    public static void queueCubeLoad(World world, ICubeIO loader, CubeProviderServer provider, int x, int y, int z, Consumer<Cube> consumer) // DONE I think
    {
        QueuedCube key = new QueuedCube(x, y, z, loader, world, provider);
        QueuedColumn columnKey = new QueuedColumn(x, z, loader, world, provider);
        loadingCubesColumnMap.put(columnKey, key);

        QueuedCube cube = new QueuedCube(x, y, z, loader, world, provider);
        cubeInstance.add(cube, consumer);
        cubeInstance.add(cube, (c -> loadingCubesColumnMap.remove(columnKey, key)));

        Chunk loadedIColumn = provider.getLoadedColumn(x, z);
        if (loadedIColumn  == null)
        {
            provider.asyncGetColumn(x, z, ICubeProviderServer.Requirement.LIGHT, cube::setColumn);
        }
        else
        {
            cube.setColumn(loadedIColumn);
        }
    }

    // Abuses the fact that hashCode and equals for QueuedChunk only use world and coords
    public static void dropQueuedCubeLoad(World world, int x, int y, int z, Consumer<Cube> runnable) {
        cubeInstance.drop(new QueuedCube(x, y, z, null, world, null), runnable);
    }



    public static void adjustCubePoolSize(int players) {
        int size = Math.max(BASE_THREADS, (int) Math.ceil((double) players / PLAYERS_PER_THREAD));
        cubeInstance.setActiveThreads(size);
    }

    public static void tick() {
        columnInstance.finishActive();
        cubeInstance.finishActive();
    }

    public static boolean canDropColumn(World world, ICubeIO loader, CubeProviderServer provider, int x, int z) {
        return !loadingCubesColumnMap.containsKey(new QueuedColumn(x, z, loader, world, provider));
    }

//    public static void shutdownNowBlocking() {
//        // best effort wait for up to 10 seconds each
//        // shut down cubes first to avoid a cube executor getting stuck waiting for it's column
//        cubeInstance.();
//        cubeTasks.clear();
//        try {
//            cubeThreadPool.awaitTermination(10, TimeUnit.SECONDS);
//        } catch (InterruptedException ignored) {
//        }
//        columnThreadPool.shutdownNow();
//        columnTasks.clear();
//        try {
//            columnThreadPool.awaitTermination(10, TimeUnit.SECONDS);
//        } catch (InterruptedException ignored) {
//        }
//        // initialize for next use
//        initExecutors();
//    }
}
