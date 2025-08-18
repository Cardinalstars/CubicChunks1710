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
package com.cardinalstar.cubicchunks.server;


import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.world.IColumnWatcher;
import com.cardinalstar.cubicchunks.network.PacketColumn;
import com.cardinalstar.cubicchunks.network.PacketDispatcher;
import com.cardinalstar.cubicchunks.network.PacketHeightMapUpdate;
import com.cardinalstar.cubicchunks.network.PacketUnloadColumn;
import com.cardinalstar.cubicchunks.server.chunkio.async.forge.CubeIOExecutor;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BucketSorterEntry;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ColumnWatcher implements XZAddressable, BucketSorterEntry, IColumnWatcher
{

    @Nonnull private final CubicPlayerManager cubicPlayerManager;
    @Nonnull private final BitSet dirtyColumns = new BitSet(256);

    private long previousWorldTime;
    private final ChunkCoordIntPair chunkLocation;
    private boolean isLoaded = false;
    @Nonnull private Chunk chunk;
    private final List<EntityPlayerMP> playersWatchingChunk = new ArrayList();
    private final java.util.HashMap<EntityPlayerMP, Runnable> players = new java.util.HashMap<EntityPlayerMP, Runnable>();

    private Runnable loadedRunnable = new Runnable()
    {
        public void run()
        {
            ColumnWatcher.this.isLoaded = true;
        }
    };

    public boolean isSentToPlayers;
    ColumnWatcher(CubicPlayerManager cubicPlayerManager, ChunkCoordIntPair pos)
    {
        this.cubicPlayerManager = cubicPlayerManager;
        this.chunkLocation = pos;
        this.chunk = this.cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(chunkLocation.chunkXPos, chunkLocation.chunkZPos);
    }

    public boolean providePlayerChunk(boolean canGenerate) {
        if (isLoaded) {
            return false;
        }
        if (this.chunk != null) {
            return true;
        }
        if (canGenerate) {
            Chunk chunk = this.cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(chunkLocation.chunkXPos, chunkLocation.chunkZPos);
            if (chunk.isEmpty()) {
                return false;
            }
            this.chunk = chunk;
        } else {
            this.chunk = this.cubicPlayerManager.getWorldServer().getChunkProvider().loadChunk(chunkLocation.chunkXPos, chunkLocation.chunkZPos);
        }

        return this.chunk != null;
    }

    boolean hasPlayer()
    {
        return !playersWatchingChunk.isEmpty();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void addPlayer(EntityPlayerMP player) {
        assert this.chunk == null || this.chunk == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        if (playersWatchingChunk.contains(player)) {
            CubicChunks.LOGGER.debug("Failed to expand player. {} already is in chunk {}, {}", player,
                chunkLocation.chunkXPos,
                chunkLocation.chunkZPos);
            return;
        }
        if (this.playersWatchingChunk.isEmpty())
        {
            this.previousWorldTime = cubicPlayerManager.getWorldServer().getTotalWorldTime();
        }

        playersWatchingChunk.add(player);

        //always sent to players, no need to check it

        if (this.isSentToPlayers) {
            if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                PacketColumn message = new PacketColumn(chunk);
                PacketDispatcher.sendTo(message, player);
            } else {
                cubicPlayerManager.vanillaNetworkHandler.sendColumnLoadPacket(chunk, player);
            }
            //this.sendNearbySpecialEntities - done by cube entry
            MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(chunk.getChunkCoordIntPair(), player));
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092//TODO: remove it, the only different line is sending packet
    @Override
    public void removePlayer(EntityPlayerMP player) {
        assert (this.chunk == null || this.chunk == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ()));
        if (!playersWatchingChunk.contains(player)) {
            return;
        }
        if (this.chunk == null) {
            playersWatchingChunk.remove(player);
            if (playersWatchingChunk.isEmpty()) {
                if (!isLoaded) {
                    CubeIOExecutor.dropQueuedColumnLoad(
                        cubicPlayerManager.getWorldServer(), chunkLocation.chunkXPos, chunkLocation.chunkZPos, (c) -> loadedRunnable.run());
                }
                this.cubicPlayerManager.removeEntry(this);
            }
            return;
        }

        if (this.isSentToPlayers) {
            if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                PacketDispatcher.sendTo(new PacketUnloadColumn(chunkLocation), player);
            } else {
                cubicPlayerManager.vanillaNetworkHandler.sendColumnUnloadPacket(chunkLocation, player);
            }
        }

        playersWatchingChunk.remove(player);

        MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(chunkLocation, player));

        if (playersWatchingChunk.isEmpty()) {
            cubicPlayerManager.removeEntry(this);
        }
    }

    @Override
    public boolean containsPlayer(EntityPlayerMP playerMP) {
        return playersWatchingChunk.contains(playerMP);
    }

    @Override
    public Chunk getChunk() {
        return this.chunk;
    }

    @Override
    public ChunkCoordIntPair getPos() {
        return chunkLocation;
    }

    @Override
    public void IncreaseInhabitedTime()
    {
        this.chunk.inhabitedTime += cubicPlayerManager.getWorldServer().getTotalWorldTime() - this.previousWorldTime;
        this.previousWorldTime = cubicPlayerManager.getWorldServer().getTotalWorldTime();
    }

    //providePlayerChunk - ok

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean sendToPlayers() {
        if (this.isSentToPlayers) {
            return true;
        }
        if (this.chunk == null) {
            return false;
        }
        assert (this.chunk == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ()));
        try {

            PacketColumn message = new PacketColumn(chunk);
            for (EntityPlayerMP player : playersWatchingChunk) {
                if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                    PacketDispatcher.sendTo(message, player);
                } else {
                    cubicPlayerManager.vanillaNetworkHandler.sendColumnLoadPacket(chunk, player);
                }
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
        assert this.chunk == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        //done by cube watcher
    }


    @Override
    @Deprecated
    public void blockChanged(int x, int y, int z) {
        assert this.chunk == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        CubeWatcher watcher = cubicPlayerManager.getCubeWatcher(CubePos.fromBlockCoords(x, y, z));
        if (watcher != null) {
            watcher.blockChanged(x, y, z);
        }
    }

    @Override
    public void update() {
        if (!isSentToPlayers) {
            return;
        }
        assert this.chunk == this.cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ()) :
            "Column watcher " + this + " at " + this.chunkLocation + " contains column " + this.chunk + " but loaded column is " +
                this.cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        if (this.dirtyColumns.isEmpty()) {
            return;
        }
        assert this.chunk != null;
        for (EntityPlayerMP player : this.playersWatchingChunk) {
            if (this.cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                PacketDispatcher.sendTo(new PacketHeightMapUpdate(dirtyColumns, this.chunk), player);
            }
        }
        this.dirtyColumns.clear();
    }

    //containsPlayer, hasPlayerMatching, hasPlayerMatchingInRange, isAddedToChunkUpdateQueue, getChunk, getClosestPlayerDistance - ok

    @Override public int getX() {
        return this.chunkLocation.chunkXPos;
    }

    @Override public int getZ() {
        return this.chunkLocation.chunkZPos;
    }

    void heightChanged(int localX, int localZ) {
        if (!isSentToPlayers) {
            return;
        }
        assert this.chunk == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        if (this.dirtyColumns.isEmpty()) {
            cubicPlayerManager.addToUpdateEntry(this);
        }
        this.dirtyColumns.set(AddressTools.getLocalAddress(localX, localZ));
    }


    private long[] bucketDataEntry = null;

    @Override public long[] getBucketEntryData() {
        return bucketDataEntry;
    }

    @Override public void setBucketEntryData(long[] data) {
        bucketDataEntry = data;
    }
}
