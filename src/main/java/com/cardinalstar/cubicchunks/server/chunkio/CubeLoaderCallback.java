package com.cardinalstar.cubicchunks.server.chunkio;

import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface CubeLoaderCallback {

    default void onColumnLoaded(Chunk column) {

    }

    default void onColumnUnloaded(Chunk column) {

    }


    default void onCubeLoaded(Cube cube) {

    }

    default void onCubeUnloaded(Cube cube) {

    }
}
