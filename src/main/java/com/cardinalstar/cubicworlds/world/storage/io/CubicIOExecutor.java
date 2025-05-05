package com.cardinalstar.cubicworlds.world.storage.io;

import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.chunkio.QueuedChunk;
import net.minecraftforge.common.util.AsynchronousExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;


public class CubeIOExecutor
{
    private static final int BASE_THREADS = 1;
    private static final int PLAYERS_PER_THREAD = 50;

    private static final AsynchronousExecutor<QueuedColumn, Chunk, Runnable, RuntimeException> columnInstance = new AsynchronousExecutor<QueuedColumn, Chunk, Runnable, RuntimeException>(new ColumnIOProvider(), BASE_THREADS);
    private static final AsynchronousExecutor<QueuedColumn, Cube, Runnable, RuntimeException> cubeInstance = new AsynchronousExecutor<QueuedColumn, Cube, Runnable, RuntimeException>(new CubeIOProvider(), BASE_THREADS);


    public static net.minecraft.world.chunk.Chunk syncChunkLoad(net.minecraft.world.World world, net.minecraft.world.chunk.storage.AnvilChunkLoader loader, net.minecraft.world.gen.ChunkProviderServer provider, int x, int z) {
        return columnInstance.getSkipQueue(new QueuedColumn(x, z, loader, world, provider));
    }

    public static void queueChunkLoad(net.minecraft.world.World world, net.minecraft.world.chunk.storage.AnvilChunkLoader loader, net.minecraft.world.gen.ChunkProviderServer provider, int x, int z, Runnable runnable) {
        columnInstance.add(new QueuedColumn(x, z, loader, world, provider), runnable);
    }

    // Abuses the fact that hashCode and equals for QueuedChunk only use world and coords
    public static void dropQueuedChunkLoad(net.minecraft.world.World world, int x, int z, Runnable runnable) {
        columnInstance.drop(new QueuedColumn(x, z, null, world, null), runnable);
    }

    public static void adjustPoolSize(int players) {
        int size = Math.max(BASE_THREADS, (int) Math.ceil(players / PLAYERS_PER_THREAD));
        columnInstance.setActiveThreads(size);
    }

    public static void tick() {
        columnInstance.finishActive();
    }

}
