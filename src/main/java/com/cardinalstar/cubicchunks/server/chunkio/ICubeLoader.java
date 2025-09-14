package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface ICubeLoader extends Flushable, Closeable {

    void pauseLoadCalls();

    void unpauseLoadCalls();

    Chunk getColumn(int x, int z, ICubeProviderServer.Requirement effort);

    default boolean columnExists(int x, int z) {
        return getColumn(x, z, ICubeProviderServer.Requirement.GET_CACHED) != null;
    }

    Cube getLoadedCube(int x, int y, int z);

    boolean cubeExists(int x, int y, int z);

    Cube getCube(int x, int y, int z, ICubeProviderServer.Requirement effort) throws IOException;

    void unloadCube(int x, int y, int z);

    void save(boolean saveAll);

    void saveColumn(Chunk column);

    void saveCube(Cube cube);

    void doGC();

    // only used by "/save-all flush" command
    @Override
    void flush() throws IOException;

    @Override
    void close() throws IOException;
}
