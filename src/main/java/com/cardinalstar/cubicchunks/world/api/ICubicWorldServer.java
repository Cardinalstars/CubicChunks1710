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

package com.cardinalstar.cubicchunks.world.api;

import com.cardinalstar.cubicchunks.util.CubeCoordIntTriple;
import com.cardinalstar.cubicchunks.world.ICubeGenerator;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.core.world.api.ICubeProviderServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public interface ICubicWorldServer extends ICubicWorld {
    ICubeProviderServer getCubeCache();

    ICubeGenerator getCubeGenerator();

    /**
     * Unloads or schedules unloading of no longer needed chunks from the world.
     * Use of this should generally be avoided, as this is done automatically.
     *
     * There are generally only a few valid uses of this method:
     *  * Generating a lot of new chunks (for example pregenerating terrain)
     *  * On user command
     *
     * Note: there are known bugs caused by this method in some situations,
     * that cause a chunk that contains a player to be unloaded when the player is moving
     * at very high speed. This is very unlikely to happen and has never been reported
     * outside of artificially created test setup with special player movement code.
     * This issue is probably not fixable. Automatic chunk unloading is not affected.
     */
    void unloadOldCubes();

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#forceChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}.
     *
     * Can accept tickets from different worlds.
     *
     * @param ticket ForgeChunkManager ticket
     * @param chunk position of the cube to force load
     */
    void forceChunk(ForgeChunkManager.Ticket ticket, CubeCoordIntTriple chunk);

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#reorderChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}.
     *
     * Can accept tickets from different worlds.
     *
     * @param ticket ForgeChunkManager ticket
     * @param chunk position of the cube to reorder
     */
    void reorderChunk(ForgeChunkManager.Ticket ticket, CubeCoordIntTriple chunk);

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#unforceChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}.
     *
     * Can accept tickets from different worlds.
     *
     * @param ticket ForgeChunkManager ticket
     * @param chunk position of the cube to unforce
     */
    void unforceChunk(ForgeChunkManager.Ticket ticket, CubePos chunk);

}
