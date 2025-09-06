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
import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;
import static net.minecraft.util.MathHelper.clamp_int;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.world.CubeWatchEvent;
import com.cardinalstar.cubicchunks.entity.ICubicEntityTracker;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.PacketCubes;
import com.cardinalstar.cubicchunks.network.PacketDispatcher;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderCallback;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.WatchersSortingList2D;
import com.cardinalstar.cubicchunks.util.WatchersSortingList3D;
import com.cardinalstar.cubicchunks.visibility.CubeSelector;
import com.cardinalstar.cubicchunks.visibility.CuboidalCubeSelector;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

/**
 * A cubic chunks implementation of Player Manager.
 * <p>
 * This class manages loading and unloading cubes for players.
 */
@ParametersAreNonnullByDefault
public class CubicPlayerManager extends PlayerManager implements CubeLoaderCallback {

    /**
     * Cube selector is used to find which cube positions need to be loaded/unloaded
     * By default use CuboidalCubeSelector.
     */
    private final CubeSelector cubeSelector = new CuboidalCubeSelector();

    /**
     * Mapping if entityId to PlayerCubeMap.PlayerWrapper objects.
     */
    private final TIntObjectMap<PlayerWrapper> players = new TIntObjectHashMap<>();

    /**
     * Mapping of Cube positions to CubeWatchers (Cube equivalent of PlayerManager.PlayerInstance).
     * Contains cube positions of all cubes loaded by players.
     */
    final XYZMap<CubeWatcher> cubeWatchers = new XYZMap<>();

    /**
     * Mapping of Column positions to ColumnWatchers.
     * Contains column positions of all columns loaded by players.
     * Exists for compatibility with vanilla and to send ColumnLoad/Unload packets to clients.
     * Columns cannot be managed by client because they have separate data, like heightmap and biome array.
     */
    final XZMap<ColumnWatcher> columnWatchers = new XZMap<>();

    /**
     * All cubeWatchers that have pending block updates to send.
     */
    private final Set<CubeWatcher> cubeWatchersToUpdate = new HashSet<>();

    /**
     * All columnWatchers that have pending height updates to send.
     */
    private final Set<ColumnWatcher> columnWatchersToUpdate = new HashSet<>();

    /**
     * A queue of cubes to add a player to, this limits the amount of cubes sent to a player per tick to the set limit
     * even when joining an area with already existing cube watchers
     */
    private final WatchersSortingList3D<CubeWatcher> watchersToAddPlayersTo = new WatchersSortingList3D<>(
        0,
        () -> players.valueCollection()
            .stream()
            .map(p -> p.playerEntity)
            .collect(Collectors.toList()));

    /**
     * Contains all CubeWatchers that need to be sent to clients,
     * but these cubes are not fully loaded/generated yet.
     * <p>
     * Note that this is not the same as cubesToGenerate list.
     * Cube can be loaded while not being fully generated yet (not in the last GeneratorStageRegistry stage).
     */
    private final WatchersSortingList3D<CubeWatcher> cubesToSendToClients = new WatchersSortingList3D<>(
        1,
        () -> players.valueCollection()
            .stream()
            .map(p -> p.playerEntity)
            .collect(Collectors.toList()));

    /**
     * Contains all ColumnWatchers that need to be sent to clients,
     * but these cubes are not fully loaded/generated yet.
     * <p>
     * Note that this is not the same as columnsToGenerate list.
     * Columns can be loaded while not being fully generated yet
     */
    private final WatchersSortingList2D<ColumnWatcher> columnsToSendToClients = new WatchersSortingList2D<>(
        3,
        () -> players.valueCollection()
            .stream()
            .map(p -> p.playerEntity)
            .collect(Collectors.toList()));

    private final WatchersSortingList3D<CubeWatcher> tickableCubeTracker = new WatchersSortingList3D<>(
        5,
        () -> players.valueCollection()
            .stream()
            .map(p -> p.playerEntity)
            .collect(Collectors.toList()));

    private int horizontalViewDistance;
    private int verticalViewDistance;

