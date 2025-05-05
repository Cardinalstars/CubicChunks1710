package com.cardinalstar.cubicworlds.world.storage.io;

import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraftforge.common.util.AsynchronousExecutor;

public class CubeIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedCube, Cube, Runnable, RuntimeException>
{
    @Override
    public Cube callStage1(QueuedCube parameter) throws RuntimeException {
        return null;
    }

    @Override
    public void callStage2(QueuedCube parameter, Cube object) throws RuntimeException {

    }

    @Override
    public void callStage3(QueuedCube parameter, Cube object, Runnable callback) throws RuntimeException {

    }

    @Override
    public Thread newThread(Runnable r) {
        return null;
    }
}
