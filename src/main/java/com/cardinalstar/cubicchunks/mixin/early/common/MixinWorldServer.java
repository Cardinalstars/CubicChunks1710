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
package com.cardinalstar.cubicchunks.mixin.early.common;

import static com.cardinalstar.cubicchunks.util.Coords.cubeToMinBlock;
import static com.cardinalstar.cubicchunks.util.ReflectionUtil.cast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.ICubicWorldServer;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.server.SpawnCubes;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.world.CubeSplitTicks;
import com.cardinalstar.cubicchunks.world.CubeSpawnerAnimals;
import com.cardinalstar.cubicchunks.world.ICubicWorldProvider;
import com.cardinalstar.cubicchunks.world.ISpawnerAnimals;
import com.cardinalstar.cubicchunks.world.chunkloader.CubicChunkManager;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Implementation of {@link ICubicWorldServer} interface.
 */
@ParametersAreNonnullByDefault
@Mixin(WorldServer.class)
@Implements(@Interface(iface = ICubicWorldServer.class, prefix = "world$"))
public abstract class MixinWorldServer extends MixinWorld implements ICubicWorldInternal.Server {

    @Shadow
    @Mutable
    @Final
    private PlayerManager thePlayerManager;
    @Shadow
    @Mutable
    @Final
    private SpawnerAnimals animalSpawner;
    @Shadow
    @Mutable
    @Final
    private EntityTracker theEntityTracker;
    @Shadow
    public boolean levelSaving; // TODO DO WE NEED TO NEGATE THIS?

    @Unique
    private Map<Chunk, Set<ICube>> forcedChunksCubes;
    @Unique
    private XYZMap<ICube> forcedCubes;
    @Unique
    private XZMap<IColumn> forcedColumns;
    @Unique
    private SpawnCubes spawnArea;
    @Unique
    private boolean runningCompatibilityGenerator;
    // private VanillaNetworkHandler vanillaNetworkHandler;

    @Shadow
    public abstract boolean addWeatherEffect(Entity entityIn);

    @Shadow
    public abstract PlayerManager getPlayerManager();

    @Shadow
    public ChunkProviderServer theChunkProviderServer;
    @Unique
    private CubeSplitTicks cubeTicks;

    @Override
    public void initCubicWorldServer() {
        this.forcedChunksCubes = new HashMap<>();
        this.forcedCubes = new XYZMap<>();
        this.forcedColumns = new XZMap<>();
        cubeTicks = new CubeSplitTicks();
    }

    @Redirect(
        method = "<init>", // constructor
        at = @At(value = "NEW", target = "net/minecraft/world/SpawnerAnimals"))
    private SpawnerAnimals redirectSpawnerAnimals() {
        SpawnerAnimals animalsSpawner = new SpawnerAnimals();
        ISpawnerAnimals spawner = new CubeSpawnerAnimals();
        ISpawnerAnimals.Handler spawnHandler = cast(animalsSpawner);
        spawnHandler.setEntitySpawner(spawner);
        return animalsSpawner;
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/WorldServer;)Lnet/minecraft/server/management/PlayerManager;"))
    private PlayerManager redirectPlayerManagerInit(WorldServer server) {
        return new CubicPlayerManager(server);
    }

    @Redirect(
        method = "createChunkProvider",
        at = @At(value = "NEW", target = "net/minecraft/world/gen/ChunkProviderServer"))
    private ChunkProviderServer redirectChunkProviderServer(WorldServer world, IChunkLoader chunkLoader,
        IChunkProvider chunkGenerator) {
        // The first time we call this it will just be used to give the AnvilSave handler to
        // the WorldSpecificSaveHandler. After that, it will be repalced by the CubeProviderServer
        if (theChunkProviderServer == null) {
            return new ChunkProviderServer(world, chunkLoader, chunkGenerator);
        }
        return new CubeProviderServer(world, chunkLoader, ((ICubicWorldProvider) world.provider).createCubeGenerator());
    }

    @WrapOperation(
        method = { "scheduleBlockUpdateWithPriority", "func_147446_b" },
        at = @At(value = "INVOKE", target = "Ljava/util/TreeSet;add(Ljava/lang/Object;)Z", remap = false))
    public boolean redirectAdd(TreeSet<NextTickListEntry> instance, Object o, Operation<Boolean> original) {
        cubeTicks.add((NextTickListEntry) o);
        return original.call(instance, o);
    }

