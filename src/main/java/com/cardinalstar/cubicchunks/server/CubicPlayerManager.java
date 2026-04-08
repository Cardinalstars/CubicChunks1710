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

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static net.minecraft.util.MathHelper.clamp_int;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeModContainer;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.MetaContainer;
import com.cardinalstar.cubicchunks.api.MetaKey;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal.Server;
import com.cardinalstar.cubicchunks.network.PacketEncoderColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderCubeBlockChange;
import com.cardinalstar.cubicchunks.network.PacketEncoderCubes;
import com.cardinalstar.cubicchunks.network.PacketEncoderHeightMapUpdate;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadColumn.PacketUnloadColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadCube;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadCube.PacketUnloadCube;
import com.cardinalstar.cubicchunks.server.CubeProviderServer.EagerCubeLoadRequest;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderCallback;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BooleanArray2D;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer.CubeStatus;
import com.cardinalstar.cubicchunks.visibility.CuboidalCubeSelector;
import com.cardinalstar.cubicchunks.visibility.WorldVisibilityChange;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

/**
 * A cubic chunks implementation of Player Manager.
 * <p>
 * This class manages loading and unloading cubes for players.
 */
@ParametersAreNonnullByDefault
public class CubicPlayerManager extends PlayerManager implements CubeLoaderCallback {

    private static final MetaKey<CubeChanges> CUBE_CHANGES = new MetaKey<>() {

    };

    private static final MetaKey<ColumnChanges> COLUMN_CHANGES = new MetaKey<>() {

    };

    /**
     * Mapping of entityId to PlayerCubeMap.PlayerWrapper objects.
     */
    private final Int2ObjectOpenHashMap<WatchingPlayer> players = new Int2ObjectOpenHashMap<>();

    private final CubeProviderServer provider;

    private final XYZMap<EagerCubeLoadRequest> cubeLoadRequests = new XYZMap<>();

    private final Set<Cube> deferredCubeSyncs = new ObjectOpenHashSet<>();

    private final Set<Chunk> deferredColumnSyncs = new ObjectOpenHashSet<>();

    private int horizontalViewDistance;
    private int verticalViewDistance;

    // these player adds will be processed on the next tick
    // this exists as temporary workaround to player respawn code calling addPlayer() before spawning
    // the player in world as it's spawning player in world that triggers sending cubic chunks world
    // information to client, this causes the server to send columns to the client before the client
    // knows it's a cubic chunks world delaying addPlayer() by one tick fixes it.
    // this should be fixed by hooking into the code in a different place to send the cubic chunks world information
    // (player respawn packet?)
    private final Set<EntityPlayerMP> pendingPlayerAddToCubeMap = new HashSet<>();

    public CubicPlayerManager(WorldServer worldServer) {
        super(worldServer);
        this.setPlayerViewDistance(
            worldServer.func_73046_m()
                .getConfigurationManager()
                .getViewDistance(),
            ((ICubicPlayerList) worldServer.func_73046_m()
                .getConfigurationManager()).getVerticalViewDistance());

        provider = ((Server) worldServer).getCubeCache();
        provider.registerCallback(this);
    }

    public Collection<Chunk> getColumns() {
        return Collections.emptyList();
    }

    public Collection<Cube> getCubes() {
        return Collections.emptyList();
    }

    /**
     * Updates all CubeWatchers and ColumnWatchers. Also sends packets to clients.
     */
    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void updatePlayerInstances() {
        getWorldServer().theProfiler.startSection("playerCubeMapUpdatePlayerInstances");

        getWorldServer().theProfiler.startSection("addPendingPlayers");

        for (EntityPlayerMP player : pendingPlayerAddToCubeMap) {
            if (player.addedToChunk) {
                addPlayer(player);
            }
        }

        pendingPlayerAddToCubeMap.removeIf(e -> e.addedToChunk);

        players.values().forEach(WatchingPlayer::flushCubes);

        syncColumns();
        syncCubes();

        getWorldServer().theProfiler.endStartSection("tickEntries");

        getWorldServer().theProfiler.endStartSection("unload");

        // if there are no players - unload everything
        if (this.players.isEmpty()) {
            WorldProvider worldprovider = this.getWorldServer().provider;

            if (!worldprovider.canRespawnHere()) {
                provider.unloadAllChunks();
            }
        }

        getWorldServer().theProfiler.endSection();// sendCubes
        getWorldServer().theProfiler.endSection();// playerCubeMapUpdatePlayerInstances
    }

