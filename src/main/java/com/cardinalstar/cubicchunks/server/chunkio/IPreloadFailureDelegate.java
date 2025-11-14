package com.cardinalstar.cubicchunks.server.chunkio;

import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.util.CubePos;

/// Tells the world generator to start preloading the noise arrays, etc for a cube or column.
/// Note that these methods are called on a background worker thread and need to be thread-safe.
public interface IPreloadFailureDelegate {

    void onColumnPreloadFailed(ChunkCoordIntPair pos);
    void onCubePreloadFailed(CubePos pos, CubeInitLevel actual, CubeInitLevel wanted);

}