    @WrapOperation(
        method = "tickUpdates",
        at = @At(value = "INVOKE", target = "Ljava/util/TreeSet;remove(Ljava/lang/Object;)Z", remap = false))
    public boolean redirectRemove(TreeSet<NextTickListEntry> instance, Object o, Operation<Boolean> original) {
        cubeTicks.remove((NextTickListEntry) o);
        return original.call(instance, o);
    }

    @Override
    public void setSpawnArea(SpawnCubes spawn) {
        this.spawnArea = spawn;
    }

    @Override
    public SpawnCubes getSpawnArea() {
        return spawnArea;
    }

    @Override
    public CubeSplitTicks getScheduledTicks() {
        return cubeTicks;
    }

    @Override
    public void tickCubicWorld() {
        getLightingManager().onTick();
        if (this.spawnArea != null) {
            this.spawnArea.update((World) (Object) this);
        }
    }

    @Override
    public CubeProviderServer getCubeCache() {
        return (CubeProviderServer) this.chunkProvider;
    }

    @Override
    public void removeForcedCube(ICube cube) {
        if (!forcedChunksCubes.get(cube.getColumn())
            .remove(cube)) {
            CubicChunks.LOGGER.error("Trying to remove forced cube " + cube.getCoords() + ", but it's not forced!");
        }
        forcedCubes.remove(cube);
        if (forcedChunksCubes.get(cube.getColumn())
            .isEmpty()) {
            forcedChunksCubes.remove(cube.getColumn());
            forcedColumns.remove(cube.getColumn());
        }
    }

    @Override
    public void addForcedCube(ICube cube) {
        if (!forcedChunksCubes.computeIfAbsent(cube.getColumn(), chunk -> new HashSet<>())
            .add(cube)) {
            CubicChunks.LOGGER.error("Trying to add forced cube " + cube.getCoords() + ", but it's already forced!");
        }
        forcedCubes.put(cube);
        forcedColumns.put(cube.getColumn());
    }

    @Override
    public XYZMap<ICube> getForcedCubes() {
        return forcedCubes;
    }

    @Override
    public XZMap<IColumn> getForcedColumns() {
        return forcedColumns;
    }

