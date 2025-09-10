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

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.init.Blocks;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.api.util.NotCubicChunksWorldException;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.VanillaCompatibilityGeneratorProviderBase;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.ICubicWorldProvider;
import com.cardinalstar.cubicchunks.world.SpawnPlaceFinder;
import com.cardinalstar.cubicchunks.world.WorldSavedCubicChunksData;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

@ParametersAreNonnullByDefault
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider implements ICubicWorldProvider {

    @Shadow
    public World worldObj;

    @Shadow
    public boolean isHellWorld;

    @Shadow
    public abstract IChunkProvider createChunkGenerator();

    @Shadow(remap = false)
    public abstract int getActualHeight();

    private boolean getActualHeightForceOriginalFlag = false;

    /**
     * @return world height
     * @reason return the real world height instead of hardcoded 256
     * @author Barteks2x
     */
    @Overwrite(remap = false)
    public int getHeight() {
        return ((ICubicWorld) worldObj).getMaxHeight();
    }

    @Inject(method = "getActualHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void getActualHeight(CallbackInfoReturnable<Integer> cir) {
        if (worldObj == null || !(worldObj.getWorldInfo()
                .getTerrainType() instanceof ICubicWorldType)) {
            return;
        }
        cir.setReturnValue(((ICubicWorld) worldObj).getMaxGenerationHeight());
    }

    @Override
    public int getOriginalActualHeight() {
        try {
            getActualHeightForceOriginalFlag = true;
            return getActualHeight();
        } finally {
            getActualHeightForceOriginalFlag = false;
        }
    }

    @Nullable
    @Override
    public ICubeGenerator createCubeGenerator() {
        if (worldObj.getWorldInfo()
            .getTerrainType() instanceof ICubicWorldType
            && ((ICubicWorldType) worldObj.getWorldInfo()
                .getTerrainType()).hasCubicGeneratorForWorld(worldObj)) {
            return ((ICubicWorldType) worldObj.getWorldInfo()
                .getTerrainType()).createCubeGenerator(worldObj);
        }
        WorldSavedCubicChunksData savedData = (WorldSavedCubicChunksData) worldObj.perWorldStorage
            .loadData(WorldSavedCubicChunksData.class, "cubicChunksData");
        return VanillaCompatibilityGeneratorProviderBase.REGISTRY.get(savedData.compatibilityGeneratorType)
            .provideGenerator(this.createChunkGenerator(), worldObj);
    }

    @Inject(method = "getRandomizedSpawnPoint", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private void findRandomizedSpawnPoint(CallbackInfoReturnable<ChunkCoordinates> cir) {
        cir.setReturnValue(SpawnPlaceFinder.getRandomizedSpawnPoint(worldObj));
    }

    @Inject(method = "canCoordinateBeSpawn", at = @At("HEAD"), cancellable = true)
    private void canCoordinateBeSpawnCC(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        BlockPos blockpos = new BlockPos(x, 64, z);

        BlockPos top = SpawnPlaceFinder.getTopBlockBisect(worldObj, blockpos);
        if (top == null) {
            cir.setReturnValue(false);
        } else {
            cir.setReturnValue(this.worldObj.getBlock(top.x, top.y, top.z) == Blocks.grass);
        }

    }

    @Override
    public World getWorld() {
        return worldObj;
    }
}
