package com.cardinalstar.cubicworlds.world.storage.io;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.AsynchronousExecutor;

public class ColumnIOProvider implements AsynchronousExecutor.CallBackProvider<QueuedColumn, Chunk, Runnable, RuntimeException>
{
    @Override
    public Chunk callStage1(QueuedColumn parameter) throws RuntimeException {
        return null;
    }

    @Override
    public void callStage2(QueuedColumn parameter, Chunk object) throws RuntimeException {

    }

    @Override
    public void callStage3(QueuedColumn parameter, Chunk object, Runnable callback) throws RuntimeException {

    }

    @Override
    public Thread newThread(Runnable r) {
        return null;
    }
}