    /**
     * This is used only to force update of all CubeWatchers every 8000 ticks
     */
    private long previousWorldTime = 0;

    private final SetMultimap<EntityPlayerMP, Cube> cubesToSend = MultimapBuilder.hashKeys().hashSetValues().build();

    // these player adds will be processed on the next tick
    // this exists as temporary workaround to player respawn code calling addPlayer() before spawning
    // the player in world as it's spawning player in world that triggers sending cubic chunks world
    // information to client, this causes the server to send columns to the client before the client
    // knows it's a cubic chunks world delaying addPlayer() by one tick fixes it.
    // this should be fixed by hooking into the code in a different place to send the cubic chunks world information
    // (player respawn packet?)
    private Set<EntityPlayerMP> pendingPlayerAddToCubeMap = new HashSet<>();

    private final TickableChunkContainer tickableChunksCubesToReturn = new TickableChunkContainer();

    // final VanillaNetworkHandler vanillaNetworkHandler;

    public CubicPlayerManager(WorldServer worldServer) {
        super(worldServer);
        this.setPlayerViewDistance(
            worldServer.func_73046_m()
                .getConfigurationManager()
                .getViewDistance(),
            ((ICubicPlayerList) worldServer.func_73046_m()
                .getConfigurationManager()).getVerticalViewDistance());
        // this.vanillaNetworkHandler = ((ICubicWorldInternal.Server) worldServer).getVanillaNetworkHandler();
        ((ICubicWorldInternal.Server) worldServer).getCubeCache().registerCallback(this);
    }

    // /**
    // * This method exists only because vanilla needs it. It shouldn't be used anywhere else.
    // */
    // @Override
    // @Deprecated // Warning: Hacks! For vanilla use only! (WorldServer.updateBlocks())
    // public Iterator<Chunk> getChunkIterator() {
    // // CubicChunks.bigWarning("Usage of PlayerCubeMap#getChunkIterator detected in a cubic chunks world! "
    // // + "This is likely to work incorrectly. This is not supported.");
    // // TODO: throw UnsupportedOperationException?
    // Iterator<Chunk> chunkIt = this.cubeCache.getLoadedChunks().iterator();
    // return new AbstractIterator<Chunk>() {
    // @Override protected Chunk computeNext() {
    // while (chunkIt.hasNext()) {
    // IColumn column = (IColumn) chunkIt.next();
    // if (column.shouldTick()) { // shouldTick is true when there Cubes with tickets the request to be ticked
    // return (Chunk) column;
    // }
    // }
    // return this.endOfData();
    // }
    // };
    // }

    public TickableChunkContainer getTickableChunks() {
        TickableChunkContainer tickableChunksCubes = this.tickableChunksCubesToReturn;
        tickableChunksCubes.clear();
        addTickableColumns(tickableChunksCubes);
        addTickableCubes(tickableChunksCubes);
        addForcedColumns(tickableChunksCubes);
        addForcedCubes(tickableChunksCubes);
        return tickableChunksCubes;
    }

    private void addForcedColumns(TickableChunkContainer tickableChunksCubes) {
        for (IColumn columns : ((ICubicWorldInternal.Server) getWorldServer()).getForcedColumns()) {
            tickableChunksCubes.addColumn((Chunk) columns);
        }
    }

    private void addForcedCubes(TickableChunkContainer tickableChunksCubes) {
        tickableChunksCubes.forcedCubes = ((ICubicWorldInternal.Server) getWorldServer()).getForcedCubes();
    }

    private void addTickableCubes(TickableChunkContainer tickableChunksCubes) {
        for (CubeWatcher watcher : (Iterable<CubeWatcher>) () -> tickableCubeTracker.iteratorUpToDistance(9)) {
            ICube cube = watcher.getCube();
            if (cube == null) {
                continue;
            }
            tickableChunksCubes.addCube(cube);
        }
    }

    private void addTickableColumns(TickableChunkContainer tickableChunksCubes) {
        for (ColumnWatcher watcher : columnWatchers) {
            Chunk chunk = watcher.getColumn();
            if (chunk == null) { // TODO WATCH IF YOU NEED TO CHECK RANGE
                continue;
            }
            tickableChunksCubes.addColumn(chunk);
        }
    }

