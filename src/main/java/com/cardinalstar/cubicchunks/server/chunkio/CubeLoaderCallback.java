package com.cardinalstar.cubicchunks.server.chunkio;

import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface CubeLoaderCallback {

    default void onColumnLoaded(Chunk column) {

    }

    default void onColumnUnloaded(Chunk column) {

    }

    /// This is always called when a cube is added to the world, regardless of its state.
    /// A cube is only valid once terraingen has occurred, but there's no guarantee that any higher init has occurred.
    /// This is not called when a cube is already loaded then generated further.
    default void onCubeLoaded(Cube cube) {

    }

    /// This is called when a cube is generated. It is called when a cube's init level increases - that is, when an
    /// already loaded cube (which may have been freshly loaded from the disk) is generated further, or when a cube is
    /// newly generated.
    default void onCubeGenerated(Cube cube, CubeInitLevel newLevel) {

    }

    default void onCubeUnloaded(Cube cube) {

    }
}
