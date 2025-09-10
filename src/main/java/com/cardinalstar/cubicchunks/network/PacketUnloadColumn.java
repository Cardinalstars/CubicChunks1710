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

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.google.common.base.Preconditions;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

@ParametersAreNonnullByDefault
public class PacketUnloadColumn implements IMessage {

    private ChunkCoordIntPair chunkPos;

    public PacketUnloadColumn() {}

    public PacketUnloadColumn(ChunkCoordIntPair chunkPos) {
        this.chunkPos = chunkPos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.chunkPos = new ChunkCoordIntPair(buf.readInt(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(chunkPos.chunkXPos);
        buf.writeInt(chunkPos.chunkZPos);
    }

    ChunkCoordIntPair getColumnPos() {
        return Preconditions.checkNotNull(chunkPos);
    }

    public static class Handler extends AbstractClientMessageHandler<PacketUnloadColumn> {

        @Nullable
        @Override
        public void handleClientMessage(World world, EntityPlayer player, PacketUnloadColumn message,
            MessageContext ctx) {
            ICubicWorld worldClient = (ICubicWorld) world;
            CubeProviderClient cubeCache = (CubeProviderClient) worldClient.getCubeCache();

            ChunkCoordIntPair chunkPos = message.getColumnPos();
            cubeCache.unloadChunk(chunkPos.chunkXPos, chunkPos.chunkZPos);
        }
    }
}
