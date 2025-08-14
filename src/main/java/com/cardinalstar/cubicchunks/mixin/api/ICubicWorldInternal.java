/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2021 OpenCubicChunks
 *  Copyright (c) 2015-2021 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.cardinalstar.cubicchunks.mixin.api;

import com.cardinalstar.cubicchunks.api.*;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.SpawnCubes;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.core.util.world.CubeSplitTickList;
import com.cardinalstar.cubicchunks.util.world.CubeSplitTickSet;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;


import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal ICubicWorld additions.
 */
@ParametersAreNonnullByDefault
public interface ICubicWorldInternal extends ICubicWorld {
    /**
     * Updates the world
     */
    void tickCubicWorld();


    /**
     * Returns the {@link com.cardinalstar.cubicchunks.world.cube.ICubeProvider} for this world, or throws {@link com.cardinalstar.cubicchunks.api.NotCubicChunksWorldException}
     * if this is not a CubicChunks world.
     */
    @Override
    ICubeProviderInternal getCubeCache();

    /**
     * Returns the {@link ILightingManager} for this world, or throws {@link NotCubicChunksWorldException}
     * if this is not a CubicChunks world.
     *
     * @return lighting manager instance for this world
     */
    ILightingManager getLightingManager();

    @Override
    Cube getCubeFromCubeCoords(int cubeX, int cubeY, int cubeZ);

    @Override
    Cube getCubeFromBlockCoords(int x, int y, int z);

    void fakeWorldHeight(int height);

//    default BlockPos getTopSolidOrLiquidBlockVanilla(BlockPos pos) {
//        Chunk chunk = ((World) this).getChunk(pos);
//
//        BlockPos current = new BlockPos(pos.getX(), chunk.getTopFilledSegment() + 16, pos.getZ());
//        while (current.getY() >= 0) {
//            BlockPos next = current.down();
//            IBlockState state = chunk.getBlockState(next);
//
//            if (state.getMaterial().blocksMovement() && !state.getBlock().isLeaves(state, (World) this, next) && !state.getBlock().isFoliage((World) this, next)) {
//                break;
//            }
//            current = next;
//        }
//
//        return current;
//    }

    interface Server extends ICubicWorldInternal, ICubicWorldServer {

        /**
         * Initializes the world to be a CubicChunks world. Must be done before any players are online and before any chunks
         * are loaded. Cannot be used more than once.
         *
         * @param heightRange     world height range
         * @param generationRange expected height range for world generation. Maximum Y should be above 0.
         */
        void initCubicWorldServer(IntRange heightRange, IntRange generationRange);

        @Override
        CubeProviderServer getCubeCache();

        void removeForcedCube(ICube cube);

        void addForcedCube(ICube cube);

        XYZMap<ICube> getForcedCubes();

        XZMap<IColumn> getForcedColumns();

        CubeSplitTickSet getScheduledTicks();

        CubeSplitTickList getThisTickScheduledTicks();

        SpawnCubes getSpawnArea();

        void setSpawnArea(SpawnCubes spawn);

        CompatGenerationScope doCompatibilityGeneration();

        boolean isCompatGenerationScope();

        // TODO Do I really need?
        // VanillaNetworkHandler getVanillaNetworkHandler();
    }

    interface Client extends ICubicWorldInternal {

        /**
         * Initializes the world to be a CubicChunks world. Must be done before any players are online and before any chunks
         * are loaded. Cannot be used more than once.
         * @param heightRange world height range
         * @param generationRange expected height range for world generation. Maximum Y should be above 0.
         */
        void initCubicWorldClient(IntRange heightRange, IntRange generationRange);

        @Override
        CubeProviderClient getCubeCache();

        void setHeightBounds(int minHeight, int maxHeight);
    }

    interface CompatGenerationScope extends AutoCloseable {
        void close();
    }
}