    private long lastWorldTime;

    private void syncColumns() {
        long now = getWorldServer().getWorldTime();

        long delta = lastWorldTime == 0 ? 0 : now - lastWorldTime;

        this.lastWorldTime = now;

        for (Chunk column : deferredColumnSyncs) {
            column.inhabitedTime += delta;

            ColumnChanges changes = ((MetaContainer) column).getMeta(COLUMN_CHANGES);

            switch (changes.dirty) {
                case None -> {
                    // whar?
                }
                case Partial -> {
                    var packet = PacketEncoderHeightMapUpdate.createPacket(changes.dirtyColumns, column);

                    for (WatchingPlayer player : changes.watchingPlayers) {
                        packet.sendToPlayer(player.player);
                    }
                }
                case Full -> {
                    var packet = PacketEncoderColumn.createPacket(column);

                    for (var player : changes.watchingPlayers) {
                        packet.sendToPlayer(player.player);
                    }
                }
            }

            changes.clean();
        }

        deferredColumnSyncs.clear();
    }

    private void syncCubes() {
        ((ICubicWorldInternal) getWorldServer()).getLightingManager()
            .onSendCubes();

        for (Cube cube : deferredCubeSyncs) {
            CubeChanges changes = cube.getMeta(CUBE_CHANGES);

            switch (changes.dirty) {
                case None -> {
                    // huh?
                }
                case Partial -> {
                    sendPartialSync(cube, changes);
                }
                case Full -> {
                    for (var player : changes.watchingPlayers) {
                        player.queueCube(cube);
                    }
                }
            }

            changes.clean();
        }

        deferredCubeSyncs.clear();

        this.players.values()
            .forEach(WatchingPlayer::flushCubes);
    }

    private void sendPartialSync(Cube cube, CubeChanges changes) {
        // send all the dirty blocks

        short[] dirtyBlocks = changes.dirtyBlocks.toShortArray();
        var cubePacket = PacketEncoderCubeBlockChange.createPacket(cube, dirtyBlocks);

        List<Packet> tiles = new ArrayList<>(0);

        int blockX = cube.getX() << 4;
        int blockY = cube.getY() << 4;
        int blockZ = cube.getZ() << 4;

        for (short localAddress : dirtyBlocks) {
            int x = AddressTools.getLocalX(localAddress);
            int y = AddressTools.getLocalY(localAddress);
            int z = AddressTools.getLocalZ(localAddress);

            TileEntity te = getWorldServer().getTileEntity(blockX + x, blockY + y, blockZ + z);

            if (te == null) continue;

            Packet packet = te.getDescriptionPacket();

            if (packet != null) tiles.add(packet);
        }

        for (WatchingPlayer player : changes.watchingPlayers) {
            cubePacket.sendToPlayer(player.player);

            for (Packet tilePacket : tiles) {
                player.player.playerNetServerHandler.sendPacket(tilePacket);
            }
        }
    }

    @Override
    public void onColumnLoaded(Chunk column) {
        var changes = getChanges(column);

        for (var player : players.values()) {
            boolean visible = CuboidalCubeSelector.INSTANCE.contains(
                CubePos.fromEntity(player.player),
                horizontalViewDistance,
                verticalViewDistance,
                column.xPosition, column.zPosition);

            if (visible) {
                changes.watchingPlayers.add(player);

                PacketEncoderColumn.createPacket(column)
                    .sendToPlayer(player.player);
            }
        }
    }

    @Override
    public void onColumnUnloaded(Chunk column) {
        var changes = ((MetaContainer) column).getMeta(COLUMN_CHANGES);

        PacketUnloadColumn packet = PacketEncoderUnloadColumn.createPacket(column.xPosition, column.zPosition);

        for (WatchingPlayer player : changes.watchingPlayers) {
            packet.sendToPlayer(player.player);
        }
    }

    @Override
    public void onCubeLoaded(Cube cube) {
        onCubeGenerated(cube, cube.getInitLevel());
    }

