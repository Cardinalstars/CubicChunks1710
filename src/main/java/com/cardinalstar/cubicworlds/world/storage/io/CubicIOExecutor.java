package com.cardinalstar.cubicworlds.world.storage.io;

import com.cardinalstar.cubicworlds.server.CubeProviderServer;
import com.cardinalstar.cubicworlds.server.CubicAnvilChunkLoader;
import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.util.AsynchronousExecutor;


public class CubicIOExecutor
{
    private static final int BASE_THREADS = 1;
    private static final int PLAYERS_PER_THREAD = 50;

    private static final AsynchronousExecutor<QueuedColumn, Chunk, Runnable, RuntimeException> columnInstance = new AsynchronousExecutor<QueuedColumn, Chunk, Runnable, RuntimeException>(new ColumnIOProvider(), BASE_THREADS);
    private static final AsynchronousExecutor<QueuedCube, Cube, Runnable, RuntimeException> cubeInstance = new AsynchronousExecutor<QueuedCube, Cube, Runnable, RuntimeException>(new CubeIOProvider(), BASE_THREADS);


    public static Chunk syncChunkLoad(World world, CubicAnvilChunkLoader loader, CubeProviderServer provider, int x, int z) {
        return columnInstance.getSkipQueue(new QueuedColumn(x, z, loader, world, provider));
    }

    public static void queueChunkLoad(World world, CubicAnvilChunkLoader loader, CubeProviderServer provider, int x, int z, Runnable runnable) {
        columnInstance.add(new QueuedColumn(x, z, loader, world, provider), runnable);
    }

    // Abuses the fact that hashCode and equals for QueuedChunk only use world and coords
    public static void dropQueuedChunkLoad(World world, int x, int z, Runnable runnable) {
        columnInstance.drop(new QueuedColumn(x, z, null, world, null), runnable);
    }

    public static void adjustColumnPoolSize(int players) {
        int size = Math.max(BASE_THREADS, (int) Math.ceil((double) players / PLAYERS_PER_THREAD));
        columnInstance.setActiveThreads(size);
    }

    public static Cube syncCubeLoad(World world, CubicAnvilChunkLoader loader, CubeProviderServer provider, int x, int y, int z) {
        return cubeInstance.getSkipQueue(new QueuedCube(x, y, z, loader, world, provider));
    }

    public static void queueCubeLoad(World world, CubicAnvilChunkLoader loader, CubeProviderServer provider, int x, int y, int z, Runnable runnable) {
        cubeInstance.add(new QueuedCube(x, y, z, loader, world, provider), runnable);
    }

    // Abuses the fact that hashCode and equals for QueuedChunk only use world and coords
    public static void dropQueuedCubeLoad(World world, int x, int y, int z, Runnable runnable) {
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



}
