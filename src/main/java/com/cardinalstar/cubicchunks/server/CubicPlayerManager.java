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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
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
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.visibility.CuboidalCubeSelector;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

/**
 * A cubic chunks implementation of Player Manager.
 * <p>
 * This class manages loading and unloading cubes for players.
 */
@ParametersAreNonnullByDefault
public class CubicPlayerManager extends PlayerManager implements CubeLoaderCallback {

    /**
     * Mapping if entityId to PlayerCubeMap.PlayerWrapper objects.
     */
    private final Int2ObjectOpenHashMap<WatchingPlayer> players = new Int2ObjectOpenHashMap<>();

    private final CubeProviderServer provider;

    private final XYZMap<WatchedCube> watchedCubes = new XYZMap<>();
    private final Set<WatchedCube> dirtyCubes = new ObjectOpenHashSet<>();

    private final XZMap<WatchedColumn> watchedColumns = new XZMap<>();
    private final Set<WatchedColumn> dirtyColumns = new ObjectOpenHashSet<>();

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

    public Iterable<Chunk> getColumnsToTick() {
        // TODO: this
        return Collections.emptyList();
    }

    public Iterable<Cube> getCubesToTick() {
        // TODO: this
        return Collections.emptyList();
    }

    public Iterable<Chunk> getWatchedColumns() {
        return () -> new AbstractIterator<>() {

            final Iterator<WatchedColumn> iter = watchedColumns.iterator();

            @Override
            protected Chunk computeNext() {
                while (iter.hasNext()) {
                    WatchedColumn column = iter.next();

                    if (column.column == null || column.watchingPlayers.isEmpty()) continue;

                    return column.column;
                }

                return this.endOfData();
            }
        };
    }

    public Collection<Cube> getCubes() {
        return new AbstractCollection<>() {

            @Override
            public @NotNull Iterator<Cube> iterator() {
                return new AbstractIterator<>() {

                    final Iterator<WatchedCube> iter = watchedCubes.iterator();

                    @Override
                    protected Cube computeNext() {
                        while (iter.hasNext()) {
                            WatchedCube cube = iter.next();

                            if (cube.cube == null) continue;

                            return cube.cube;
                        }

                        return this.endOfData();
                    }
                };
            }

            @Override
            public int size() {
                return watchedCubes.getSize();
            }
        };
    }