    /**
     * Updates all CubeWatchers and ColumnWatchers.
     * Also sends packets to clients.
     */
    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void updatePlayerInstances() {
        getWorldServer().theProfiler.startSection("playerCubeMapUpdatePlayerInstances");

        long currentTime = this.getWorldServer()
            .getTotalWorldTime();

        getWorldServer().theProfiler.startSection("addPendingPlayers");
        if (!pendingPlayerAddToCubeMap.isEmpty()) {
            // copy in case player still isn't in world
            Set<EntityPlayerMP> players = pendingPlayerAddToCubeMap;
            pendingPlayerAddToCubeMap = new HashSet<>();
            for (EntityPlayerMP player : players) {
                addPlayer(player);
            }
        }
        getWorldServer().theProfiler.endStartSection("tickEntries");
        // force update-all every 8000 ticks (400 seconds)
        if (currentTime - this.previousWorldTime > 8000L) {
            this.previousWorldTime = currentTime;

            for (CubeWatcher playerInstance : this.cubeWatchers) {
                playerInstance.update();
                playerInstance.updateInhabitedTime();
            }
        }

        // process instances to update
        if (!cubeWatchersToUpdate.isEmpty()) {
            this.cubeWatchersToUpdate.forEach(CubeWatcher::update);
            this.cubeWatchersToUpdate.clear();
        }

        if (!columnWatchersToUpdate.isEmpty()) {
            this.columnWatchersToUpdate.forEach(ColumnWatcher::update);
            this.columnWatchersToUpdate.clear();
        }
        getWorldServer().theProfiler.endStartSection("sortTickableTracker");
        tickableCubeTracker.tick();

        getWorldServer().theProfiler.endStartSection("sortToSend");
        this.cubesToSendToClients.tick();
        this.columnsToSendToClients.tick();
        this.watchersToAddPlayersTo.tick();

        getWorldServer().theProfiler.endStartSection("send");

        if (!this.columnsToSendToClients.isEmpty()) {
            getWorldServer().theProfiler.startSection("columns");

            this.columnsToSendToClients.removeIf(ColumnWatcher::sendToPlayers);

            getWorldServer().theProfiler.endSection(); // columns
        }

        if (!this.cubesToSendToClients.isEmpty()) {
            getWorldServer().theProfiler.startSection("cubes");

            Iterator<CubeWatcher> iter = this.cubesToSendToClients.iterator();
            int toSend = CubicChunksConfig.cubesToSendPerTick;

            while (iter.hasNext() && toSend > 0) {
                CubeWatcher cubeWatcher = iter.next();

                if (cubeWatcher.sendToPlayers()) {
                    iter.remove();
                    toSend--;
                }
            }

            getWorldServer().theProfiler.endSection(); // cubes
        }

        if (!watchersToAddPlayersTo.isEmpty()) {
            Iterator<CubeWatcher> iter = watchersToAddPlayersTo.iterator();
            int toSend = CubicChunksConfig.cubesToSendPerTick;

            while (toSend > 0 && iter.hasNext()) {
                CubeWatcher cubeWatcher = iter.next();
                cubeWatcher.addScheduledPlayers();

                if (cubeWatcher.sendToPlayers()) {
                    iter.remove();
                    toSend--;
                }
            }
        }

        getWorldServer().theProfiler.endStartSection("unload");
        // if there are no players - unload everything
        if (this.players.isEmpty()) {
            WorldProvider worldprovider = this.getWorldServer().provider;

            if (!worldprovider.canRespawnHere()) {
                ((ChunkProviderServer) this.getWorldServer()
                    .getChunkProvider()).unloadAllChunks();
            }
        }
        getWorldServer().theProfiler.endStartSection("sendCubes");// unload
        if (!cubesToSend.isEmpty()) {
            for (EntityPlayerMP player : cubesToSend.keySet()) {
                Collection<Cube> cubes = cubesToSend.get(player);
                if (!players.containsKey(player.getEntityId())) {
                    CubicChunks.LOGGER.info(
                        "Skipping sending " + cubes.size()
                            + " chunks to player "
                            + player.getCommandSenderName()
                            + " that is no longer in this world!");
                    continue;
                }
                ((ICubicWorldInternal) getWorldServer()).getLightingManager()
                    .onSendCubes(cubes);
                // if (vanillaNetworkHandler.hasCubicChunks(player)) {
                ArrayList<Cube> list = new ArrayList<>(100);
                for (Cube cube : cubes) {
                    list.add(cube);
                    if (list.size() >= 100) {
                        PacketCubes packet = new PacketCubes(list);
                        PacketDispatcher.sendTo(packet, player);
                        list.clear();
                    }
                }
                if (!list.isEmpty()) {
                    PacketCubes packet = new PacketCubes(list);
                    PacketDispatcher.sendTo(packet, player);
                }
                // } else {
                // vanillaNetworkHandler.sendCubeLoadPackets(cubes, player);
                // }
                // Sending entities per cube.
                for (Cube cube : cubes) {
                    ((ICubicEntityTracker) getWorldServer().getEntityTracker()).sendLeashedEntitiesInCube(player, cube);
                    CubeWatcher watcher = getCubeWatcher(cube.getCoords());
                    assert watcher != null;
                    MinecraftForge.EVENT_BUS.post(new CubeWatchEvent(cube, cube.getCoords(), watcher, player));
                }
            }
            cubesToSend.clear();
        }
        getWorldServer().theProfiler.endSection();// sendCubes
        getWorldServer().theProfiler.endSection();// playerCubeMapUpdatePlayerInstances
    }

