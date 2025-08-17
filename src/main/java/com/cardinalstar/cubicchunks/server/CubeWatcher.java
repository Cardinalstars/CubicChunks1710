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
import com.cardinalstar.cubicchunks.api.world.ICubeWatcher;
import com.cardinalstar.cubicchunks.entity.ICubicEntityTracker;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.PacketCubeBlockChange;
import com.cardinalstar.cubicchunks.network.PacketDispatcher;
import com.cardinalstar.cubicchunks.network.PacketUnloadCube;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BucketSorterEntry;
import com.cardinalstar.cubicchunks.util.CubeCoordIntTriple;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.ITicket;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.base.Predicate;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import gnu.trove.list.TShortList;
import gnu.trove.list.array.TShortArrayList;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.MinecraftForge;

import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class CubeWatcher implements ITicket, ICubeWatcher, BucketSorterEntry {

    private final Consumer<Cube> consumer;

    private final CubeProviderServer cubeCache;
    private CubicPlayerManager cubicPlayerManager;
    @Nullable private Cube cube;
    // Note: using wrap() so that the internal array is not Object[], and can be safely cast to EntityPlayerMP[]
    private final ObjectArrayList<EntityPlayerMP> players = ObjectArrayList.wrap(new EntityPlayerMP[0]);
    private final ObjectArrayList<EntityPlayerMP> playersToAdd = ObjectArrayList.wrap(new EntityPlayerMP[1], 0);

    private final TShortList dirtyBlocks = new TShortArrayList(64);
    private final CubePos cubePos;
    private long previousWorldTime = 0;
    private boolean sentToPlayers = false;
    private boolean loading = true;
    private boolean invalid = false;

    // CHECKED: 1.10.2-12.18.1.2092
    CubeWatcher(CubicPlayerManager cubicPlayerManager, CubePos cubePos) {
        this.cubePos = cubePos;
        this.cubicPlayerManager = cubicPlayerManager;
        this.cubeCache = ((ICubicWorldInternal.Server) cubicPlayerManager.getWorldServer()).getCubeCache();
        this.consumer = (c) -> {
            if (this.invalid) {
                return;
            }
            this.cube = c;
            this.loading = false;
            if (this.cube != null) {
                this.cube.getTickets().add(this);
            }
        };
        this.cubeCache.asyncGetCube(
            cubePos.getX(), cubePos.getY(), cubePos.getZ(),
            ICubeProviderServer.Requirement.LOAD,
            consumer);
    }

    void scheduleAddPlayer(EntityPlayerMP player) {
        if (!playersToAdd.contains(player)) {
            playersToAdd.add(player);
        }
    }

    void removeScheduledAddPlayer(EntityPlayerMP player) {
        playersToAdd.remove(player); // TODO: why does rem() and remove() exist separately?
    }

    void addScheduledPlayers() {
        if (!playersToAdd.isEmpty()) {
            for (EntityPlayerMP player : playersToAdd.elements()) {
                if (player == null) {
                    break;
                }
                addPlayer(player);
            }
            playersToAdd.clear();
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092
    void addPlayer(EntityPlayerMP player) {
        if (this.players.contains(player)) {
            CubicChunks.LOGGER.debug("Failed to add player. {} already is in cube at {}", player, cubePos);
            return;
        }
        if (this.players.isEmpty()) {
            this.previousWorldTime = this.getWorldTime();
        }
        this.players.add(player);

        if (this.sentToPlayers) {
            this.sendToPlayer(player);
            ((ICubicEntityTracker) cubicPlayerManager.getWorldServer().getEntityTracker())
                .sendLeashedEntitiesInCube(player, this.getCube());
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092
    void removePlayer(EntityPlayerMP player) {
        if (!this.players.contains(player)) {
            removeScheduledAddPlayer(player);
            if (this.players.isEmpty()) {
                cubicPlayerManager.removeEntry(this);
            }
            return;
        }
        // If we haven't loaded yet don't load the chunk just so we can clean it up
        if (this.cube == null) {
            this.players.remove(player);

            if (this.players.isEmpty()) {
                cubicPlayerManager.removeEntry(this);
            }
            return;
        }

        if (this.sentToPlayers) {
            PacketDispatcher.sendTo(new PacketUnloadCube(this.cubePos), player);
            cubicPlayerManager.removeSchedulesSendCubeToPlayer(cube, player);
        }

        this.players.remove(player);
        MinecraftForge.EVENT_BUS.post(new CubeUnWatchEvent(cube, cubePos, this, player));

        if (this.players.isEmpty()) {
            cubicPlayerManager.removeEntry(this);
        }
    }

    void invalidate() {
        if (loading) {
            AsyncWorldIOExecutor.dropQueuedCubeLoad(this.cubicPlayerManager.getWorldServer(),
                cubePos.getX(), cubePos.getY(), cubePos.getZ(),
                c -> this.cube = c);
        }
        invalid = true;
        playersToAdd.clear();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    boolean providePlayerCube(boolean canGenerate) {
        if (loading) {
            return false;
        }
        if (isWaitingForColumn()) {
            return false;
        }
        if (this.cube != null && (!canGenerate || !isWaitingForCube())) {
            return true;
        }
        int cubeX = cubePos.getX();
        int cubeY = cubePos.getY();
        int cubeZ = cubePos.getZ();

        cubicPlayerManager.getWorldServer().theProfiler.startSection("getCube");
        if (canGenerate) {
            this.cube = this.cubeCache.getCube(cubeX, cubeY, cubeZ, ICubeProviderServer.Requirement.LIGHT);
            assert this.cube != null;
            if (this.cube instanceof BlankCube) {
                this.cube = null;
                return false;
            }
            if (!this.cube.isFullyPopulated()) {
                return false;
            }
        } else {
            this.cube = this.cubeCache.getCube(cubeX, cubeY, cubeZ, ICubeProviderServer.Requirement.LOAD);
        }
        if (this.cube != null) {
            this.cube.getTickets().add(this);
        }
        cubicPlayerManager.getWorldServer().theProfiler.endStartSection("light");
        cubicPlayerManager.getWorldServer().theProfiler.endSection();

        return this.cube != null;
    }

    @Override public boolean isSentToPlayers() {
        return sentToPlayers;
    }

    boolean isWaitingForCube() {
        return this.cube == null || !this.cube.isFullyPopulated() || !this.cube.isInitialLightingDone() || !this.cube.isSurfaceTracked();
    }

    boolean isWaitingForColumn() {
        ColumnWatcher columnEntry = cubicPlayerManager.getColumnWatcher(this.cubePos.chunkPos());
        return columnEntry == null || !columnEntry.isSentToPlayers();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    SendToPlayersResult sendToPlayers() {
        if (this.sentToPlayers) {
            return SendToPlayersResult.ALREADY_DONE;
        }
        if (isWaitingForCube()) {
            return SendToPlayersResult.WAITING;
        }
        //can't send cubes before columns
        if (isWaitingForColumn()) {
            return SendToPlayersResult.WAITING;
        }
        if (!cubicPlayerManager.getColumnWatcher(cubePos.chunkPos()).isSentToPlayers) {
            return SendToPlayersResult.WAITING;
        }
        this.dirtyBlocks.clear();
        //set to true before adding to queue so that sendToPlayer can actually add it
        this.sentToPlayers = true;

        for (EntityPlayerMP playerEntry : this.players) {
            sendToPlayer(playerEntry);
        }

        return SendToPlayersResult.CUBE_SENT;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    private void sendToPlayer(EntityPlayerMP player) {
        if (!this.sentToPlayers) {
            return;
        }
        assert cube != null;
        cubicPlayerManager.scheduleSendCubeToPlayer(cube, player);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    void updateInhabitedTime() {
        final long now = getWorldTime();
        if (this.cube == null) {
            this.previousWorldTime = now;
            return;
        }

        long inhabitedTime = this.cube.getColumn().inhabitedTime;
        inhabitedTime += now - this.previousWorldTime;

        this.cube.getColumn().inhabitedTime = inhabitedTime;
        this.previousWorldTime = now;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    void blockChanged(int localX, int localY, int localZ) {
        //if we are adding the first one, add it to update list
        if (this.dirtyBlocks.isEmpty()) {
            cubicPlayerManager.addToUpdateEntry(this);
        }
        // If the number of changes is above clumpingThreshold
        // we send the whole cube, but to decrease network usage
        // forge sends only TEs that have changed,
        // so we need to know all changed blocks. So add everything
        // it's a set so no need to check for duplicates
        this.dirtyBlocks.add((short) AddressTools.getLocalAddress(localX, localY, localZ));
    }

    // CHECKED: 1.10.2-12.18.1.2092
    void update() {
        if (!this.sentToPlayers) {
            return;
        }
        assert cube != null;
        // are there any updates?
        if (this.dirtyBlocks.isEmpty()) {
            return;
        }

        World world = this.cube.getWorld();

        if (this.dirtyBlocks.size() >= ForgeModContainer.clumpingThreshold) {
            // send whole cube
            this.players.forEach(entry -> cubicPlayerManager.scheduleSendCubeToPlayer(cube, entry));
        } else {
            // send all the dirty blocks
            PacketCubeBlockChange packet = null;
            for (EntityPlayerMP player : this.players) {
                if (cubicPlayerManager.vanillaNetworkHandler.hasCubicChunks(player)) {
                    if (packet == null) { // create packet lazily
                        packet = new PacketCubeBlockChange(this.cube, this.dirtyBlocks);
                    }
                    PacketDispatcher.sendTo(packet, player);
                } else {
                    cubicPlayerManager.vanillaNetworkHandler.sendBlockChanges(dirtyBlocks, cube, player);
                }
            }
            // send the block entites on those blocks too
            this.dirtyBlocks.forEach(localAddress -> {
                BlockPos pos = cube.localAddressToBlockPos(localAddress);

                Block block = this.cube.getBlock(pos.getX(), pos.getY(), pos.getZ());
                int meta = this.cube.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ())
                if (block.hasTileEntity(meta)) {
                    sendBlockEntityToAllPlayers(world.getTileEntity(pos.getX(), pos.getY(), pos.getZ()));
                }
                return true;
            });
        }
        this.dirtyBlocks.clear();
    }

    private void sendBlockEntityToAllPlayers(@Nullable TileEntity blockEntity) {
        if (blockEntity == null) {
            return;
        }
        Packet packet = blockEntity.getDescriptionPacket();
        if (packet == null) {
            return;
        }
        sendPacketToAllPlayers(packet);
    }

    boolean containsPlayer(EntityPlayerMP player) {
        return this.players.contains(player);
    }

    boolean hasPlayerMatching(Predicate<EntityPlayerMP> predicate) {
        for (EntityPlayerMP e : players.elements()) {
            if (e == null) {
                break;
            }
            if (predicate.apply(e)) {
                return true;
            }
        }
        return false;
    }

    boolean hasPlayerMatchingInRange(Predicate<EntityPlayerMP> predicate, int range) {
        double d = range*range;
        double cx = cubePos.getXCenter();
        double cy = cubePos.getYCenter();
        double cz = cubePos.getZCenter();
        for (EntityPlayerMP e : players.elements()) {
            if (e == null) {
                break;
            }
            if (predicate.apply(e)) {
                double dist = cx - e.posX;
                dist *= dist;
                if (dist > d) {
                    continue;
                }
                double dy = cy - e.posY;
                dist += dy * dy;
                if (dist > d) {
                    continue;
                }
                double dz = cz - e.posZ;
                dist += dz * dz;
                if (dist > d) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private double getDistanceSq(CubePos cubePos, Entity entity) {
        double blockX = cubePos.getXCenter();
        double blockY = cubePos.getYCenter();
        double blockZ = cubePos.getZCenter();
        double dx = blockX - entity.posX;
        double dy = blockY - entity.posY;
        double dz = blockZ - entity.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override @Nullable public Cube getCube() {
        return this.cube;
    }

    double getClosestPlayerDistance() {
        double min = Double.MAX_VALUE;

        for (EntityPlayerMP entry : this.players.elements()) {
            if (entry == null) {
                break;
            }
            double dist = getDistanceSq(cubePos, entry);

            if (dist < min) {
                min = dist;
            }
        }

        return min;
    }

    private long getWorldTime() {
        return cubicPlayerManager.getWorldServer().getWorldTime();
    }

    private void sendPacketToAllPlayers(Packet packet) {
        for (EntityPlayerMP entry : this.players) {
            entry.playerNetServerHandler.sendPacket(packet);
        }
    }

    @Override public void sendPacketToAllPlayers(IMessage packet) {
        for (EntityPlayerMP entry : this.players) {
            PacketDispatcher.sendTo(packet, entry);
        }
    }

    CubePos getCubePos() {
        return cubePos;
    }

    @Override public int getX() {
        return this.cubePos.getX();
    }

    @Override public int getY() {
        return this.cubePos.getY();
    }

    @Override public int getZ() {
        return this.cubePos.getZ();
    }

    @Override public boolean shouldTick() {
        return false; // player seeing a cube is not enough to force ticking from the ticket system
    }

    private long[] bucketDataEntry = null;

    @Override public long[] getBucketEntryData() {
        return bucketDataEntry;
    }

    @Override public void setBucketEntryData(long[] data) {
        bucketDataEntry = data;
    }

    public enum SendToPlayersResult {
        ALREADY_DONE, CUBE_SENT, WAITING
    }
}
