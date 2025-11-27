/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.network;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;

import org.joml.Vector2ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.BooleanArray2D;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.ints.IntArrayList;

@ParametersAreNonnullByDefault
public class PacketEncoderHeightMapUpdate extends CCPacketEncoder<PacketEncoderHeightMapUpdate.PacketHeightMapUpdate> {

    @Desugar
    public record PacketHeightMapUpdate(ChunkCoordIntPair chunk, BooleanArray2D updates, IntArrayList heights)
        implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.HeightMapUpdate.id;
        }
    }

    public PacketEncoderHeightMapUpdate() {}

    public static PacketHeightMapUpdate createPacket(BooleanArray2D updates, Chunk chunk) {
        ChunkCoordIntPair pos = chunk.getChunkCoordIntPair();
        IntArrayList heights = new IntArrayList();

        for (Vector2ic v : updates) {
            heights.add(((IColumnInternal) chunk).getTopYWithStaging(v.x(), v.y()));
        }

        return new PacketHeightMapUpdate(pos, updates.clone(), heights);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.HeightMapUpdate.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketHeightMapUpdate packet) {
        buffer.writeChunkPos(packet.chunk);

        buffer.writeByteArray(packet.updates.toByteArray());
        buffer.writeIntArray(packet.heights.toIntArray());
    }

    @Override
    public PacketHeightMapUpdate readPacket(CCPacketBuffer buffer) {
        ChunkCoordIntPair pos = buffer.readChunkPos();

        BooleanArray2D updates = new BooleanArray2D(16, 16, buffer.readByteArray());
        int[] heights = buffer.readIntArray();

        return new PacketHeightMapUpdate(pos, updates, IntArrayList.wrap(heights));
    }

    @Override
    public void process(World world, PacketHeightMapUpdate packet) {
        ICubicWorldInternal.Client worldClient = (ICubicWorldInternal.Client) world;
        CubeProviderClient cubeCache = worldClient.getCubeCache();

        Chunk column = cubeCache.provideColumn(packet.chunk.chunkXPos, packet.chunk.chunkZPos);
        if (column instanceof EmptyChunk) {
            CubicChunks.LOGGER
                .error("Ignored block update to blank column {},{}", packet.chunk.chunkXPos, packet.chunk.chunkZPos);
            return;
        }

        ClientHeightMap index = (ClientHeightMap) ((IColumn) column).getOpacityIndex();
        ILightingManager lm = worldClient.getLightingManager();

        int i = 0;

        for (Vector2ic v : packet.updates) {
            int height = packet.heights.getInt(i++);

            int oldHeight = index.getTopBlockY(v.x(), v.y());
            index.setHeight(v.x(), v.y(), height);
            lm.updateLightBetween(column, v.x(), oldHeight, height, v.y());
        }
    }
}
