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
import com.cardinalstar.cubicchunks.mixin.server.IPlayerInstance;
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

import java.util.BitSet;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ColumnWatcher implements XZAddressable, BucketSorterEntry, IColumnWatcher
{

    @Nonnull private final Object backingPlayerInstance;
    @Nonnull private final CubicPlayerManager cubicPlayerManager;
    @Nonnull private final BitSet dirtyColumns = new BitSet(256);

    public boolean isSentToPlayers;
    ColumnWatcher(CubicPlayerManager cubicPlayerManager, ChunkCoordIntPair pos, Object backingPlayerInstance)
    {
        this.backingPlayerInstance = backingPlayerInstance;
        this.cubicPlayerManager = cubicPlayerManager;
    }

    public boolean providePlayerChunk(boolean canGenerate) {
        if (!((IPlayerInstance) backingPlayerInstance).isLoaded()) {
            return false;
        }
        if (((IPlayerInstance) backingPlayerInstance).getChunk() != null) {
            return true;
        }
        if (canGenerate) {
            Chunk chunk = this.cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(((IPlayerInstance) backingPlayerInstance).getPos().chunkXPos, ((IPlayerInstance) backingPlayerInstance).getPos().chunkZPos);
            if (chunk.isEmpty()) {
                return false;
            }
            ((IPlayerInstance) backingPlayerInstance).setChunk(chunk);
        } else {
            ((IPlayerInstance) backingPlayerInstance).setChunk(this.cubicPlayerManager.getWorldServer().getChunkProvider().loadChunk(((IPlayerInstance) backingPlayerInstance).getPos().chunkXPos, ((IPlayerInstance) backingPlayerInstance).getPos().chunkZPos));
        }

        return ((IPlayerInstance) backingPlayerInstance).getChunk() != null;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void addPlayer(EntityPlayerMP player) {
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == null || ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        if (((IPlayerInstance) backingPlayerInstance).getPlayers().contains(player)) {
            CubicChunks.LOGGER.debug("Failed to expand player. {} already is in chunk {}, {}", player,
                ((IPlayerInstance) backingPlayerInstance).getPos().chunkXPos,
                ((IPlayerInstance) backingPlayerInstance).getPos().chunkZPos);
            return;
        }
        if (((IPlayerInstance) backingPlayerInstance).getPlayers().isEmpty()) {
            ((IPlayerInstance) backingPlayerInstance).setLastUpdateInhabitedTime(cubicPlayerManager.getWorldServer().getTotalWorldTime());
        }

        ((IPlayerInstance) backingPlayerInstance).getPlayers().add(player);

        //always sent to players, no need to check it

        if (this.isSentToPlayers) {
            if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                PacketColumn message = new PacketColumn(((IPlayerInstance) backingPlayerInstance).getChunk());
                PacketDispatcher.sendTo(message, player);
            } else {
                cubicPlayerManager.vanillaNetworkHandler.sendColumnLoadPacket(((IPlayerInstance) backingPlayerInstance).getChunk(), player);
            }
            //this.sendNearbySpecialEntities - done by cube entry
            MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(((IPlayerInstance) backingPlayerInstance).getChunk().getChunkCoordIntPair(), player));
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092//TODO: remove it, the only different line is sending packet
    @Override
    public void removePlayer(EntityPlayerMP player) {
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == null || ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        if (!((IPlayerInstance) backingPlayerInstance).getPlayers().contains(player)) {
            return;
        }
        if (((IPlayerInstance) backingPlayerInstance).getChunk() == null) {
            ((IPlayerInstance) backingPlayerInstance).getPlayers().remove(player);
            if (((IPlayerInstance) backingPlayerInstance).getPlayers().isEmpty()) {
                if (!((IPlayerInstance) backingPlayerInstance).isLoaded()) {
                    CubeIOExecutor.dropQueuedColumnLoad(
                        cubicPlayerManager.getWorldServer(), ((IPlayerInstance) backingPlayerInstance).getPos().chunkXPos, ((IPlayerInstance) backingPlayerInstance).getPos().chunkZPos, (c) -> ((IPlayerInstance) backingPlayerInstance).getLoadedRunnable().run());
                }
                this.cubicPlayerManager.removeEntry(this);
            }
            return;
        }

        if (this.isSentToPlayers) {
            if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                PacketDispatcher.sendTo(new PacketUnloadColumn(((IPlayerInstance) backingPlayerInstance).getPos()), player);
            } else {
                cubicPlayerManager.vanillaNetworkHandler.sendColumnUnloadPacket(((IPlayerInstance) backingPlayerInstance).getPos(), player);
            }
        }

        ((IPlayerInstance) backingPlayerInstance).getPlayers().remove(player);

        MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(((IPlayerInstance) backingPlayerInstance).getChunk().getChunkCoordIntPair(), player));

        if (((IPlayerInstance) backingPlayerInstance).getPlayers().isEmpty()) {
            cubicPlayerManager.removeEntry(this);
        }
    }

    @Override
    public boolean containsPlayer(EntityPlayerMP playerMP) {
        return ((IPlayerInstance) backingPlayerInstance).getPlayers().contains(playerMP);
    }

    @Override
    public Chunk getChunk() {
        return ((IPlayerInstance) backingPlayerInstance).getChunk();
    }

    @Override
    public ChunkCoordIntPair getPos() {
        return ((IPlayerInstance) backingPlayerInstance).getPos();
    }

    @Override
    public void UpdateChunkInhabitedTime()
    {
        ((IPlayerInstance) backingPlayerInstance).increaseInhabitedTime(this.getChunk());
    }

    //providePlayerChunk - ok

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean sendToPlayers() {
        if (this.isSentToPlayers) {
            return true;
        }
        if (((IPlayerInstance) backingPlayerInstance).getChunk() == null) {
            return false;
        }
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        try {

            PacketColumn message = new PacketColumn(((IPlayerInstance) backingPlayerInstance).getChunk());
            for (EntityPlayerMP player : ((IPlayerInstance) backingPlayerInstance).getPlayers()) {
                if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                    PacketDispatcher.sendTo(message, player);
                } else {
                    cubicPlayerManager.vanillaNetworkHandler.sendColumnLoadPacket(((IPlayerInstance) backingPlayerInstance).getChunk(), player);
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
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        //done by cube watcher
    }

    //updateChunkInhabitedTime - ok

    @Override
    @Deprecated
    public void blockChanged(int x, int y, int z) {
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
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
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ()) :
            "Column watcher " + this + " at " + ((IPlayerInstance) backingPlayerInstance).getPos() + " contains column " + ((IPlayerInstance) backingPlayerInstance).getChunk() + " but loaded column is " +
                cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
        if (this.dirtyColumns.isEmpty()) {
            return;
        }
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() != null;
        for (EntityPlayerMP player : ((IPlayerInstance) backingPlayerInstance).getPlayers()) {
            if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                PacketDispatcher.sendTo(new PacketHeightMapUpdate(dirtyColumns, ((IPlayerInstance) backingPlayerInstance).getChunk()), player);
            }
        }
        this.dirtyColumns.clear();
    }

    //containsPlayer, hasPlayerMatching, hasPlayerMatchingInRange, isAddedToChunkUpdateQueue, getChunk, getClosestPlayerDistance - ok

    @Override public int getX() {
        return ((IPlayerInstance) backingPlayerInstance).getPos().chunkXPos;
    }

    @Override public int getZ() {
        return ((IPlayerInstance) backingPlayerInstance).getPos().chunkZPos;
    }

    void heightChanged(int localX, int localZ) {
        if (!isSentToPlayers) {
            return;
        }
        assert ((IPlayerInstance) backingPlayerInstance).getChunk() == cubicPlayerManager.getWorldServer().getChunkProvider().provideChunk(getX(), getZ());
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
