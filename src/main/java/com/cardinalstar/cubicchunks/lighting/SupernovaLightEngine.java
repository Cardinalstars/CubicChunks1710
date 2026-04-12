package com.cardinalstar.cubicchunks.lighting;

import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.mitchej123.supernova.light.ChunkLightHelper;
import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.light.WorldLightManager;
import com.mitchej123.supernova.util.WorldUtil;
import com.mitchej123.supernova.world.SupernovaWorld;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

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

    }
}