    @Override
    public void onColumnLoaded(Chunk column) {
        ColumnWatcher watcher = this.columnWatchers.get(column.xPosition, column.zPosition);

        if (watcher != null) {
            watcher.onColumnLoaded(column);
        }
    }

    @Override
    public void onCubeLoaded(Cube cube) {
        CubeWatcher watcher = this.cubeWatchers.get(cube.getX(), cube.getY(), cube.getZ());

        if (watcher != null) {
            watcher.onCubeLoaded(cube);
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092
    // contains(int cubeX, int cubeZ)
    @Override
    public boolean func_152621_a(int cubeX, int cubeZ) {
        return this.columnWatchers.get(cubeX, cubeZ) != null;
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
    private CubeWatcher getOrCreateCubeWatcher(CubePos cubePos) {
        CubeWatcher cubeWatcher = this.cubeWatchers.get(cubePos.getX(), cubePos.getY(), cubePos.getZ());

        if (cubeWatcher == null) {
            // make a new watcher
            cubeWatcher = new CubeWatcher(this, cubePos);
            this.cubeWatchers.put(cubeWatcher);
            this.tickableCubeTracker.add(cubeWatcher);

            // vanilla has the below check, which causes the cubes to be sent to client too early and sometimes in too
            // big amounts
            // if they are sent too early, client won't have the right player position and renderer positions are wrong
            // which cause some cubes to not be rendered
            // DO NOT make it the same as vanilla until it's confirmed that Mojang fixed MC-120079
            // if (!cubeWatcher.sendToPlayers()) {
            this.cubesToSendToClients.add(cubeWatcher);
            // }
        }
        return cubeWatcher;
    }

    /**
     * Returns existing ColumnWatcher or creates new one if it doesn't exist.
     * Always creates the Column.
     */
    private ColumnWatcher getOrCreateColumnWatcher(ChunkCoordIntPair chunkPos) {
        ColumnWatcher columnWatcher = this.columnWatchers.get(chunkPos.chunkXPos, chunkPos.chunkZPos);

        if (columnWatcher == null) {
            this.columnWatchers.put(columnWatcher = new ColumnWatcher(this, chunkPos));

            if (!columnWatcher.sendToPlayers()) {
                this.columnsToSendToClients.add(columnWatcher);
            }
        }

        return columnWatcher;
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void markBlockForUpdate(int x, int y, int z) {
        CubeWatcher cubeWatcher = this.getCubeWatcher(CubePos.fromBlockCoords(x, y, z));

        if (cubeWatcher != null) {
            int localX = blockToLocal(x);
            int localY = blockToLocal(y);
            int localZ = blockToLocal(z);
            cubeWatcher.blockChanged(localX, localY, localZ);
        }
    }

    public void heightUpdated(int blockX, int blockZ) {
        ColumnWatcher columnWatcher = this.columnWatchers.get(blockToCube(blockX), blockToCube(blockZ));
        if (columnWatcher != null) {
            int localX = blockToLocal(blockX);
            int localZ = blockToLocal(blockZ);
            columnWatcher.heightChanged(localX, localZ);
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

        PlayerWrapper playerWrapper = new PlayerWrapper(player);
        playerWrapper.updateManagedPos();

        // if (!vanillaNetworkHandler.hasCubicChunks(player)) {
        // vanillaNetworkHandler.updatePlayerPosition(this, player, playerWrapper.getManagedCubePos());
        // }

        CubePos playerCubePos = CubePos.fromEntity(player);

        this.cubeSelector
            .forAllVisibleFrom(playerCubePos, horizontalViewDistance, verticalViewDistance, (currentPos) -> {
                // create cubeWatcher and chunkWatcher
                // order is important
                ColumnWatcher chunkWatcher = getOrCreateColumnWatcher(currentPos.chunkPos());
                // and add the player to them
                if (!chunkWatcher.containsPlayer(player)) {
                    chunkWatcher.addPlayer(player);
                }
                CubeWatcher cubeWatcher = getOrCreateCubeWatcher(currentPos);

                scheduleAddPlayerToWatcher(cubeWatcher, player);
            });
        this.players.put(player.getEntityId(), playerWrapper);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void removePlayer(EntityPlayerMP player) {
        PlayerWrapper playerWrapper = this.players.get(player.getEntityId());
        if (playerWrapper == null) {
            return;
        }
        // Minecraft does something evil there: this method is called *after* changing the player's position
        // so we need to use managedPosition there
        CubePos playerCubePos = CubePos
            .fromEntityCoords(player.managedPosX, playerWrapper.managedPosY, player.managedPosZ);

        // send unload columns later so that they get unloaded after their corresponding cubes
        ObjectSet<ColumnWatcher> toSendUnload = new ObjectOpenHashSet<>(
            (horizontalViewDistance * 2 + 1) * (horizontalViewDistance * 2 + 1) * 6);
        this.cubeSelector.forAllVisibleFrom(playerCubePos, horizontalViewDistance, verticalViewDistance, (cubePos) -> {

            // get the watcher
            CubeWatcher watcher = getCubeWatcher(cubePos);
            if (watcher != null) {
                // remove from the watcher, it also removes the watcher if it becomes empty
                removePlayerFromCubeWatcher(watcher, player);
            }

            // remove column watchers if needed
            ColumnWatcher columnWatcher = getColumnWatcher(cubePos.chunkPos());
            if (columnWatcher == null) {
                return;
            }

            toSendUnload.add(columnWatcher);
        });
        toSendUnload.stream()
            .filter(watcher -> watcher.containsPlayer(player))
            .forEach(watcher -> watcher.removePlayer(player));
        this.players.remove(player.getEntityId());
        // vanillaNetworkHandler.removePlayer(player);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void updatePlayerPertinentChunks(EntityPlayerMP player) {
        // the player moved
        // if the player moved into a new chunk, update which chunks the player needs to know about
        // then update the list of chunks that need to be sent to the client

        // get the player info
        PlayerWrapper playerWrapper = this.players.get(player.getEntityId());

        if (playerWrapper == null) {
            // vanilla sometimes does it, this is normal
            return;
        }
        // did the player move into new cube?
        if (!playerWrapper.cubePosChanged()) {
            return;
        }

        this.updatePlayer(playerWrapper, playerWrapper.getManagedCubePos(), CubePos.fromEntity(player));
        playerWrapper.updateManagedPos();

        // if (!vanillaNetworkHandler.hasCubicChunks(player)) {
        // vanillaNetworkHandler.updatePlayerPosition(this, player, playerWrapper.getManagedCubePos());
        // }
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
        ((ICubicWorldInternal.Server) getWorldServer()).getCubeCache().getCubeLoader().doGC();
    }

    private void updatePlayer(PlayerWrapper entry, CubePos oldPos, CubePos newPos) {
        getWorldServer().theProfiler.startSection("updateMovedPlayer");
        Set<CubePos> cubesToRemove = new HashSet<>();
        Set<CubePos> cubesToLoad = new HashSet<>();
        Set<ChunkCoordIntPair> columnsToRemove = new HashSet<>();
        Set<ChunkCoordIntPair> columnsToLoad = new HashSet<>();

        getWorldServer().theProfiler.startSection("findChanges");
        // calculate new visibility
        this.cubeSelector.findChanged(
            oldPos,
            newPos,
            horizontalViewDistance,
            verticalViewDistance,
            cubesToRemove,
            cubesToLoad,
            columnsToRemove,
            columnsToLoad);

        getWorldServer().theProfiler.endStartSection("createColumns");
        // order is important, columns first
        columnsToLoad.forEach(pos -> {
            ColumnWatcher columnWatcher = this.getOrCreateColumnWatcher(pos);
            assert columnWatcher.getPos()
                .equals(pos);
            columnWatcher.addPlayer(entry.playerEntity);
        });
        getWorldServer().theProfiler.endStartSection("createCubes");
        cubesToLoad.forEach(pos -> {
            CubeWatcher cubeWatcher = this.getOrCreateCubeWatcher(pos);
            assert cubeWatcher.getCubePos()
                .equals(pos);
            scheduleAddPlayerToWatcher(cubeWatcher, entry.playerEntity);
        });
        getWorldServer().theProfiler.endStartSection("removeCubes");
        cubesToRemove.forEach(pos -> {
            CubeWatcher cubeWatcher = this.getCubeWatcher(pos);
            if (cubeWatcher != null) {
                assert cubeWatcher.getCubePos()
                    .equals(pos);
                removePlayerFromCubeWatcher(cubeWatcher, entry.playerEntity);
            }
        });
        getWorldServer().theProfiler.endStartSection("removeColumns");
        columnsToRemove.forEach(pos -> {
            ColumnWatcher columnWatcher = this.getColumnWatcher(pos);
            if (columnWatcher != null) {
                assert columnWatcher.getPos()
                    .equals(pos);
                columnWatcher.removePlayer(entry.playerEntity);
            }
        });
        getWorldServer().theProfiler.endStartSection("Immediate nearby cube loading");

        CubeProviderServer cubeCache = ((ICubicWorldInternal.Server) getWorldServer()).getCubeCache();

        // Force load the cube the player is in along with its 26 neighbours
        for (Vector3ic v : new Box(-1, -1, -1, 1, 1, 1)) {
            cubeCache.getCube(newPos.getX() + v.x(), newPos.getY() + v.y(), newPos.getZ() + v.z(), ICubeProviderServer.Requirement.LIGHT);
        }

        getWorldServer().theProfiler.endSection();// Immediate nearby cube loading
        getWorldServer().theProfiler.endSection();// updateMovedPlayer
    }

    private void removePlayerFromCubeWatcher(CubeWatcher cubeWatcher, EntityPlayerMP playerEntity) {
        cubeWatcher.removePlayer(playerEntity);
    }

    private void scheduleAddPlayerToWatcher(CubeWatcher cubeWatcher, EntityPlayerMP playerEntity) {
        watchersToAddPlayersTo.add(cubeWatcher);
        cubeWatcher.scheduleAddPlayer(playerEntity);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean isPlayerWatchingChunk(EntityPlayerMP player, int cubeX, int cubeZ) {
        ColumnWatcher columnWatcher = this.getColumnWatcher(new ChunkCoordIntPair(cubeX, cubeZ));
        return columnWatcher != null && columnWatcher.containsPlayer(player) && columnWatcher.isSentToPlayers;
    }

    public boolean isPlayerWatchingCube(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
        CubeWatcher watcher = this.getCubeWatcher(new CubePos(cubeX, cubeY, cubeZ));
        return watcher != null && watcher.containsPlayer(player) && watcher.isSentToPlayers();
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

        for (PlayerWrapper playerWrapper : this.players.valueCollection()) {

            EntityPlayerMP player = playerWrapper.playerEntity;
            CubePos playerPos = playerWrapper.getManagedCubePos();

            if (newHorizontalViewDistance > oldHorizontalViewDistance
                || newVerticalViewDistance > oldVerticalViewDistance) {
                // if newRadius is bigger, we only need to load new cubes
                this.cubeSelector
                    .forAllVisibleFrom(playerPos, newHorizontalViewDistance, newVerticalViewDistance, pos -> {
                        // order is important
                        ColumnWatcher columnWatcher = this.getOrCreateColumnWatcher(pos.chunkPos());
                        if (!columnWatcher.containsPlayer(player)) {
                            columnWatcher.addPlayer(player);
                        }
                        CubeWatcher cubeWatcher = this.getOrCreateCubeWatcher(pos);
                        if (!cubeWatcher.containsPlayer(player)) {
                            scheduleAddPlayerToWatcher(cubeWatcher, player);
                        }
                    });
                // either both got smaller or only one of them changed
            } else {
                // if it got smaller...
                Set<CubePos> cubesToUnload = new HashSet<>();
                Set<ChunkCoordIntPair> columnsToUnload = new HashSet<>();
                this.cubeSelector.findAllUnloadedOnViewDistanceDecrease(
                    playerPos,
                    oldHorizontalViewDistance,
                    newHorizontalViewDistance,
                    oldVerticalViewDistance,
                    newVerticalViewDistance,
                    cubesToUnload,
                    columnsToUnload);

                cubesToUnload.forEach(pos -> {
                    CubeWatcher cubeWatcher = this.getCubeWatcher(pos);
                    if (cubeWatcher != null) {
                        removePlayerFromCubeWatcher(cubeWatcher, player);
                    } else {
                        CubicChunks.LOGGER.warn("cubeWatcher null on render distance change");
                    }
                });
                columnsToUnload.forEach(pos -> {
                    ColumnWatcher columnWatcher = this.getColumnWatcher(pos);
                    if (columnWatcher != null && columnWatcher.containsPlayer(player)) {
                        columnWatcher.removePlayer(player);
                    } else {
                        CubicChunks.LOGGER.warn("cubeWatcher null or doesn't contain player on render distance change");
                    }
                });
            }
        }

        this.horizontalViewDistance = newHorizontalViewDistance;
        this.verticalViewDistance = newVerticalViewDistance;
    }

    // TODO NEEDED?
    // @Override
    // public void entryChanged(IColumnWatcher entry) {
    // throw new UnsupportedOperationException();
    // }
    //
    // @Override
    // public void removeEntry(IColumnWatcher entry) {
    // throw new UnsupportedOperationException();
    // }

    void addToUpdateEntry(CubeWatcher cubeWatcher) {
        this.cubeWatchersToUpdate.add(cubeWatcher);
    }

    void addToUpdateEntry(ColumnWatcher columnWatcher) {
        this.columnWatchersToUpdate.add(columnWatcher);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    void removeEntry(CubeWatcher cubeWatcher) {
        watchersToAddPlayersTo.remove(cubeWatcher);
        cubeWatcher.invalidate();
        cubeWatcher.updateInhabitedTime();
        this.tickableCubeTracker.remove(cubeWatcher);
        CubeWatcher removed = this.cubeWatchers.remove(cubeWatcher.getX(), cubeWatcher.getY(), cubeWatcher.getZ());
        assert removed == cubeWatcher : "Removed unexpected cube watcher";
        this.cubeWatchersToUpdate.remove(cubeWatcher);
        this.cubesToSendToClients.remove(cubeWatcher);
        if (cubeWatcher.getCube() != null) {
            cubeWatcher.getCube()
                .getTickets()
                .remove(cubeWatcher); // remove the ticket, so this Cube can unload
        }
        // don't unload, ChunkGc unloads chunks
    }

    public void removeEntry(ColumnWatcher entry) {
        ChunkCoordIntPair pos = entry.getPos();
        entry.increaseInhabitedTime();
        this.columnWatchers.remove(pos.chunkXPos, pos.chunkZPos);
        this.columnsToSendToClients.remove(entry);
        this.columnWatchersToUpdate.remove(entry);
    }

    public void scheduleSendCubeToPlayer(Cube cube, EntityPlayerMP player) {
        cubesToSend.put(player, cube);
    }

    public void removeSchedulesSendCubeToPlayer(Cube cube, EntityPlayerMP player) {
        cubesToSend.remove(player, cube);
    }

    @Nullable
    public CubeWatcher getCubeWatcher(CubePos pos) {
        return this.cubeWatchers.get(pos.getX(), pos.getY(), pos.getZ());
    }

    @Nullable
    public ColumnWatcher getColumnWatcher(ChunkCoordIntPair pos) {
        return this.columnWatchers.get(pos.chunkXPos, pos.chunkZPos);
    }

    public boolean contains(CubePos coords) {
        return this.cubeWatchers.get(coords.getX(), coords.getY(), coords.getZ()) != null;
    }

    private static final class PlayerWrapper {

        final EntityPlayerMP playerEntity;
        private double managedPosY;

        PlayerWrapper(EntityPlayerMP player) {
            this.playerEntity = player;
        }

        void updateManagedPos() {
            this.playerEntity.managedPosX = playerEntity.posX;
            this.managedPosY = playerEntity.posY;
            this.playerEntity.managedPosZ = playerEntity.posZ;
        }

        int getManagedCubePosX() {
            return blockToCube(this.playerEntity.managedPosX);
        }

        int getManagedCubePosY() {
            return blockToCube(this.managedPosY);
        }

        int getManagedCubePosZ() {
            return blockToCube(this.playerEntity.managedPosZ);
        }

        CubePos getManagedCubePos() {
            return new CubePos(getManagedCubePosX(), getManagedCubePosY(), getManagedCubePosZ());
        }

        boolean cubePosChanged() {
            // did the player move far enough to matter?
            return blockToCube(playerEntity.posX) != this.getManagedCubePosX()
                || blockToCube(playerEntity.posY) != this.getManagedCubePosY()
                || blockToCube(playerEntity.posZ) != this.getManagedCubePosZ();
        }
    }

    public Iterable<Cube> getWatchedCubes() {
        return () -> new AbstractIterator<Cube>() {
            final Iterator<CubeWatcher> iterator = CubicPlayerManager.this.tickableCubeTracker.iterator();

            boolean shouldSkip(@Nullable Cube cube) {
                if (cube == null) return true;
                if (cube.isEmpty()) return true;
                if (!cube.isPopulated()) return true;
                return false;
            }

            @Override
            protected Cube computeNext() {
                while (iterator.hasNext()) {
                    CubeWatcher watcher = iterator.next();
                    Cube cube = watcher.getCube();
                    if (shouldSkip(cube)) continue;
                    return cube;
                }
                return this.endOfData();
            }
        };
    }

    public static class TickableChunkContainer {

        private final ObjectArrayList<ICube> cubes = ObjectArrayList.wrap(new ICube[64 * 1024]);
        private XYZMap<ICube> forcedCubes;
        private final Set<Chunk> columns = Collections.newSetFromMap(new IdentityHashMap<>());

        private void clear() {
            this.cubes.clear();
            this.columns.clear();
        }

        private void addCube(ICube cube) {
            cubes.add(cube);
        }

        public void addColumn(Chunk column) {
            columns.add(column);
        }

        public Iterable<ICube> forcedCubes() {
            return forcedCubes;
        }

        public ICube[] playerTickableCubes() {
            return cubes.elements();
        }

        public Iterable<Chunk> columns() {
            return columns;
        }
    }
}
