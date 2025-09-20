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
package com.cardinalstar.cubicchunks.server;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.world.IColumnWatcher;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.PacketEncoderColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderHeightMapUpdate;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadColumn;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BucketSorterEntry;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;

@ParametersAreNonnullByDefault
public class ColumnWatcher implements XZAddressable, BucketSorterEntry, IColumnWatcher {

    @Nonnull
    private final CubicPlayerManager cubicPlayerManager;
    private final CubeProviderServer cubeCache;
    @Nonnull
    private final BitSet dirtyColumns = new BitSet(256);

    private long previousWorldTime;
    private final ChunkCoordIntPair pos;
    @Nullable
    private Chunk column;
    private final List<EntityPlayerMP> playersWatchingChunk = new ArrayList();

    public boolean isSentToPlayers;

    private CubeProviderServer.EagerColumnLoadRequest request;

    public ColumnWatcher(CubicPlayerManager cubicPlayerManager, ChunkCoordIntPair pos) {
        this.cubicPlayerManager = cubicPlayerManager;
        this.pos = pos;
        this.cubeCache = ((ICubicWorldInternal.Server) cubicPlayerManager.getWorldServer()).getCubeCache();

        this.column = cubeCache.getLoadedColumn(pos.chunkXPos, pos.chunkZPos);

        if (column == null) {
            request = cubeCache
                .loadColumnEagerly(pos.chunkXPos, pos.chunkZPos, ICubeProviderServer.Requirement.GENERATE);
        }
    }

    public void onColumnLoaded(Chunk column) {
        this.column = column;
        request = null;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void addPlayer(EntityPlayerMP player) {
        if (playersWatchingChunk.contains(player)) {
            CubicChunks.LOGGER
                .debug("Failed to add player. {} already is in chunk {}, {}", player, pos.chunkXPos, pos.chunkZPos);
            return;
        }

        if (this.playersWatchingChunk.isEmpty()) {
            this.previousWorldTime = cubicPlayerManager.getWorldServer()
                .getTotalWorldTime();
        }

        playersWatchingChunk.add(player);

        // always sent to players, no need to check it

        if (this.isSentToPlayers) {
            assert column != null;
            PacketEncoderColumn.createPacket(column).sendToPlayer(player);
            MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(column.getChunkCoordIntPair(), player));
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092//TODO: remove it, the only different line is sending packet
    @Override
    public void removePlayer(EntityPlayerMP player) {
        if (!playersWatchingChunk.remove(player)) return;

        if (request != null) {
            request.cancel();
        }

        if (this.isSentToPlayers) {
            PacketEncoderUnloadColumn.createPacket(pos.chunkXPos, pos.chunkZPos).sendToPlayer(player);
        }

        if (column != null) {
            MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(pos, player));
        }

        if (playersWatchingChunk.isEmpty()) {
            cubicPlayerManager.removeEntry(this);
        }
    }

    @Override
    public boolean containsPlayer(EntityPlayerMP playerMP) {
        return playersWatchingChunk.contains(playerMP);
    }

    @Override
    public Chunk getColumn() {
        return this.column;
    }

    @Override
    public ChunkCoordIntPair getPos() {
        return pos;
    }

    @Override
    public void increaseInhabitedTime() {
        if (column != null) {
            this.column.inhabitedTime += cubicPlayerManager.getWorldServer()
                .getTotalWorldTime() - this.previousWorldTime;
        }
        this.previousWorldTime = cubicPlayerManager.getWorldServer()
            .getTotalWorldTime();
    }

    // providePlayerChunk - ok

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean sendToPlayers() {
        if (this.isSentToPlayers) return true;
        if (this.column == null) return false;

        try {
            var message = PacketEncoderColumn.createPacket(column);
            for (EntityPlayerMP player : playersWatchingChunk) {
                message.sendToPlayer(player);
            }
            isSentToPlayers = true;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        return true;
    }

    @Override
    @Deprecated
    public void sendToPlayer(EntityPlayerMP player) {
        // done by cube watcher
    }

    @Override
    @Deprecated
    public void blockChanged(int x, int y, int z) {
        CubeWatcher watcher = cubicPlayerManager.getCubeWatcher(CubePos.fromBlockCoords(x, y, z));

        if (watcher != null) {
            watcher.blockChanged(x, y, z);
        }
    }

    @Override
    public void update() {
        if (!isSentToPlayers) return;

        if (this.dirtyColumns.isEmpty()) return;

        assert this.column != null;

        var packet = PacketEncoderHeightMapUpdate.createPacket(dirtyColumns, this.column);

        for (EntityPlayerMP player : this.playersWatchingChunk) {
            packet.sendToPlayer(player);
        }

        this.dirtyColumns.clear();
    }

    // containsPlayer, hasPlayerMatching, hasPlayerMatchingInRange, isAddedToChunkUpdateQueue, getChunk,
    // getClosestPlayerDistance - ok

    @Override
    public int getX() {
        return this.pos.chunkXPos;
    }

    @Override
    public int getZ() {
        return this.pos.chunkZPos;
    }

    public void heightChanged(int localX, int localZ) {
        if (!isSentToPlayers) {
            return;
        }
        assert this.column == cubicPlayerManager.getWorldServer()
            .getChunkProvider()
            .provideChunk(getX(), getZ());
        if (this.dirtyColumns.isEmpty()) {
            cubicPlayerManager.addToUpdateEntry(this);
        }
        this.dirtyColumns.set(AddressTools.getLocalAddress(localX, localZ));
    }

    private long[] bucketDataEntry = null;

    @Override
    public long[] getBucketEntryData() {
        return bucketDataEntry;
    }

    @Override
    public void setBucketEntryData(long[] data) {
        bucketDataEntry = data;
    }

    public List<EntityPlayerMP> getWatchingPlayers() {
        return playersWatchingChunk;
    }
}