    /**
     * Updates all CubeWatchers and ColumnWatchers.
     * Also sends packets to clients.
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

        for (WatchedColumn column : dirtyColumns) {
            if (column.column == null) continue;

            column.column.inhabitedTime += delta;

            switch (column.dirty) {
                case None -> {
                    // whar?
                }
                case Partial -> {
                    column.syncPartial();
                }
                case Full -> {
                    column.syncFull();
                }
            }

            column.clean();
        }

        dirtyColumns.clear();
    }

    private void syncCubes() {
        ((ICubicWorldInternal) getWorldServer()).getLightingManager().onSendCubes(() -> Iterators.transform(dirtyCubes.iterator(), c -> c.cube));

        List<WatchedCube> fullSync = new ObjectArrayList<>();

        for (WatchedCube cube : dirtyCubes) {
            if (cube.cube == null) continue;

            switch (cube.dirty) {
                case None -> {
                    // huh?
                }
                case Partial -> {
                    cube.syncPartial();
                    cube.clean();
                }
                case Full -> {
                    fullSync.add(cube);
                }
            }

            cube.dirty = Dirtiness.None;
        }

        dirtyCubes.clear();

        for (WatchedCube cube : fullSync) {
            for (WatchingPlayer player : cube.watchingPlayers) {
                player.queueCube(cube.cube);
            }

            cube.clean();
        }

        for (WatchingPlayer player : this.players.values()) {
            player.flushCubes();
        }
    }

    @Override
    public void onColumnLoaded(Chunk column) {
        WatchedColumn watcher = this.watchedColumns.get(column.xPosition, column.zPosition);

        if (watcher != null) {
            watcher.setColumn(column);
        }
    }

    @Override
    public void onColumnUnloaded(Chunk column) {
        WatchedColumn watcher = this.watchedColumns.remove(column.xPosition, column.zPosition);

        if (watcher != null) {
            PacketUnloadColumn packet = PacketEncoderUnloadColumn.createPacket(column.xPosition, column.zPosition);

            for (WatchingPlayer player : watcher.watchingPlayers) {
                packet.sendToPlayer(player.player);
            }
        }
    }

    @Override
    public void onCubeLoaded(Cube cube) {
        WatchedCube watcher = this.getOrCreateCubeWatcher(cube.getCoords());

        watcher.setCube(cube);

        CubeStatusVisualizer.put(cube.getCoords(), switch (cube.getInitLevel()) {
            case None -> CubeStatus.None;
            case Generated -> CubeStatus.Generated;
            case Populated -> CubeStatus.Populated;
            case Lit -> CubeStatus.Lit;
        });
    }

    @Override
    public void onCubeGenerated(Cube cube, CubeInitLevel newLevel) {
        WatchedCube watcher = this.getOrCreateCubeWatcher(cube.getCoords());

        watcher.setCube(cube);

        CubeStatusVisualizer.put(cube.getCoords(), switch (cube.getInitLevel()) {
            case None -> CubeStatus.None;
            case Generated -> CubeStatus.Generated;
            case Populated -> CubeStatus.Populated;
            case Lit -> CubeStatus.Lit;
        });
    }

    @Override
    public void onCubeUnloaded(Cube cube) {
        WatchedCube watcher = this.watchedCubes.remove(cube);

        if (watcher != null && watcher.cube != null) {
            PacketUnloadCube packet = PacketEncoderUnloadCube.createPacket(cube.getCoords());

            for (WatchingPlayer player : watcher.watchingPlayers) {
                packet.sendToPlayer(player.player);
            }
        }

        CubeStatusVisualizer.remove(cube.getCoords());
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean func_152621_a(int columnX, int columnZ) {
        return isColumnWatched(columnX, columnZ);
    }

    // TODO WATCH
    // // CHECKED: 1.10.2-12.18.1.2092
    // @Override
    // public ColumnWatcher getEntry(int cubeX, int cubeZ) {
    // return this.columnWatchers.get(cubeX, cubeZ);
    // }

    /**
     * Returns existing CubeWatcher or creates new one if it doesn't exist.
     * Attempts to load the cube and send it to client.
     * If it can't load it or send it to client - adds it to cubesToGenerate/cubesToSendToClients
     */
    private WatchedCube getOrCreateCubeWatcher(CubePos cubePos) {
        WatchedCube cubeWatcher = this.watchedCubes.get(cubePos.getX(), cubePos.getY(), cubePos.getZ());

        if (cubeWatcher == null) {
            this.watchedCubes.put(cubeWatcher = new WatchedCube(cubePos.getX(), cubePos.getY(), cubePos.getZ()));
        }

        return cubeWatcher;
    }

