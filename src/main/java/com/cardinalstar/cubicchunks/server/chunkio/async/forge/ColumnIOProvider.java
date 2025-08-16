package com.cardinalstar.cubicchunks.server.chunkio.async.forge;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.AsynchronousExecutor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ColumnIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedColumn, ICubeIO.PartialData<IColumn>, Consumer<Chunk>, RuntimeException>
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Override
    public ICubeIO.PartialData<IColumn> callStage1(QueuedColumn queuedColumn) throws RuntimeException
    {
        return null;
    }

    @Override
    public void callStage2(QueuedColumn queuedColumn, ICubeIO.PartialData<IColumn> object) throws RuntimeException
    {

    }

    @Override
    public void callStage3(QueuedColumn queuedColumn, ICubeIO.PartialData<IColumn> columnData, Consumer<Chunk> callback) throws RuntimeException
    {
        callback.accept((Chunk) columnData.getObject());
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
