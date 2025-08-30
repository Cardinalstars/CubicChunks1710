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
package com.cardinalstar.cubicchunks.event.handlers;

import java.io.IOException;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldSettings;
import com.cardinalstar.cubicchunks.network.PacketCubicWorldData;
import com.cardinalstar.cubicchunks.network.PacketDispatcher;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;
import com.cardinalstar.cubicchunks.util.ReflectionUtil;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.ICubicWorldProvider;
import com.cardinalstar.cubicchunks.world.WorldSavedCubicChunksData;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.google.common.collect.ImmutableList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

@ParametersAreNonnullByDefault
public class CommonEventHandler {

    @SubscribeEvent // this event is fired early enough to replace world with cubic chunks without any issues
    public void onCreateSpawnPoint(WorldEvent.CreateSpawnPosition event) {
        onCreateSpawnPoint2(event.world);
    }
    public void onCreateSpawnPoint2(World world) {
        if (world.isRemote || !(world instanceof WorldServer)) {
            return; // we will send packet to the client when it joins, client shouldn't change world types as it wants
        }

        WorldSavedCubicChunksData savedData = (WorldSavedCubicChunksData) world.perWorldStorage
            .loadData(WorldSavedCubicChunksData.class, "cubicChunksData");
        boolean ccWorldType = world.getWorldInfo()
            .getTerrainType() instanceof ICubicWorldType;
        boolean ccGenerator = ccWorldType && ((ICubicWorldType) world.getWorldInfo()
            .getTerrainType()).hasCubicGeneratorForWorld(world);
        boolean savedCC = savedData != null && savedData.isCubicChunks;
        boolean ccWorldInfo = ((ICubicWorldSettings) world.getWorldInfo()).isCubic()
            && (savedData == null || savedData.isCubicChunks);
        boolean excludeCC = CubicChunksConfig.isDimensionExcluded(world.provider.dimensionId);
        boolean forceExclusions = CubicChunksConfig.forceDimensionExcludes;
        // TODO: simplify this mess of booleans and document where each of them comes from
        // these espressions are generated using Quine McCluskey algorithm
        // using the JQM v1.2.0 (Java QuineMcCluskey) program:
        // IS_CC := CC_GEN OR CC_TYPE AND NOT(EXCLUDED) OR SAVED_CC AND NOT(EXCLUDED) OR SAVED_CC AND NOT(F_EX) OR
        // CC_NEW AND NOT(EXCLUDED);
        // ERROR := CC_GEN AND NOT(CC_TYPE);
        boolean impossible = ccGenerator && !ccWorldType;
        if (impossible) {
            throw new Error("Trying to use cubic chunks generator without cubic chunks world type.");
        }
        boolean isCC = ccGenerator || (ccWorldType && !excludeCC)
            || (savedCC && !excludeCC)
            || (savedCC && !forceExclusions)
            || (ccWorldInfo && !excludeCC);
        if ((CubicChunksConfig.forceLoadCubicChunks == CubicChunksConfig.ForceCCMode.LOAD_NOT_EXCLUDED && !excludeCC)
            || CubicChunksConfig.forceLoadCubicChunks == CubicChunksConfig.ForceCCMode.ALWAYS) {
            isCC = true;
        }

        if (savedData == null) {
            int minY = CubicChunksConfig.defaultMinHeight;
            int maxY = CubicChunksConfig.defaultMaxHeight;
            if (world.provider.dimensionId != 0) {
                WorldSavedCubicChunksData overworld = (WorldSavedCubicChunksData) DimensionManager
                    .getWorld(0).perWorldStorage.loadData(WorldSavedCubicChunksData.class, "cubicChunksData");
                if (overworld != null) {
                    minY = overworld.minHeight;
                    maxY = overworld.maxHeight;
                }
            }
            savedData = new WorldSavedCubicChunksData("cubicChunksData", isCC, minY, maxY);
        }
        savedData.markDirty();
        world.perWorldStorage.setData("cubicChunksData", savedData);
        world.perWorldStorage.saveAllData();

        if (!isCC) {
            return;
        }

        if (shouldSkipWorld(world)) {
            CubicChunks.LOGGER.info(
                "Skipping world " + world
                    + " with type "
                    + world.getWorldInfo()
                        .getTerrainType()
                    + " due to potential "
                    + "compatibility issues");
            return;
        }
        CubicChunks.LOGGER.info(
            "Initializing world " + world
                + " with type "
                + world.getWorldInfo()
                    .getTerrainType());

        IntRange generationRange = new IntRange(0, ((ICubicWorldProvider) world.provider).getOriginalActualHeight());
        WorldType type = world.getWorldInfo()
            .getTerrainType();
        if (type instanceof ICubicWorldType && ((ICubicWorldType) type).hasCubicGeneratorForWorld(world)) {
            generationRange = ((ICubicWorldType) type).calculateGenerationHeightRange((WorldServer) world);
        }

        int minHeight = savedData.minHeight;
        int maxHeight = savedData.maxHeight;
        ((ICubicWorldInternal.Server) world)
            .initCubicWorldServerPart1(new IntRange(minHeight, maxHeight), generationRange);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote || !(event.world instanceof WorldServer world)) {
            return; // we will send packet to the client when it joins, client shouldn't change world types as it wants
        }
        onCreateSpawnPoint2(world);
        ((ICubicWorldInternal.Server) world).initCubicWorldServerPart2();
    }

    @SubscribeEvent
    public void onWorldServerTick(TickEvent.WorldTickEvent evt) {
        WorldServer world = (WorldServer) evt.world;
        // Forge (at least version 11.14.3.1521) doesn't call this event for client world.
        if (evt.phase == TickEvent.Phase.END && ((ICubicWorld) world).isCubicWorld() && evt.side == Side.SERVER) {
            ((ICubicWorldInternal) world).tickCubicWorld();
        }
    }

    @SubscribeEvent
    public void onPlayerJoinWorld(EntityJoinWorldEvent evt) {
        if (evt.entity instanceof EntityPlayerMP && ((ICubicWorld) evt.world).isCubicWorld()) {
            PacketDispatcher.sendTo(new PacketCubicWorldData((WorldServer) evt.world), (EntityPlayerMP) evt.entity);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // VanillaNetworkHandler.removeBedrockPlayer((EntityPlayerMP) event.player);
    }

    @SuppressWarnings("unchecked")
    private final List<Class<?>> allowedServerWorldClasses = ImmutableList.copyOf(
        new Class[] { WorldServer.class, WorldServerMulti.class,
            // non-existing classes will be Objects
            ReflectionUtil.getClassOrDefault("WorldServerOF", Object.class), // OptiFine's WorldServer, no package
            ReflectionUtil.getClassOrDefault("WorldServerMultiOF", Object.class), // OptiFine's WorldServerMulti, no
                                                                                  // package
            ReflectionUtil.getClassOrDefault("net.optifine.override.WorldServerOF", Object.class), // OptiFine's
                                                                                                   // WorldServer
            ReflectionUtil.getClassOrDefault("net.optifine.override.WorldServerMultiOF", Object.class), // OptiFine's
                                                                                                        // WorldServerMulti
            ReflectionUtil.getClassOrDefault("com.forgeessentials.multiworld.WorldServerMultiworld", Object.class) // ForgeEssentials
                                                                                                                   // world
        });

    @SuppressWarnings("unchecked")
    private final List<Class<? extends IChunkProvider>> allowedServerChunkProviderClasses = ImmutableList
        .copyOf(new Class[] { ChunkProviderServer.class });

    private boolean shouldSkipWorld(World world) {
        return !allowedServerWorldClasses.contains(world.getClass()) || !allowedServerChunkProviderClasses.contains(
            world.getChunkProvider()
                .getClass());
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote || !((ICubicWorld) event.world).isCubicWorld()) {
            return;
        }

        ICubicWorld world = (ICubicWorld) event.world;
        if (!world.isCubicWorld()) {
            return;
        }

        ICubeIO io = ((ICubeProviderInternal.Server) world.getCubeCache()).getCubeIO();
        try {
            io.close();
        } catch (IOException e) {
            CubicChunks.LOGGER.catching(e);
        }
    }
}
