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

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;

import java.util.Random;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameRules;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.util.NotCubicChunksWorldException;
import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldSettings;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Contains implementation of {@link ICubicWorld} interface.
 */
@ParametersAreNonnullByDefault
@Mixin(World.class)
@Implements(@Interface(iface = ICubicWorld.class, prefix = "world$"))
public abstract class MixinWorld implements ICubicWorldInternal {

    // these have to be here because of mixin limitation, they are used by MixinWorldServer
    @Shadow
    public abstract ISaveHandler getSaveHandler();

    // TODO FIGURE OUT WHERE THESE GO
    @Shadow
    public abstract boolean checkChunksExist(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    @Shadow
    public abstract boolean doChunksNearChunkExist(int x, int y, int z, int radius);

    @Shadow
    protected IChunkProvider chunkProvider;
    @Shadow
    @Final
    @Mutable
    public WorldProvider provider;
    @Shadow
    @Final
    public Random rand;
    @Shadow
    @Final
    public boolean isRemote;
    @Shadow
    @Final
    public Profiler theProfiler;
    @Shadow
    @Final
    @Mutable
    protected ISaveHandler saveHandler;
    @Shadow
    protected boolean findingSpawnPoint;
    @Shadow
    protected WorldInfo worldInfo;
    @Shadow
    protected int updateLCG;

    @Shadow
    protected abstract boolean chunkExists(int i, int i1);

    @Nullable
    protected LightingManager lightingManager;
    protected boolean isCubicWorld;
    protected int minHeight = 0, maxHeight = 256, fakedMaxHeight = 0;
    private int minGenerationHeight = 0, maxGenerationHeight = 256;

    // @Shadow public abstract boolean isValid(BlockPos pos);

    @Shadow
    public abstract GameRules getGameRules();

    @Shadow
    public abstract boolean isRaining();

    @Shadow
    public abstract boolean isThundering();

    // @Shadow public abstract boolean isRainingAt(BlockPos position);

    // @Shadow public abstract DifficultyInstance getDifficultyForLocation(BlockPos pos);

    @Shadow
    public abstract int getPrecipitationHeight(int blockX, int blockY);

    @Shadow
    protected abstract void setActivePlayerChunksAndCheckLight();

    @Shadow
    public abstract boolean canLightningStrikeAt(int x, int y, int z);

    @Shadow
    public abstract boolean canBlockFreeze(int x, int y, int z, boolean byWater);

    @Shadow
    public abstract boolean setBlock(int x, int y, int z, Block blockIn);

    @Shadow
    public abstract boolean setBlock(int x, int y, int z, Block blockIn, int metaIn, int flags);

    @Shadow
    public abstract boolean isBlockFreezableNaturally(int x, int y, int z);

    // canSnowAt
    @Shadow
    public abstract boolean func_147478_e(int x, int y, int z, boolean checkLight);

    // @Shadow public abstract boolean isBlockLoaded(BlockPos pos);

    @Shadow
    public abstract BiomeGenBase getBiomeGenForCoords(int x, int z);

    // @Shadow public abstract boolean isBlockLoaded(BlockPos pos, boolean allowEmpty);

    @Shadow
    public abstract Chunk getChunkFromBlockCoords(int x, int z);

    @Shadow
    public abstract boolean canBlockSeeTheSky(int x, int y, int z);

    @Shadow
    public abstract void setLightValue(EnumSkyBlock type, int x, int y, int z, int lightValue);

    /*
     * This shadow method is used by MixinWorldServer, place in here for Bukkit compatibility.
     * As World#spawnEntity method is not getting overridden in CraftBukkit WorldServer class,
     * shadowing spawnEntity in WorldServer will break Bukkit compatibility.
     */
    @Shadow
    public abstract boolean spawnEntityInWorld(Entity entityIn);

    protected void initCubicWorld(IntRange heightRange, IntRange generationRange) {
        ((ICubicWorldSettings) worldInfo).setCubic(true);
        // Set the world height boundaries to their highest and lowest values respectively
        this.minHeight = heightRange.getMin();
        this.maxHeight = heightRange.getMax();
        this.fakedMaxHeight = this.maxHeight;

        this.minGenerationHeight = generationRange.getMin();
        this.maxGenerationHeight = generationRange.getMax();
    }

    @Override
    public boolean isCubicWorld() {
        return this.isCubicWorld;
    }

    @Override
    public int getMinHeight() {
        return this.minHeight;
    }

    @Override
    public int getMaxHeight() {
        return this.maxHeight;
    }

    @Override
    public int getMinGenerationHeight() {
        return this.minGenerationHeight;
    }

    @Override
    public int getMaxGenerationHeight() {
        return this.maxGenerationHeight;
    }

    @Override
    public ICubeProviderInternal getCubeCache() {
        if (!this.isCubicWorld()) {
            throw new NotCubicChunksWorldException();
        }
        return (ICubeProviderInternal) this.chunkProvider;
    }

    @Override
    public LightingManager getLightingManager() {
        if (!this.isCubicWorld()) {
            throw new NotCubicChunksWorldException();
        }
        assert this.lightingManager != null;
        return this.lightingManager;
    }

    @Override
    public boolean testForCubes(CubePos start, CubePos end, Predicate<? super ICube> cubeAllowed) {
        // convert block bounds to chunk bounds
        int minCubeX = start.getX();
        int minCubeY = start.getY();
        int minCubeZ = start.getZ();
        int maxCubeX = end.getX();
        int maxCubeY = end.getY();
        int maxCubeZ = end.getZ();

        for (int cubeX = minCubeX; cubeX <= maxCubeX; cubeX++) {
            for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) {
                for (int cubeZ = minCubeZ; cubeZ <= maxCubeZ; cubeZ++) {
                    Cube cube = this.getCubeCache()
                        .getLoadedCube(cubeX, cubeY, cubeZ);
                    if (!cubeAllowed.test(cube)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public Cube getCubeFromCubeCoords(int cubeX, int cubeY, int cubeZ) {
        return this.getCubeCache()
            .getCube(cubeX, cubeY, cubeZ);
    }

    @Override
    public Cube getCubeFromBlockCoords(int blockX, int blockY, int blockZ) {
        return this.getCubeFromCubeCoords(blockToCube(blockX), blockToCube(blockY), blockToCube(blockZ));
    }

    @Override
    public Cube getCubeFromBlockCoords(BlockPos pos) {
        return this.getCubeFromBlockCoords(blockToCube(pos.getX()), blockToCube(pos.getY()), blockToCube(pos.getZ()));
    }

    @Override
    public int getEffectiveHeight(int blockX, int blockZ) {
        return this.chunkProvider.provideChunk(blockToCube(blockX), blockToCube(blockZ))
            .getHeightValue(blockToLocal(blockX), blockToLocal(blockZ));
    }

    // suppress mixin warning when running with -Dmixin.checks.interfaces=true
    @Override
    public void tickCubicWorld() {
        // pretend this method doesn't exist
        throw new NoSuchMethodError(
            "World.tickCubicWorld: Classes extending World need to implement tickCubicWorld in CubicChunks");
    }

    @Override
    public void fakeWorldHeight(int height) {
        this.fakedMaxHeight = height;
    }

    /**
     * Some mod's world generation will try to do their work over the whole world height.
     * This allows to fake the world height for them.
     *
     * @return world height
     * @author Barteks2x
     * @reason Optionally return fake height
     */
    @Overwrite
    public int getHeight() {
        if (fakedMaxHeight != 0) {
            return fakedMaxHeight;
        }
        return this.provider.getHeight();
    }

    @Inject(method = "updateLightByType", at = @At("HEAD"), cancellable = true)
    private void updateLightByType(EnumSkyBlock lightType, int x, int y, int z, CallbackInfoReturnable<Boolean> ci) {
        if (!isCubicWorld()) {
            return;
        }
        ci.setReturnValue(getLightingManager().checkLightFor(lightType, x, y, z));
    }

    /**
     * @param x                x block position
     * @param y                y block position
     * @param z                z block position
     * @param unusedTileEntity tile entity instance, unused
     * @param ci               callback info
     *
     * @author Foghrye4
     * @reason Original {@link World#markTileEntityChunkModified}
     *         called by TileEntities whenever they need to force Chunk to save
     *         valuable info they changed. Because now we store TileEntities in
     *         Cubes instead of Chunks, it will be quite reasonable to force
     *         Cubes to save themselves.
     */
    @Inject(method = "markTileEntityChunkModified", at = @At("HEAD"), cancellable = true)
    private void onMarkChunkDirty(int x, int y, int z, TileEntity unusedTileEntity, CallbackInfo ci) {
        if (this.isCubicWorld()) {
            Cube cube = this.getCubeCache()
                .getLoadedCube(CubePos.fromBlockCoords(x, y, z));
            if (cube != null) {
                cube.markDirty();
            }
            ci.cancel();
        }
    }

    /**
     * @param x block x position
     * @param y block y position
     * @param z block z position
     * @return block at that position
     * @author Barteks2x
     * @reason Injection causes performance issues, overwrite for cubic chunks version
     */
    @Overwrite
    public Block getBlock(int x, int y, int z) {
        if (y >= getMaxHeight() || y < getMinHeight()) { // TODO: maybe avoid height check for cubic chunks world?
            return Blocks.air;
        }
        if (this.isCubicWorld) {
            ICube cube = ((ICubeProviderInternal) this.chunkProvider)
                .getCube(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));
            if (cube == null) {
                CubicChunks.LOGGER.info("NULL cube found at {}, {}, {}, returning Blocks.air", x, y, z);
                return Blocks.air;
            }
            return cube.getBlock(x, y, z);
        } else {
            Chunk chunk = this.getChunkFromBlockCoords(x, z);
            if (chunk == null) {
                CubicChunks.LOGGER.info("NULL chunk found at {}, {}, {}, returning Blocks.air", x, y, z);
                return Blocks.air;
            }
            return chunk.getBlock(x & 15, y, z & 15);
        }
    }

    @Inject(method = "getTopSolidOrLiquidBlock", at = @At("HEAD"), cancellable = true)
    private void getTopSolidOrLiquidBlockCubicChunks(int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (!isCubicWorld()) {
            return;
        }
        Chunk chunk = this.getChunkFromBlockCoords(x, z);
        int currentY = getPrecipitationHeight(x, z);
        int minY = currentY - 64;
        while (currentY >= minY) {
            int nextY = currentY - 1;
            Block block = chunk.getBlock(Coords.blockToLocal(x), nextY, Coords.blockToLocal(z));

            if (block.getMaterial()
                .blocksMovement() && !block.isLeaves((IBlockAccess) this, x, nextY, z)
                && !block.isFoliage((IBlockAccess) this, x, nextY, z)) {
                break;
            }
            currentY = nextY;
        }
        cir.setReturnValue(currentY);
    }

    @Override
    public boolean isBlockColumnLoaded(int x, int y, int z) {
        return this.chunkExists(blockToCube(x), blockToCube(z));
    }

    @ModifyConstant(method = "getCollidingBoundingBoxes", constant = @Constant(intValue = 64), require = 1)
    private int collidingBoxFix1(int constant, @Local(argsOnly = true) AxisAlignedBB box) {
        return (int) ((box.maxY - box.minY) / 2 + box.minY);
    }

    @ModifyConstant(method = "func_147461_a", constant = @Constant(intValue = 64), require = 1)
    private int collidingBoxFix2(int constant, @Local(argsOnly = true) AxisAlignedBB box) {
        return (int) ((box.maxY - box.minY) / 2 + box.minY);
    }
}
