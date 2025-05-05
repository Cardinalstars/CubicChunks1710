package com.cardinalstar.cubicworlds.world.storage.io;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.AsynchronousExecutor;

import java.util.concurrent.atomic.AtomicInteger;

public class ColumnIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedColumn, Chunk, Runnable, RuntimeException>
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Override
    public Chunk callStage1(QueuedColumn parameter) throws RuntimeException
    {
        return null;
    }

    @Override
    public void callStage2(QueuedColumn parameter, Chunk object) throws RuntimeException
    {

    }

    @Override
    public void callStage3(QueuedColumn parameter, Chunk object, Runnable callback) throws RuntimeException
    {
        callback.run();
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
