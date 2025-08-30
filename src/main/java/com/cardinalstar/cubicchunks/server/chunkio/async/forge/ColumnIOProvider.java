package com.cardinalstar.cubicchunks.server.chunkio.async.forge;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.event.events.CubeDataEvent;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.AsynchronousExecutor;
import net.minecraftforge.event.world.ChunkDataEvent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ColumnIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedColumn, ICubeIO.PartialData<Chunk>, Consumer<Chunk>, RuntimeException>
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Override
    public ICubeIO.PartialData<Chunk> callStage1(QueuedColumn queuedColumn) throws RuntimeException
    {
        ICubeIO.PartialData<Chunk> columnData = null;
        try
        {
            columnData = queuedColumn.loader.loadColumnAsyncPart(queuedColumn.world, queuedColumn.x, queuedColumn.z);
        }
        catch (Exception e)
        {
            if (CubicChunksConfig.ignoreCorruptedChunks) {
                columnData = new ICubeIO.PartialData<>(null, null);
            }
            queuedColumn.exception = e;
            CubicChunks.LOGGER.error("Could not load column in {} @ ({}, {})", queuedColumn.world, queuedColumn.x, queuedColumn.z, e);
        }

        return columnData;
    }

    @Override
    public void callStage2(QueuedColumn queuedColumn, ICubeIO.PartialData<Chunk> columnData) throws RuntimeException
    {
        if (columnData == null)
        {
            throw new RuntimeException("Corrupted column at " + queuedColumn.x + ", " + queuedColumn.z, queuedColumn.exception);
        }
        if (columnData.getObject() != null)
        {
            queuedColumn.loader.loadColumnSyncPart(columnData);
            Chunk column = columnData.getObject();
            assert column != null;
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(column, columnData.getNbt()));
            column.lastSaveTime = queuedColumn.world.getTotalWorldTime();

            queuedColumn.provider.recreateStructures(column.xPosition, column.zPosition);
        }
    }

    @Override
    public void callStage3(QueuedColumn queuedColumn, ICubeIO.PartialData<Chunk> columnData, Consumer<Chunk> callback) throws RuntimeException
    {
        callback.accept(columnData.getObject());
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