    /**
     * Returns existing ColumnWatcher or creates new one if it doesn't exist.
     * Always creates the Column.
     */
    private WatchedColumn getOrCreateWatchedColumn(ChunkCoordIntPair chunkPos) {
        WatchedColumn watchedColumn = this.watchedColumns.get(chunkPos.chunkXPos, chunkPos.chunkZPos);

        if (watchedColumn == null) {
            this.watchedColumns.put(watchedColumn = new WatchedColumn(chunkPos.chunkXPos, chunkPos.chunkZPos));
        }

        return watchedColumn;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void markBlockForUpdate(int x, int y, int z) {
        WatchedCube cube = watchedCubes.get(x >> 4, y >> 4, z >> 4);

        if (cube != null) {
            cube.markDirty(x, y, z);
        }
    }

    public void heightUpdated(int x, int z) {
        WatchedColumn column = watchedColumns.get(x >> 4, z >> 4);

        if (column != null) {
            column.markDirty(x, z);
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

        CuboidalCubeSelector.INSTANCE.forAllVisibleFrom(playerCubePos, horizontalViewDistance, verticalViewDistance, (currentPos) -> {
            // create cubeWatcher and chunkWatcher
            // order is important

            getOrCreateWatchedColumn(currentPos.chunkPos()).addPlayer(watchingPlayer);
            getOrCreateCubeWatcher(currentPos).addPlayer(watchingPlayer);
        });

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
        CubePos playerCubePos = CubePos
            .fromEntityCoords(player.managedPosX, watchingPlayer.managedPosY, player.managedPosZ);

        // send unload columns later so that they get unloaded after their corresponding cubes
        ObjectSet<WatchedColumn> unloadedColumns = new ObjectOpenHashSet<>(
            (horizontalViewDistance * 2 + 1) * (horizontalViewDistance * 2 + 1));

        CuboidalCubeSelector.INSTANCE.forAllVisibleFrom(playerCubePos, horizontalViewDistance, verticalViewDistance, (cubePos) -> {
            // get the watcher
            WatchedCube cube = watchedCubes.get(cubePos);

            if (cube != null) {
                // Cube will be GC'd if it isn't watched by a player
                cube.removePlayer(watchingPlayer);
            }

            // remove column watchers if needed
            WatchedColumn column = watchedColumns.get(cubePos.getX(), cubePos.getZ());

            if (column != null) {
                // Column will be GC'd if it isn't watched by a player
                unloadedColumns.add(column);
            }
        });

        for (WatchedColumn watcher : unloadedColumns) {
            watcher.removePlayer(watchingPlayer);
        }
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

    private void updatePlayer(WatchingPlayer player, CubePos oldPos, CubePos newPos) {
        getWorldServer().theProfiler.startSection("updateMovedPlayer");

        Set<CubePos> cubesToRemove = new HashSet<>();
        Set<CubePos> cubesToLoad = new HashSet<>();
        Set<ChunkCoordIntPair> columnsToRemove = new HashSet<>();
        Set<ChunkCoordIntPair> columnsToLoad = new HashSet<>();

        getWorldServer().theProfiler.startSection("findChanges");

        // calculate new visibility
        CuboidalCubeSelector.INSTANCE.findChanged(
            oldPos,
            newPos,
            horizontalViewDistance,
            verticalViewDistance,
            cubesToRemove,
            cubesToLoad,
            columnsToRemove,
            columnsToLoad);

        // order is important, columns first

        getWorldServer().theProfiler.endStartSection("createColumns");
        columnsToLoad.forEach(pos -> {
            this.getOrCreateWatchedColumn(pos).addPlayer(player);
        });

        getWorldServer().theProfiler.endStartSection("createCubes");
        cubesToLoad.forEach(pos -> {
            this.getOrCreateCubeWatcher(pos).addPlayer(player);
        });

        getWorldServer().theProfiler.endStartSection("removeCubes");
        cubesToRemove.forEach(pos -> {
            WatchedCube cube = watchedCubes.get(pos);

            if (cube != null) {
                cube.removePlayer(player);
            }
        });

        getWorldServer().theProfiler.endStartSection("removeColumns");
        columnsToRemove.forEach(pos -> {
            WatchedColumn column = watchedColumns.get(pos.chunkXPos, pos.chunkZPos);

            if (column != null) {
                column.removePlayer(player);
            }
        });

        getWorldServer().theProfiler.endStartSection("Immediate nearby cube loading");

        CubeProviderServer cubeCache = ((Server) getWorldServer()).getCubeCache();

        // Force load the cube the player is in along with its 26 neighbours
        for (Vector3ic v : new Box(-1, -1, -1, 1, 1, 1)) {
            cubeCache.getCube(
                newPos.getX() + v.x(),
                newPos.getY() + v.y(),
                newPos.getZ() + v.z(),
                Requirement.LIGHT);
        }

        getWorldServer().theProfiler.endSection();// Immediate nearby cube loading
        getWorldServer().theProfiler.endSection();// updateMovedPlayer
    }

    public boolean isColumnWatched(int columnX, int columnZ) {
        WatchedColumn column = watchedColumns.get(columnX, columnZ);

        return column != null && !column.watchingPlayers.isEmpty();
    }

    public boolean isCubeWatched(int cubeX, int cubeY, int cubeZ) {
        WatchedCube cube = watchedCubes.get(cubeX, cubeY, cubeZ);

        return cube != null && !cube.watchingPlayers.isEmpty();
    }

    public boolean isCubeWatchedAndPresent(int cubeX, int cubeY, int cubeZ) {
        WatchedCube cube = watchedCubes.get(cubeX, cubeY, cubeZ);

        return cube != null && cube.cube != null && !cube.watchingPlayers.isEmpty();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean isPlayerWatchingChunk(EntityPlayerMP player, int cubeX, int cubeZ) {
        WatchedColumn column = watchedColumns.get(cubeX, cubeZ);

        if (column == null || column.column == null) return false;

        for (WatchingPlayer watchingPlayer : column.watchingPlayers) {
            if (watchingPlayer.player == player) return true;
        }

        return false;
    }

    public boolean isPlayerWatchingCube(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
        WatchedCube cube = watchedCubes.get(cubeX, cubeY, cubeZ);

        if (cube == null || cube.cube == null) return false;

        for (WatchingPlayer watchingPlayer : cube.watchingPlayers) {
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

        // Somehow the view distances went in opposite directions
        if ((newHorizontalViewDistance < oldHorizontalViewDistance && newVerticalViewDistance > oldVerticalViewDistance)
            || (newHorizontalViewDistance > oldHorizontalViewDistance
                && newVerticalViewDistance < oldVerticalViewDistance)) {
            // Adjust the values separately to avoid imploding
            setPlayerViewDistance(newHorizontalViewDistance, oldVerticalViewDistance);
            setPlayerViewDistance(newHorizontalViewDistance, newVerticalViewDistance);
            return;
        }

        for (WatchingPlayer watchingPlayer : this.players.values()) {

            EntityPlayerMP player = watchingPlayer.player;
            CubePos playerPos = watchingPlayer.getManagedCubePos();

            if (newHorizontalViewDistance > oldHorizontalViewDistance
                || newVerticalViewDistance > oldVerticalViewDistance) {
                // if newRadius is bigger, we only need to load new cubes
                CuboidalCubeSelector.INSTANCE.forAllVisibleFrom(playerPos, newHorizontalViewDistance, newVerticalViewDistance, pos -> {
                    getOrCreateWatchedColumn(pos.chunkPos()).addPlayer(watchingPlayer);
                    getOrCreateCubeWatcher(pos).addPlayer(watchingPlayer);
                });
            } else {
                // either both got smaller or only one of them changed
                Set<CubePos> cubesToUnload = new HashSet<>();
                Set<ChunkCoordIntPair> columnsToUnload = new HashSet<>();
                CuboidalCubeSelector.INSTANCE.findAllUnloadedOnViewDistanceDecrease(
                    playerPos,
                    oldHorizontalViewDistance,
                    newHorizontalViewDistance,
                    oldVerticalViewDistance,
                    newVerticalViewDistance,
                    cubesToUnload,
                    columnsToUnload);

                cubesToUnload.forEach(pos -> {
                    WatchedCube cube = watchedCubes.get(pos);

                    if (cube != null) cube.removePlayer(watchingPlayer);
                });
                columnsToUnload.forEach(pos -> {
                    WatchedColumn column = watchedColumns.get(pos.chunkXPos, pos.chunkZPos);

                    if (column != null) column.removePlayer(watchingPlayer);
                });
            }
        }

        this.horizontalViewDistance = newHorizontalViewDistance;
        this.verticalViewDistance = newVerticalViewDistance;
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
                PacketEncoderCubes.createPacket(cubeSendQueue).sendToPlayer(player);
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

    enum Dirtiness {
        None,
        Partial,
        Full
    }

    private class WatchedColumn extends ChunkCoordIntPair implements XZAddressable {
        public Chunk column;
        public final BooleanArray2D dirtyColumns = new BooleanArray2D(16, 16);
        public final ReferenceOpenHashSet<WatchingPlayer> watchingPlayers = new ReferenceOpenHashSet<>(4);
        public long lastInhabitedTime;

        public Dirtiness dirty = Dirtiness.None;

        public WatchedColumn(int x, int z) {
            super(x, z);

            setColumn(provider.getLoadedColumn(x, z));
        }

        public void setColumn(@Nullable Chunk column) {
            if (column != null) {
                this.column = column;
                this.lastInhabitedTime = column.inhabitedTime;

                requestFullSync();
            }
        }

        public void addPlayer(WatchingPlayer player) {
            if (watchingPlayers.contains(player)) return;

            watchingPlayers.add(player);

            if (column != null) {
                PacketEncoderColumn.createPacket(column).sendToPlayer(player.player);
            }
        }

        public void removePlayer(WatchingPlayer player) {
            if (!watchingPlayers.contains(player)) return;

            watchingPlayers.remove(player);

            if (column != null) {
                PacketEncoderUnloadColumn.createPacket(chunkXPos, chunkZPos).sendToPlayer(player.player);
            }
        }

        public void markDirty(int blockX, int blockZ) {
            if (this.dirty == Dirtiness.Full) return;

            blockX = Coords.blockToLocal(blockX);
            blockZ = Coords.blockToLocal(blockZ);

            dirtyColumns.set(blockX, blockZ);

            if (this.dirty == Dirtiness.None) {
                this.dirty = Dirtiness.Partial;
                CubicPlayerManager.this.dirtyColumns.add(this);
            } else if (dirtyColumns.cardinality() >= ForgeModContainer.clumpingThreshold) {
                requestFullSync();
            }
        }

        public void requestFullSync() {
            this.dirty = Dirtiness.Full;
            dirtyColumns.clear();
            CubicPlayerManager.this.dirtyColumns.add(this);
        }

        public void clean() {
            this.dirtyColumns.clear();
            this.dirty = Dirtiness.None;
        }

        private void syncFull() {
            var packet = PacketEncoderColumn.createPacket(column);

            for (WatchingPlayer player : this.watchingPlayers) {
                packet.sendToPlayer(player.player);
            }
        }

        private void syncPartial() {
            var packet = PacketEncoderHeightMapUpdate.createPacket(dirtyColumns, column);

            for (WatchingPlayer player : this.watchingPlayers) {
                packet.sendToPlayer(player.player);
            }
        }

        @Override
        public int getX() {
            return chunkXPos;
        }

        @Override
        public int getZ() {
            return chunkZPos;
        }
    }

    private class WatchedCube extends CubePos {
        public EagerCubeLoadRequest request;
        public Cube cube;
        public final ShortArrayList dirtyBlocks = new ShortArrayList(8);
        public final ArrayList<WatchingPlayer> watchingPlayers = new ArrayList<>(1);

        private Dirtiness dirty = Dirtiness.None;

        public WatchedCube(int cubeX, int cubeY, int cubeZ) {
            super(cubeX, cubeY, cubeZ);

            setCube(provider.getLoadedCube(cubeX, cubeY, cubeZ));
        }

        public void setCube(@Nullable Cube cube) {
            if (cube != null && cube.getInitLevel() == CubeInitLevel.Lit) {
                this.cube = cube;

                requestFullSync();

                if (this.request != null) {
                    if (!this.request.isCompleted()) this.request.cancel();
                    this.request = null;
                }
            }
        }

        public void markDirty(int blockX, int blockY, int blockZ) {
            if (this.dirty == Dirtiness.Full) return;

            blockX = Coords.blockToLocal(blockX);
            blockY = Coords.blockToLocal(blockY);
            blockZ = Coords.blockToLocal(blockZ);

            dirtyBlocks.add((short) AddressTools.getLocalAddress(blockX, blockY, blockZ));

            if (this.dirty == Dirtiness.None) {
                this.dirty = Dirtiness.Partial;
                CubicPlayerManager.this.dirtyCubes.add(this);
            } else if (dirtyBlocks.size() >= ForgeModContainer.clumpingThreshold) {
                requestFullSync();
            }

            CubeStatusVisualizer.put(new CubePos(this), CubeStatus.Dirty);
        }

        public void requestFullSync() {
            this.dirty = Dirtiness.Full;
            dirtyBlocks.clear();
            CubicPlayerManager.this.dirtyCubes.add(this);
        }

        public void addPlayer(WatchingPlayer player) {
            if (watchingPlayers.contains(player)) return;

            watchingPlayers.add(player);

            if (cube != null) {
                player.queueCube(cube);
            } else {
                if (request == null || request.isCompleted()) {
                    request = provider.loadCubeEagerly(getX(), getY(), getZ(), Requirement.LIGHT);
                }
            }
        }

        public void removePlayer(WatchingPlayer player) {
            if (!watchingPlayers.contains(player)) return;

            watchingPlayers.remove(player);

            if (cube != null) {
                PacketEncoderUnloadCube.createPacket(cube.getCoords()).sendToPlayer(player.player);
            }

            if (watchingPlayers.isEmpty() && this.request != null) this.request.cancel();
        }

        public void clean() {
            this.dirtyBlocks.clear();
            this.dirty = Dirtiness.None;
        }

        private void syncPartial() {
            // send all the dirty blocks
            short[] dirtyBlocks = this.dirtyBlocks.toShortArray();
            var cubePacket = PacketEncoderCubeBlockChange.createPacket(this.cube, dirtyBlocks);

            List<Packet> tiles = new ArrayList<>(0);

            int blockX = this.cube.getX() << 4;
            int blockY = this.cube.getY() << 4;
            int blockZ = this.cube.getZ() << 4;

            for (int i = 0, localAddressesLength = dirtyBlocks.length; i < localAddressesLength; i++) {
                short localAddress = dirtyBlocks[i];

                int x = AddressTools.getLocalX(localAddress);
                int y = AddressTools.getLocalY(localAddress);
                int z = AddressTools.getLocalZ(localAddress);

                TileEntity te = getWorldServer().getTileEntity(blockX + x, blockY + y, blockZ + z);

                if (te == null) continue;

                Packet packet = te.getDescriptionPacket();

                if (packet != null) tiles.add(packet);
            }

            for (WatchingPlayer player : this.watchingPlayers) {
                cubePacket.sendToPlayer(player.player);

                for (Packet tilePacket : tiles) {
                    player.player.playerNetServerHandler.sendPacket(tilePacket);
                }
            }
        }
    }
}