    @Override
    public void onCubeGenerated(Cube cube, CubeInitLevel newLevel) {
        var changes = getChanges(cube);

        if (newLevel == CubeInitLevel.Lit) {
            for (var player : players.values()) {
                boolean visible = CuboidalCubeSelector.INSTANCE.contains(
                    CubePos.fromEntity(player.player),
                    horizontalViewDistance + 2,
                    verticalViewDistance + 2,
                    cube.getX(),
                    cube.getY(),
                    cube.getZ());

                if (visible) {
                    changes.watchingPlayers.add(player);
                    player.queueCube(cube);
                }
            }

            var request = cubeLoadRequests.remove(cube);

            if (request != null) {
                request.cancel();
            }
        }

        CubeStatusVisualizer.put(
            cube.getCoords(), switch (newLevel) {
                case None -> CubeStatus.None;
                case Generated -> CubeStatus.Generated;
                case Populated -> CubeStatus.Populated;
                case Lit -> CubeStatus.Lit;
            });
    }

    @Override
    public void onCubeUnloaded(Cube cube) {
        var changes = getChanges(cube);

        PacketUnloadCube packet = PacketEncoderUnloadCube.createPacket(cube.getCoords());

        for (var player : changes.watchingPlayers) {
            packet.sendToPlayer(player.player);
        }

        CubeStatusVisualizer.remove(cube.getCoords());
    }

    private CubeChanges getChanges(Cube cube) {
        var changes = cube.getMeta(CUBE_CHANGES);

        if (changes == null) {
            changes = new CubeChanges(cube);
            cube.setMeta(CUBE_CHANGES, changes);
        }

        return changes;
    }