    @Override
    public void unloadOldCubes() {
        this.getCubeCache()
            .getCubeLoader()
            .doGC();
    }

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#forceChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}.
     *
     * Can accept tickets from different worlds.
     */
    @Override
    public void forceChunk(ForgeChunkManager.Ticket ticket, CubePos cube) {
        CubicChunkManager.forceChunk(ticket, cube);
    }

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#reorderChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}
     *
     * Can accept tickets from different worlds.
     */
    @Override
    public void reorderChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        CubicChunkManager.reorderChunk(ticket, chunk);
    }

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#unforceChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}
     *
     * Can accept tickets from different worlds.
     */
    @Override
    public void unforceChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        CubicChunkManager.unforceChunk(ticket, chunk);
    }

    @Override
    public CompatGenerationScope doCompatibilityGeneration() {
        runningCompatibilityGenerator = true;
        return () -> runningCompatibilityGenerator = false;
    }

    @Override
    public boolean isCompatGenerationScope() {
        return runningCompatibilityGenerator;
    }

    /**
     * Handles cubic chunks world block updates.
     *
     * @param cbi callback info
     * @author Barteks2x
     */
    @Inject(method = "func_147456_g", at = @At("HEAD"), cancellable = true)
    private void updateBlocksCubicChunks(CallbackInfo cbi) {
        cbi.cancel();
        this.setActivePlayerChunksAndCheckLight();

        boolean raining = this.isRaining();
        boolean thundering = this.isThundering();
        this.theProfiler.startSection("pollingChunks");

        // CubicChunks - iterate over PlayerCubeMap.TickableChunkContainer instead of Chunks, getTickableChunks already
        // includes forced chunks
        CubicPlayerManager.TickableChunkContainer chunks = ((CubicPlayerManager) this.thePlayerManager)
            .getTickableChunks();
        for (Chunk chunk : chunks.columns()) {
            tickColumn(raining, thundering, chunk);
        }
        this.theProfiler.endStartSection("pollingCubes");

        long worldTime = worldInfo.getWorldTotalTime();
        // CubicChunks - iterate over cubes instead of storage array from Chunk
        for (ICube cube : chunks.forcedCubes()) {
            tickCube(cube, worldTime);
        }
        for (ICube cube : chunks.playerTickableCubes()) {
            if (cube == null) { // this is the internal array from the arraylist, anything beyond the size is null
                break;
            }
            tickCube(cube, worldTime);
        }

        this.theProfiler.endSection();
    }

    private void tickCube(ICube cube, long worldTime) {
        if (!((Cube) cube).checkAndUpdateTick(worldTime)) {
            return;
        }
        int chunkBlockX = cubeToMinBlock(cube.getX());
        int chunkBlockZ = cubeToMinBlock(cube.getZ());

        this.theProfiler.startSection("tickBlocks");
        ExtendedBlockStorage ebs = cube.getStorage();
        if (ebs != null && ebs.getNeedsRandomTick()) {
            for (int i = 0; i < 3; ++i) {
                tickNextBlock(chunkBlockX, chunkBlockZ, ebs);
            }
        }
        this.theProfiler.endSection();
    }

    private void tickNextBlock(int chunkBlockX, int chunkBlockZ, ExtendedBlockStorage ebs) {
        this.updateLCG = this.updateLCG * 3 + 1013904223;
        int rand = this.updateLCG >> 2;
        int localX = rand & 15;
        int localZ = rand >> 8 & 15;
        int localY = rand >> 16 & 15;
        Block block = ebs.getBlockByExtId(localX, localY, localZ);
        this.theProfiler.startSection("randomTick");

        if (block.getTickRandomly()) {
            block.updateTick(
                (World) (Object) this,
                localX + chunkBlockX,
                localY + ebs.getYLocation(),
                localZ + chunkBlockZ,
                this.rand);
        }

        this.theProfiler.endSection();
    }

    private void tickColumn(boolean raining, boolean thundering, Chunk chunk) {
        int chunkBlockX = chunk.xPosition * 16;
        int chunkBlockZ = chunk.zPosition * 16;
        this.theProfiler.startSection("checkNextLight");
        chunk.enqueueRelightChecks();
        this.theProfiler.endStartSection("tickChunk");
        chunk.func_150804_b(false);
        this.theProfiler.endStartSection("thunder");

        int i1;
        int x;
        int z;
        int y;

        if (provider.canDoLightning(chunk) && this.rand.nextInt(100000) == 0
            && this.isRaining()
            && this.isThundering()) {
            this.updateLCG = this.updateLCG * 3 + 1013904223;
            i1 = this.updateLCG >> 2;
            x = chunkBlockX + (i1 & 15);
            z = chunkBlockZ + (i1 >> 8 & 15);
            y = this.getPrecipitationHeight(x, z);

            if (this.canLightningStrikeAt(x, y, z)) {
                this.addWeatherEffect(new EntityLightningBolt((World) (Object) this, x, y, z));
            }
        }

        this.theProfiler.endStartSection("iceandsnow");

        if (provider.canDoRainSnowIce(chunk) && this.rand.nextInt(16) == 0) {
            this.updateLCG = this.updateLCG * 3 + 1013904223;
            i1 = this.updateLCG >> 2;
            x = i1 & 15;
            z = i1 >> 8 & 15;
            y = this.getPrecipitationHeight(x + chunkBlockX, z + chunkBlockZ);

            if (this.isBlockFreezableNaturally(x + chunkBlockX, y - 1, z + chunkBlockZ)) {
                this.setBlock(x + chunkBlockX, y - 1, z + chunkBlockZ, Blocks.ice);
            }

            if (this.isRaining() && this.func_147478_e(x + chunkBlockX, y, z + chunkBlockZ, true)) {
                this.setBlock(x + chunkBlockX, y, z + chunkBlockZ, Blocks.snow_layer);
            }

            if (this.isRaining()) {
                BiomeGenBase biomegenbase = this.getBiomeGenForCoords(x + chunkBlockX, z + chunkBlockZ);

                if (biomegenbase.canSpawnLightningBolt()) {
                    this.getBlock(x + chunkBlockX, y - 1, z + chunkBlockZ)
                        .fillWithRain((World) (Object) this, x + chunkBlockX, y - 1, z + chunkBlockZ);
                }
            }
        }
        this.theProfiler.endSection();
    }
}
