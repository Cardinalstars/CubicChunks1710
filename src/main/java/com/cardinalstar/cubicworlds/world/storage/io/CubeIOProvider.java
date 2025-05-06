package com.cardinalstar.cubicworlds.world.storage.io;

import com.cardinalstar.cubicworlds.event.events.CubeDataEvent;
import com.cardinalstar.cubicworlds.server.CubicAnvilChunkLoader;
import com.cardinalstar.cubicworlds.util.CubeCoordIntTriple;
import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.AsynchronousExecutor;
import net.minecraftforge.event.world.ChunkDataEvent;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class CubeIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedCube, Cube, Runnable, RuntimeException>
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    @Override
    public Cube callStage1(QueuedCube queuedCube) throws RuntimeException
    {
        CubicAnvilChunkLoader loader = queuedCube.loader;
        Object[] data = null;
        try {
            data = loader.loadChunk__Async(queuedCube.world, queuedCube.x, queuedCube.z);
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
    public void callStage2(QueuedCube queuedCube, Cube cube) throws RuntimeException
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
    public void callStage3(QueuedCube parameter, Cube object, Runnable callback) throws RuntimeException
    {
        callback.run();
    }

    @Override
    public Thread newThread(Runnable runnable)
    {
        Thread thread = new Thread(runnable, "Cube I/O Executor Thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
