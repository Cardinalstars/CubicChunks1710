package com.cardinalstar.cubicchunks.server.chunkio.async.forge;

import com.cardinalstar.cubicchunks.core.server.chunkio.async.forge.QueuedColumn;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.AsynchronousExecutor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ColumnIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedColumn, Chunk, Consumer<Chunk>, RuntimeException>
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
    public void callStage3(QueuedColumn parameter, Chunk object, Consumer<Chunk> callback) throws RuntimeException
    {
        callback.accept(object);
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