    private ColumnChanges getChanges(Chunk column) {
        var changes = ((MetaContainer) column).getMeta(COLUMN_CHANGES);

        if (changes == null) {
            changes = new ColumnChanges(column);
            ((MetaContainer) column).setMeta(COLUMN_CHANGES, changes);
        }

        return changes;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean func_152621_a(int columnX, int columnZ) {
        return isColumnWatched(columnX, columnZ);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void markBlockForUpdate(int x, int y, int z) {
        Cube cube = provider.getLoadedCube(x >> 4, y >> 4, z >> 4);

        if (cube != null) {
            getChanges(cube).markDirty(x, y, z);
        }
    }

    // Note these arguments are in global block coordinates
    public void heightUpdated(int x, int z) {
        Chunk column = provider.getLoadedColumn(x, z);

        if (column != null) {
            getChanges(column).markDirty(x, z);
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void addPlayer(EntityPlayerMP player) {
        if (player.worldObj != this.getWorldServer()) {
            CubicChunks.bigWarning(
                "Player world not the same as PlayerCubeMap world! Adding anyway. This is very likely to cause issues! Player "
                    + "world dimension ID: %d, PlayerCubeMap dimension ID: %d",
                player.worldObj.provider.dimensionId,
                getWorldServer().provider.dimensionId);
        } else if (!player.worldObj.playerEntities.contains(player)) {
            CubicChunks.LOGGER.debug(
                "PlayerCubeMap (dimension {}): Adding player to pending to add list",
                getWorldServer().provider.dimensionId);
            pendingPlayerAddToCubeMap.add(player);
            return;
        }

        WatchingPlayer watchingPlayer = new WatchingPlayer(player);
        watchingPlayer.updateManagedPos();

        CubePos playerCubePos = CubePos.fromEntity(player);

        CuboidalCubeSelector.INSTANCE.forAllVisibleCubes(
            playerCubePos,
            horizontalViewDistance,
            verticalViewDistance,
            pos -> onPlayerStartedViewingCube(watchingPlayer, pos));

        CuboidalCubeSelector.INSTANCE.forAllVisibleColumns(
            playerCubePos,
            horizontalViewDistance,
            verticalViewDistance,
            pos -> onPlayerStartedViewingColumn(watchingPlayer, pos));

        watchingPlayer.flushCubes();

        this.players.put(player.getEntityId(), watchingPlayer);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void removePlayer(EntityPlayerMP player) {
        WatchingPlayer watchingPlayer = this.players.remove(player.getEntityId());

        if (watchingPlayer == null) {
            return;
        }

        // Minecraft does something evil there: this method is called *after* changing the player's position
        // so we need to use managedPosition there
        CubePos playerCubePos = CubePos.fromEntityCoords(
            player.managedPosX,
            watchingPlayer.managedPosY,
            player.managedPosZ);

        CuboidalCubeSelector.INSTANCE.forAllVisibleCubes(
            playerCubePos,
            horizontalViewDistance,
            verticalViewDistance,
            pos -> onPlayerStoppedViewingCube(watchingPlayer, pos));

        CuboidalCubeSelector.INSTANCE.forAllVisibleColumns(
            playerCubePos,
            horizontalViewDistance,
            verticalViewDistance,
            pos -> onPlayerStoppedViewingColumn(watchingPlayer, pos));
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void updatePlayerPertinentChunks(EntityPlayerMP player) {
        // the player moved
        // if the player moved into a new chunk, update which chunks the player needs to know about
        // then update the list of chunks that need to be sent to the client

        // get the player info
        WatchingPlayer watchingPlayer = this.players.get(player.getEntityId());

        if (watchingPlayer == null) {
            // vanilla sometimes does it, this is normal
            return;
        }

        // did the player move into new cube?
        if (!watchingPlayer.cubePosChanged()) {
            return;
        }

        this.updatePlayer(watchingPlayer, watchingPlayer.getManagedCubePos(), CubePos.fromEntity(player));

        watchingPlayer.updateManagedPos();

        // With ChunkGc being separate from PlayerCubeMap, there are 2 issues:
        // Problem 0: Sometimes, a chunk can be generated after CubeWatcher's chunk load callback returns with a null
        // but before ChunkGC call. This means that the cube will get unloaded, even when ChunkWatcher is waiting for
        // it.
        // Problem 1: When chunkGc call is not in this method, sometimes, when a player teleports far away and is
        // unlucky, and ChunkGc runs in the same tick the teleport appears to happen after PlayerCubeMap call, but
        // before ChunkGc call. This means that PlayerCubeMap won't yet have a CubeWatcher for the player cubes at all,
        // so even directly checking for CubeWatchers before unload attempt won't work.
        //
        // While normally not an issue as it will be reloaded soon anyway, it breaks a lot of things if that cube
        // contains the player. Which is not unlikely if the player is what caused generating this cube in the first
        // place
        // for problem #0.
        // So we put ChunkGc here so that we can be sure it has consistent data about player location, and that no
        // chunks are
        // loaded while we aren't looking.
        ((ICubicWorldInternal.Server) getWorldServer()).getCubeCache()
            .getCubeLoader()
            .doGC();
    }

    private void onPlayerStartedViewingColumn(WatchingPlayer player, ChunkCoordIntPair pos) {
        Chunk column = provider.getLoadedColumn(pos.chunkXPos, pos.chunkZPos);

        if (column != null) {
            var changes = ((MetaContainer) column).getMeta(COLUMN_CHANGES);

            changes.watchingPlayers.add(player);

            PacketEncoderColumn.createPacket(column)
                .sendToPlayer(player.player);
        }
    }

    private void onPlayerStoppedViewingColumn(WatchingPlayer player, ChunkCoordIntPair pos) {
        Chunk column = provider.getLoadedColumn(pos.chunkXPos, pos.chunkZPos);

        if (column != null) {
            var changes = ((MetaContainer) column).getMeta(COLUMN_CHANGES);

            changes.watchingPlayers.remove(player);
        }
    }

    private void onPlayerStartedViewingCube(WatchingPlayer player, CubePos pos) {
        Cube cube = provider.getLoadedCube(pos);

        if (cube != null) {
            var changes = cube.getMeta(CUBE_CHANGES);

            changes.watchingPlayers.add(player);
            player.queueCube(cube);
        } else {
            var request = cubeLoadRequests.get(pos);

            if (request == null || request.isCompleted()) {
                cubeLoadRequests.put(provider.loadCubeEagerly(pos.getX(), pos.getY(), pos.getZ(), Requirement.LIGHT));
            }
        }
    }

    private void onPlayerStoppedViewingCube(WatchingPlayer player, CubePos pos) {
        Cube cube = provider.getLoadedCube(pos);

        if (cube != null) {
            var changes = cube.getMeta(CUBE_CHANGES);

            changes.watchingPlayers.remove(player);

            if (changes.watchingPlayers.isEmpty()) {
                var request = cubeLoadRequests.remove(pos);

                if (request != null) {
                    request.cancel();
                }
            }
        }
    }

    private void updatePlayer(WatchingPlayer player, CubePos oldPos, CubePos newPos) {
        getWorldServer().theProfiler.startSection("updateMovedPlayer");

        getWorldServer().theProfiler.startSection("findChanges");

        var delta = CuboidalCubeSelector.INSTANCE.findChanged(
            oldPos,
            newPos,
            horizontalViewDistance,
            verticalViewDistance,
            horizontalViewDistance,
            verticalViewDistance);

        applyWorldVisibilityChanges(player, delta);

        getWorldServer().theProfiler.endStartSection("Immediate nearby cube loading");

        CubeProviderServer cubeCache = ((Server) getWorldServer()).getCubeCache();

        // Force load the cube the player is in along with its 26 neighbours
        for (Vector3ic v : new Box(-1, -1, -1, 1, 1, 1)) {
            cubeCache.getCube(newPos.getX() + v.x(), newPos.getY() + v.y(), newPos.getZ() + v.z(), Requirement.LIGHT);
        }

        getWorldServer().theProfiler.endSection();// Immediate nearby cube loading
        getWorldServer().theProfiler.endSection();// updateMovedPlayer
    }

    public boolean isColumnWatched(int columnX, int columnZ) {
        Chunk column = provider.getLoadedColumn(columnX, columnZ);

        return isColumnWatched(column);
    }

    public boolean isColumnWatched(@Nullable Chunk column) {
        return column != null && !getChanges(column).watchingPlayers.isEmpty();
    }

    public boolean isCubeWatched(int cubeX, int cubeY, int cubeZ) {
        Cube cube = provider.getLoadedCube(cubeX, cubeY, cubeZ);

        return isCubeWatched(cube);
    }

    public boolean isCubeWatched(@Nullable Cube cube) {
        return cube != null && !getChanges(cube).watchingPlayers.isEmpty();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean isPlayerWatchingChunk(EntityPlayerMP player, int cubeX, int cubeZ) {
        Chunk column = provider.getLoadedColumn(cubeX, cubeZ);

        if (column == null) return false;

        for (WatchingPlayer watchingPlayer : getChanges(column).watchingPlayers) {
            if (watchingPlayer.player == player) return true;
        }

        return false;
    }

    public boolean isPlayerWatchingCube(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
        Cube cube = provider.getLoadedCube(cubeX, cubeY, cubeZ);

        if (cube == null) return false;

        for (WatchingPlayer watchingPlayer : getChanges(cube).watchingPlayers) {
            if (watchingPlayer.player == player) return true;
        }

        return false;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    @Deprecated
    public final void func_152622_a(int newHorizontalViewDistance) {
        this.setPlayerViewDistance(newHorizontalViewDistance, verticalViewDistance);
    }

    public final void setPlayerViewDistance(int newHorizontalViewDistance, int newVerticalViewDistance) {
        // this method is called by vanilla before these fields are initialized.
        // and it doesn't really need to be called because in this case
        // it reduces to setting the viewRadius field
        if (this.players == null) {
            return;
        }

        newHorizontalViewDistance = clamp_int(newHorizontalViewDistance, 3, 32);
        newVerticalViewDistance = clamp_int(newVerticalViewDistance, 3, 32);

        if (newHorizontalViewDistance == this.horizontalViewDistance
            && newVerticalViewDistance == this.verticalViewDistance) {
            return;
        }

        int oldHorizontalViewDistance = this.horizontalViewDistance;
        int oldVerticalViewDistance = this.verticalViewDistance;

        for (WatchingPlayer watchingPlayer : this.players.values()) {

            CubePos playerPos = watchingPlayer.getManagedCubePos();

            var delta = CuboidalCubeSelector.INSTANCE.findChanged(
                playerPos,
                playerPos,
                oldHorizontalViewDistance,
                oldVerticalViewDistance,
                newHorizontalViewDistance,
                newVerticalViewDistance);

            applyWorldVisibilityChanges(watchingPlayer, delta);
        }

        this.horizontalViewDistance = newHorizontalViewDistance;
        this.verticalViewDistance = newVerticalViewDistance;
    }

    private void applyWorldVisibilityChanges(WatchingPlayer player, WorldVisibilityChange delta) {
        delta.columnsToLoad.forEach(col -> onPlayerStartedViewingColumn(player, col));
        delta.cubesToLoad.forEach(pos -> onPlayerStartedViewingCube(player, pos));

        delta.cubesToUnload.forEach(pos -> onPlayerStoppedViewingCube(player, pos));
        delta.columnsToUnload.forEach(col -> onPlayerStoppedViewingColumn(player, col));

        player.flushCubes();
    }

    private static class WatchingPlayer {

        public final EntityPlayerMP player;
        private double managedPosY;
        private final ArrayList<Cube> cubeSendQueue = new ArrayList<>(20);

        WatchingPlayer(EntityPlayerMP player) {
            this.player = player;
        }

        public void queueCube(Cube cube) {
            cubeSendQueue.add(cube);

            if (cubeSendQueue.size() >= 20) {
                flushCubes();
            }
        }

        public void flushCubes() {
            if (!cubeSendQueue.isEmpty()) {
                PacketEncoderCubes.createPacket(cubeSendQueue)
                    .sendToPlayer(player);
                cubeSendQueue.clear();
            }
        }

        void updateManagedPos() {
            this.player.managedPosX = player.posX;
            this.managedPosY = player.posY;
            this.player.managedPosZ = player.posZ;
        }

        int getManagedCubePosX() {
            return blockToCube(this.player.managedPosX);
        }

        int getManagedCubePosY() {
            return blockToCube(this.managedPosY);
        }

        int getManagedCubePosZ() {
            return blockToCube(this.player.managedPosZ);
        }

        CubePos getManagedCubePos() {
            return new CubePos(getManagedCubePosX(), getManagedCubePosY(), getManagedCubePosZ());
        }

        boolean cubePosChanged() {
            // did the player move far enough to matter?
            return blockToCube(player.posX) != this.getManagedCubePosX()
                || blockToCube(player.posY) != this.getManagedCubePosY()
                || blockToCube(player.posZ) != this.getManagedCubePosZ();
        }
    }

    public enum Dirtiness {
        None,
        Partial,
        Full
    }

    private class ColumnChanges {

        public final Chunk column;

        public Dirtiness dirty = Dirtiness.None;

        public final BooleanArray2D dirtyColumns = new BooleanArray2D(16, 16);

        public final ReferenceOpenHashSet<WatchingPlayer> watchingPlayers = new ReferenceOpenHashSet<>(1);

        public ColumnChanges(Chunk column) {
            this.column = column;
        }

        public void markDirty(int blockX, int blockZ) {
            if (this.dirty == Dirtiness.Full) return;

            blockX = Coords.blockToLocal(blockX);
            blockZ = Coords.blockToLocal(blockZ);

            dirtyColumns.set(blockX, blockZ);

            if (this.dirty == Dirtiness.None) {
                this.dirty = Dirtiness.Partial;
            } else if (dirtyColumns.cardinality() >= ForgeModContainer.clumpingThreshold) {
                this.dirty = Dirtiness.Full;
                dirtyColumns.clear();
            }

            CubicPlayerManager.this.deferredColumnSyncs.add(column);
        }

        public void clean() {
            this.dirtyColumns.clear();
            this.dirty = Dirtiness.None;
        }
    }

    private class CubeChanges {

        public final Cube cube;

        public Dirtiness dirty = Dirtiness.None;
        public final ShortArrayList dirtyBlocks = new ShortArrayList(8);

        public final ReferenceOpenHashSet<WatchingPlayer> watchingPlayers = new ReferenceOpenHashSet<>(1);

        public CubeChanges(Cube cube) {
            this.cube = cube;
        }

        public void markDirty(int blockX, int blockY, int blockZ) {
            if (this.dirty == Dirtiness.Full) return;

            blockX = Coords.blockToLocal(blockX);
            blockY = Coords.blockToLocal(blockY);
            blockZ = Coords.blockToLocal(blockZ);

            dirtyBlocks.add((short) AddressTools.getLocalAddress(blockX, blockY, blockZ));

            if (this.dirty == Dirtiness.None) {
                this.dirty = Dirtiness.Partial;
            } else if (dirtyBlocks.size() >= ForgeModContainer.clumpingThreshold) {
                this.dirty = Dirtiness.Full;
                this.dirtyBlocks.clear();
            }

            CubicPlayerManager.this.deferredCubeSyncs.add(cube);

            CubeStatusVisualizer.put(cube.getCoords(), CubeStatus.Dirty);
        }

        public void clean() {
            this.dirtyBlocks.clear();
            this.dirty = Dirtiness.None;
            CubeStatusVisualizer.put(cube.getCoords(), CubeStatus.Synced);
        }
    }
}
