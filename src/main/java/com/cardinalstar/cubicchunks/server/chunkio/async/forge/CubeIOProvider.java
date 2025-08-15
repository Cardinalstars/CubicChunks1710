package com.cardinalstar.cubicchunks.server.chunkio.async.forge;

import com.cardinalstar.cubicchunks.event.events.CubeDataEvent;
import com.cardinalstar.cubicchunks.server.CubicAnvilChunkLoader;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.AsynchronousExecutor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CubeIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedCube, Cube, Consumer<Cube>, RuntimeException>
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    @Override
    public Cube callStage1(QueuedCube queuedCube) throws RuntimeException // In 1.12 this is something like run
    {
        ICubeIO loader = queuedCube.loader;
        Object[] data = null;
        try {
            data = loader.loadCubeAsyncPart();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (data != null) {
            queuedCube.compound = (NBTTagCompound) data[1];
            return (Cube) data[0];
        }

        return null;
    }

    @Override
    public void callStage2(QueuedCube queuedCube, Cube cube) throws RuntimeException // In 1.12 this is something like runSychronousPart
    {
        if(cube == null) {
            // If the chunk loading failed just do it synchronously (may generate)
            queuedCube.provider.originalLoadCube(queuedCube.x, queuedCube.y, queuedCube.z);
            return;
        }

        // TODO: Needed? Where should this go?
        // queuedCube.loader.loadEntities(queuedCube.world, queuedCube.compound.getCompoundTag("Level"), cube);
        MinecraftForge.EVENT_BUS.post(new CubeDataEvent.Load(cube, queuedCube.compound)); // Don't call ChunkDataEvent.Load async
        cube.lastSaveTime = queuedCube.provider.worldObj.getTotalWorldTime();
        queuedCube.provider.loadedCubesHashMap.add(CubeCoordIntTriple.cubeXYZToLong(queuedCube.x, queuedCube.y, queuedCube.z), cube);
        queuedCube.provider.loadedCubes.add(cube);
        cube.onCubeLoad();

        // TODO: Implement a world provider that mimics vanilla
        if (queuedCube.provider.currentChunkProvider != null) {
            queuedCube.provider.currentChunkProvider.recreateStructures(queuedCube.x, queuedCube.y, queuedCube.z);
        }

        // TODO: Implement
        cube.populateCube(queuedCube.provider, queuedCube.provider, queuedCube.x, queuedCube.y, queuedCube.z);

    }

    @Override
    public void callStage3(QueuedCube parameter, Cube object, Consumer<Cube> callback) throws RuntimeException // This is also part of runSynchronousPart, but at the end
    {
        callback.accept(object);
    }

    @Override
    public Thread newThread(Runnable runnable)
    {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
