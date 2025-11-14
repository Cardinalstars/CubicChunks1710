package com.cardinalstar.cubicchunks.server.chunkio;

import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface CubeLoaderCallback {

    default void onColumnLoaded(Chunk column) {

    }

    default void onColumnUnloaded(Chunk column) {

    }

    /**
     * This is called when a cube is first loaded into the world, regardless of its state.
     * A cube is only valid once terraingen has occured, but there's no guarantee that any higher init has occurred.
     * This is not called when a cube is already loaded then generated further.
     */
    default void onCubeLoaded(Cube cube) {

    }

    /**
     * This is called when a cube is generated. It is called when a cube is loaded, then generated further, and when the
     * cube is initially generated from nothing.
     */
    default void onCubeGenerated(Cube cube, CubeInitLevel newLevel) {

    }

    default void onCubeUnloaded(Cube cube) {

    }
}
