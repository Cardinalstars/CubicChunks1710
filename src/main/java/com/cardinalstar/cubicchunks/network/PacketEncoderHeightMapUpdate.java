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

import java.util.BitSet;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.github.bsideup.jabel.Desugar;

import gnu.trove.list.TByteList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;

@ParametersAreNonnullByDefault
public class PacketEncoderHeightMapUpdate extends CCPacketEncoder<PacketEncoderHeightMapUpdate.PacketHeightMapUpdate> {

    @Desugar
    public record PacketHeightMapUpdate(ChunkCoordIntPair chunk, TByteList updates, TIntList heights)
        implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.HeightMapUpdate.id;
        }
    }

    public PacketEncoderHeightMapUpdate() {}

    public static PacketHeightMapUpdate createPacket(BitSet updateSet, Chunk chunk) {
        ChunkCoordIntPair pos = chunk.getChunkCoordIntPair();
        TByteArrayList updates = new TByteArrayList();
        TIntArrayList heights = new TIntArrayList();

        for (int i = updateSet.nextSetBit(0); i >= 0; i = updateSet.nextSetBit(i + 1)) {
            updates.add((byte) i);
            heights.add(
                ((IColumnInternal) chunk).getTopYWithStaging(AddressTools.getLocalX(i), AddressTools.getLocalZ(i)));
        }

        return new PacketHeightMapUpdate(pos, updates, heights);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.HeightMapUpdate.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketHeightMapUpdate packet) {
        buffer.writeChunkPos(packet.chunk);

        int len = packet.updates.size();
        buffer.writeByte(len);

        for (int i = 0; i < len; i++) {
            buffer.writeByte(packet.updates.get(i) & 0xFF);
            buffer.writeVarIntToBuffer(packet.heights.get(i));
        }
    }

    @Override
    public PacketHeightMapUpdate readPacket(CCPacketBuffer buffer) {
        ChunkCoordIntPair pos = buffer.readChunkPos();

        int len = buffer.readByte();

        TByteArrayList updates = new TByteArrayList();
        TIntArrayList heights = new TIntArrayList();

        for (int i = 0; i < len; i++) {
            updates.add(buffer.readByte());
            heights.add(buffer.readVarIntFromBuffer());
        }

        return new PacketHeightMapUpdate(pos, updates, heights);
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

        int size = packet.updates.size();

        for (int i = 0; i < size; i++) {
            int packed = packet.updates.get(i) & 0xFF;
            int x = AddressTools.getLocalX(packed);
            int z = AddressTools.getLocalZ(packed);
            int height = packet.heights.get(i);

            int oldHeight = index.getTopBlockY(x, z);
            index.setHeight(x, z, height);
            lm.updateLightBetween(column, x, oldHeight, height, z);
        }
    }
}
