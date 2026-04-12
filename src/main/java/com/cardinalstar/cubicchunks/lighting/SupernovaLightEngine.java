package com.cardinalstar.cubicchunks.lighting;

import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.mitchej123.supernova.light.ChunkLightHelper;
import com.mitchej123.supernova.light.SWMRNibbleArray;
import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.light.WorldLightManager;
import com.mitchej123.supernova.light.engine.SupernovaEngine;
import com.mitchej123.supernova.util.WorldUtil;
import com.mitchej123.supernova.world.SupernovaWorld;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class SupernovaLightEngine implements ICubicChunkLightingEngine {

    private final World world;
    private final WorldLightManager lightManager;

    public SupernovaLightEngine(World world) {
        this.world = world;
        configureWorldBounds((ICubicWorld) world);
        this.lightManager = ((SupernovaWorld) world).supernova$getLightManager();
    }

    @Override
    public void processLightUpdates() {
        if (this.lightManager.hasUpdates()) {
            this.lightManager.scheduleUpdate();
        }
    }

    @Override
    public void scheduleLightUpdate(EnumSkyBlock lightType, int x, int y, int z) {
        if (this.world.isRemote && ((SupernovaWorld) this.world).supernova$isPlayerAction()) {
            this.lightManager.blockChange(x, y, z);
            return;
        }

        this.lightManager.queueBlockChange(x, y, z);

        if (this.world.isRemote) {
            this.lightManager.scheduleUpdate();
        }
    }

    @Override
    public boolean hasLightUpdates() {
        return this.lightManager.hasUpdates();
    }

    @Override
    public int getSavedLightValue(ICube cube, EnumSkyBlock type, int x, int posY, int z) {
        if (cube == null) {
            return 0;
        }

        SupernovaChunk chunk = (SupernovaChunk) cube.getColumn();
        if (type == EnumSkyBlock.Sky) {
            return ChunkLightHelper.getSkyLight(chunk.getSkyNibblesR(), chunk.getSkyNibblesG(), chunk.getSkyNibblesB(), x, posY, z);
        }
        return ChunkLightHelper.getBlockLight(chunk.getBlockNibblesR(), chunk.getBlockNibblesG(), chunk.getBlockNibblesB(), x, posY, z);
    }

    private static void configureWorldBounds(ICubicWorld world) {
        int minBlockY = Math.min(world.getMinHeight(), world.getMinGenerationHeight());
        int maxBlockY = Math.max(world.getMaxHeight(), world.getMaxGenerationHeight()) - 1;

        WorldUtil.setBounds(Coords.blockToCube(minBlockY), Coords.blockToCube(maxBlockY));
    }

    @Override
    public void onCubeLoad(ICube cube) {
        Chunk column = (Chunk) cube.getColumn();
        SupernovaChunk supernovaChunk = (SupernovaChunk) column;

        // Register column with light manager (idempotent)
        lightManager.registerChunk(column);

        // Import vanilla light data for this cube's section into supernova nibbles
        ExtendedBlockStorage storage = cube.getStorage();
        if (storage != null) {
            int sectionY = cube.getY();
            int minLight = WorldUtil.getMinLightSection();
            int idx = sectionY - minLight;

            SWMRNibbleArray[] blockR = supernovaChunk.getBlockNibblesR();
            if (idx >= 0 && idx < blockR.length) {
                NibbleArray vanillaBlock = storage.getBlocklightArray();
                if (vanillaBlock != null) {
                    blockR[idx] = SWMRNibbleArray.fromVanilla(vanillaBlock);
                    SWMRNibbleArray[] blockG = supernovaChunk.getBlockNibblesG();
                    SWMRNibbleArray[] blockB = supernovaChunk.getBlockNibblesB();
                    if (blockG != null) blockG[idx] = SWMRNibbleArray.fromVanilla(vanillaBlock);
                    if (blockB != null) blockB[idx] = SWMRNibbleArray.fromVanilla(vanillaBlock);
                }

                NibbleArray vanillaSky = storage.getSkylightArray();
                if (vanillaSky != null) {
                    SWMRNibbleArray[] skyR = supernovaChunk.getSkyNibbles();
                    if (idx < skyR.length) {
                        skyR[idx] = SWMRNibbleArray.fromVanilla(vanillaSky);
                        SWMRNibbleArray[] skyG = supernovaChunk.getSkyNibblesG();
                        SWMRNibbleArray[] skyB = supernovaChunk.getSkyNibblesB();
                        if (skyG != null) skyG[idx] = SWMRNibbleArray.fromVanilla(vanillaSky);
                        if (skyB != null) skyB[idx] = SWMRNibbleArray.fromVanilla(vanillaSky);
                    }
                }
            }
        }

        // Queue chunk for BFS lighting so the new cube's section gets properly lit
        Boolean[] emptySections = SupernovaEngine.getEmptySectionsForChunk(column);
        lightManager.queueChunkLight(column.xPosition, column.zPosition, column, emptySections);
        lightManager.scheduleUpdate();

        // Sync sky light back to vanilla nibbles for chunk packets
        if (storage != null) {
            ChunkLightHelper.syncSkyToVanilla(
                supernovaChunk.getSkyNibbles(),
                com.mitchej123.supernova.compat.cubicchunks.CubicChunksHelper.getBlockStorageArrays(column));
        }
    }

    @Override
    public void onCubeUnload(ICube cube) {
        Chunk column = (Chunk) cube.getColumn();

        lightManager.removeChunkFromQueues(column.xPosition, column.zPosition);

        if (!world.isRemote) {
            lightManager.awaitPendingWork(column.xPosition, column.zPosition);
        }

        // Only unregister column when no more cubes are loaded in it
        if (cube.getColumn().getLoadedCubes().isEmpty()) {
            lightManager.unregisterChunk(column.xPosition, column.zPosition);
        }
    }
}
