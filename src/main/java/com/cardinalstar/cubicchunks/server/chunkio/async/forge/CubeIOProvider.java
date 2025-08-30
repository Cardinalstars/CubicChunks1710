package com.cardinalstar.cubicchunks.server.chunkio.async.forge;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.event.events.CubeDataEvent;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.AsynchronousExecutor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CubeIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedCube, ICubeIO.PartialData<ICube>, Consumer<Cube>, RuntimeException>
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Override
    public ICubeIO.PartialData<ICube> callStage1(QueuedCube queuedCube) throws RuntimeException // In 1.12 this is something like run
    {
        ICubeIO.PartialData<ICube> cubeData = null;
        try {
            Chunk column = queuedCube.futureColumn.get();
            if (column.isEmpty())
            {
                cubeData = new ICubeIO.PartialData<>(null, null);
            }
            else
            {
                cubeData = queuedCube.loader.loadCubeAsyncPart(column, queuedCube.y);
            }
        }  catch (InterruptedException e) {
            throw new Error(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            if (CubicChunksConfig.ignoreCorruptedChunks) {
                cubeData = new ICubeIO.PartialData<>(null, null);
            }
            CubicChunks.LOGGER.error("Could not load cube in {} @ ({}, {}, {})", queuedCube.world, queuedCube.x, queuedCube.y, queuedCube.z, e);
        }

        if (cubeData != null) {
            queuedCube.compound = cubeData.getNbt();
            return cubeData;
        }
        return null;
    }

    // TODO WATCH THIS
    @Override
    public void callStage2(QueuedCube queuedCube, ICubeIO.PartialData<ICube> cubeData) throws RuntimeException // In 1.12 this is something like runSychronousPart
    {
        if (cubeData == null)
        {
            return;
        }
        if (cubeData.getObject() != null)
        {
            queuedCube.loader.loadCubeSyncPart(cubeData);
            ICube cube = cubeData.getObject();
            assert cube != null;
            MinecraftForge.EVENT_BUS.post(new CubeDataEvent.Load(cube, cubeData.getNbt()));
        }
    }

    @Override
    public void callStage3(QueuedCube parameter, ICubeIO.PartialData<ICube> object, Consumer<Cube> callback) throws RuntimeException // This is also part of runSynchronousPart, but at the end
    {
        callback.accept((Cube) object.getObject());
    }

    @Override
    public Thread newThread(Runnable runnable)
    {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
