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
package com.cardinalstar.cubicchunks.mixin.early.common;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static com.cardinalstar.cubicchunks.util.Coords.cubeToMinBlock;

/**
 * Contains fixes for hardcoded height checks and other height-related issues.
 */
@ParametersAreNonnullByDefault
@Mixin(World.class)
public abstract class MixinWorld_HeightLimit implements ICubicWorld {

    @Shadow private int skylightSubtracted;

    @Shadow @Final public boolean isRemote;

    @Shadow @Final public WorldProvider provider;

    @Shadow public abstract Chunk getChunkFromBlockCoords(int x, int z);

    @Shadow public abstract Block getBlock(int x, int y, int z);

    @Shadow public abstract boolean blockExists(int x, int y, int z);


    @Shadow protected abstract boolean chunkExists(int x, int z);

    // TODO WATCH func_147467_a
    // =================================================
    //          Individual Height Limit Mixins
    // =================================================

    // getBlock
    @ModifyConstant(method = "getBlock",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0)
    )
    private int getBlock_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlock",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getBlock_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // blockExists
    @ModifyConstant(method = "blockExists",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0))
    private int blockExists_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "blockExists",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int blockExists_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // checkChunksExist
    @ModifyConstant(method = "checkChunksExist",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0))
    private int checkChunksExist_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "checkChunksExist",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int checkChunksExist_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // setBlock
    @ModifyConstant(method = "setBlock",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int setBlock_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "setBlock",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int setBlock_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // getBlockMetadata
    @ModifyConstant(method = "getBlockMetadata",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getBlockMetadata_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlockMetadata",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getBlockMetadata_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // setBlockMetadataWithNotify
    @ModifyConstant(method = "setBlockMetadataWithNotify",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int setBlockMetadataWithNotify_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "setBlockMetadataWithNotify",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int setBlockMetadataWithNotify_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // getFullBlockLightValue
    @ModifyConstant(method = "getFullBlockLightValue",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getFullBlockLightValue_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getFullBlockLightValue",
        constant = @Constant(intValue = 0, ordinal = 1))
    private int getFullBlockLightValue_heightLimits_minDefault(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getFullBlockLightValue",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getFullBlockLightValue_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    @ModifyConstant(method = "getFullBlockLightValue",
        constant = @Constant(intValue = 255, ordinal = 0))
    private int getFullBlockLightValue_heightLimits_maxDefault(int original)
    {
        return getMaxHeight();
    }

    // ================= getBlockLightValue_do ======================
    @ModifyConstant(method = "getBlockLightValue_do",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getBlockLightValue_do_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlockLightValue_do",
        constant = @Constant(intValue = 0, ordinal = 1))
    private int getBlockLightValue_do_heightLimits_minDefault(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlockLightValue_do",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getBlockLightValue_do_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    @ModifyConstant(method = "getBlockLightValue_do",
        constant = @Constant(intValue = 255, ordinal = 0))
    private int getBlockLightValue_do_heightLimits_maxDefault(int original)
    {
        return getMaxHeight();
    }

    // getSkyBlockTypeBrightness
    @ModifyConstant(method = "getSkyBlockTypeBrightness",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getSkyBlockTypeBrightness_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getSkyBlockTypeBrightness",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getSkyBlockTypeBrightness_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // getSavedLightValue
    @ModifyConstant(method = "getSavedLightValue",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getSavedLightValue_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getSavedLightValue",
        constant = @Constant(intValue = 0, ordinal = 1))
    private int getSavedLightValue_heightLimits_minDefault(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getSavedLightValue",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getSavedLightValue_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    @ModifyConstant(method = "getSavedLightValue",
        constant = @Constant(intValue = 255, ordinal = 0))
    private int getSavedLightValue_heightLimits_maxDefault(int original)
    {
        return getMaxHeight();
    }

    // setLightValue
    @ModifyConstant(method = "setLightValue",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0))
    private int setLightValue_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "setLightValue",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int setLightValue_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // getTileEntity
    @ModifyConstant(method = "getTileEntity",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0))
    private int getTileEntity_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getTileEntity",
        constant = @Constant(intValue = 256, ordinal = 0))
    private int getTileEntity_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // canBlockFreezeBody
    @ModifyConstant(method = "canBlockFreezeBody(IIIZ)Z",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0),
        remap = false)
    private int canBlockFreezeBody_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "canBlockFreezeBody(IIIZ)Z",
        constant = @Constant(intValue = 256, ordinal = 0),
        remap = false)
    private int canBlockFreezeBody_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // canSnowAtBody
    @ModifyConstant(method = "canSnowAtBody",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO, intValue = 0, ordinal = 0),
        remap = false)
    private int canSnowAtBody_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "canSnowAtBody",
        constant = @Constant(intValue = 256, ordinal = 0),
        remap = false)
    private int canSnowAtBody_heightLimits_max(int original)
    {
        return getMaxHeight();
    }

    // getBlockLightOpacity
    @ModifyConstant(method = "getBlockLightOpacity",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0),
        remap = false)
    private int getBlockLightOpacity_heightLimits_min(int original)
    {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlockLightOpacity",
        constant = @Constant(intValue = 256, ordinal = 0),
        remap = false)
    private int getBlockLightOpacity_heightLimits_max(int original)
    {
        return getMaxHeight();
    }


//    /**
//     * @param pos block positionn
//     * @return light value at the given position
//     *
//     * @author Barteks2x
//     * @reason Replace {@link World#getLight(BlockPos)} with method that works outside of 0..255 height range. It would
//     * be possible to fix it using @Redirect and @ModifyConstant but this way is much cleaner, especially for simple
//     * method. A @{@link ModifyConstant} wouldn't work because it can't replace comparison to 0. This is because there
//     * is a special instruction to compare something to 0, so the constant is never used.
//     * <p>
//     * Note: The getLight method is used in parts of game logic and entity rendering code. Doesn't directly affect block
//     * rendering.
//     */
//    @Overwrite
//    public int getFullBlockLightValue(BlockPos pos) {
//        if (pos.getY() < this.getMinHeight()) {
//            return 0;
//        }
//        if (pos.getY() >= this.getMaxHeight()) {
//            //CubicChunks edit
//            //return default light value above maxHeight instead of the same value as at maxHeight
//            return EnumSkyBlock.SKY.defaultLightValue;
//            //CubicChunks end
//        }
//        return this.getChunk(pos).getLightSubtracted(pos, 0);
//    }
//
//    /**
//     * This getLight method is used in parts of game logic and entity rendering code.
//     * Doesn't directly affect block rendering.
//     */
//    @Group(name = "getLightHeightOverride", max = 4)
//    @ModifyConstant(
//        method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
//        constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
//        slice = @Slice(
//            from = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;getY()I"),
//            to = @At(value = "INVOKE",
//                target = "Lnet/minecraft/world/World;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/Chunk;")
//        ))
//    private int getLightGetYReplace(int zero) {
//        return getMinHeight();
//    }
//
//    /**
//     * Modify constant 255 in {@link World#getLight(BlockPos)} used in case tha height check didn't pass.
//     * When max height is exceeded vanilla clamps the value to 255 (maxHeight - 1 = actual max allowed block Y).
//     */
//    @Group(name = "getLightHeightOverride")
//    @ModifyConstant(method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
//        constant = {@Constant(intValue = 255), @Constant(intValue = 256)}, require = 2)
//    private int getLightGetReplacementYTooHigh(int original) {
//        return this.getMaxHeight() + original - 256;
//    }
//
//    /**
//     * Redirect 0 constant in getLightFor(EnumSkyBlock, BlockPos)
//     * so that getLightFor returns light at y=minHeight when below minHeight.
//     */
//    @Group(name = "getLightForHeightOverride", min = 2, max = 2)
//    @ModifyConstant(method = "getLightFor",
//        constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.LESS_THAN_ZERO))
//    private int getLightForGetMinYReplace(int origY) {
//        return this.getMinHeight();
//    }

    /**
     * Conditionally replaces isAreaLoaded with Cubic Chunks implementation
     * (continues with vanilla code if it's not a cubic chunks world).
     * World.isAreaLoaded is used to check if some things can be updated (like light).
     * If it returns false - update doesn't happen. This fixes it
     * <p>
     * NOTE: there are some methods that use it incorrectly
     * ie. by checking it at some constant height (usually 0 or 64).
     * These places need to be modified.
     *
     * @author Barteks2x
     */
    @Group(name = "exists", max = 1)
    @Inject(method = "checkChunksExist(IIIIII)Z", at = @At(value = "HEAD"), cancellable = true, require = 1)
    private void checkChunksExistInject(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd, boolean allowEmpty,
                                    @Nonnull CallbackInfoReturnable<Boolean> cbi) {
        if (!this.isCubicWorld()) {
            return;
        }

        boolean ret = (this.isRemote && allowEmpty) || // on the client all cubes count as loaded if allowEmpty
            this.testForCubes(
                xStart, yStart, zStart,
                xEnd, yEnd, zEnd,
                Objects::nonNull);

        cbi.setReturnValue(ret);
    }

    // NOTE: This may break some things

    /**
     * @param x block x position
     * @param y block y position
     * @param z block z position
     * @param cbi callback info
     *
     * @author Barteks2x
     * @reason CubicChunks needs to check if cube is loaded instead of chunk
     */
    @Inject(method = "blockExists(III)Z", cancellable = true, at = @At(value = "HEAD"))
    private void isBlockLoaded(int x, int y, int z, CallbackInfoReturnable<Boolean> cbi) {
        if (!isCubicWorld()) {
            return;
        }
        ICube cube = this.getCubeCache().getLoadedCube(blockToCube(x), blockToCube(y), blockToCube(z));
        cbi.setReturnValue(cube != null && !(cube instanceof BlankCube));
    }

    @Redirect(method = "updateEntityWithOptionalForce",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 0))
    private boolean updateEntityWithOptionalForce_chunkExists0(World world, int chunkX, int chunkZ, Entity ent, boolean force) {
        assert this == (Object) world;
        if (isCubicWorld()) {
            return this.blockExists(cubeToMinBlock(chunkX), cubeToMinBlock(ent.chunkCoordY), cubeToMinBlock(chunkZ));
        } else {
            return this.chunkExists(chunkX, chunkZ);
        }
    }

    @Redirect(method = "updateEntityWithOptionalForce",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 1))
    private boolean updateEntityWithOptionalForce_chunkExists1(World world, int chunkX, int chunkZ, Entity ent, boolean force) {
        assert this == (Object) world;
        if (isCubicWorld()) {
            return this.blockExists(cubeToMinBlock(chunkX), cubeToMinBlock(ent.chunkCoordY), cubeToMinBlock(chunkZ));
        } else {
            return this.chunkExists(chunkX, chunkZ);
        }
    }

    private int updateEntities_enityChunkBlockY;

    @Inject(method = "updateEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 0),
        locals = LocalCapture.CAPTURE_FAILHARD,
        require = 1)
    private void updateEntities_chunkExists0_getLocals(CallbackInfo cbi, int i, Entity entity, CrashReport crashreport, CrashReportCategory crashreportcategory, int chunkX, int chunkZ) {
        updateEntities_enityChunkBlockY = cubeToMinBlock(entity.chunkCoordY);
    }

    @Inject(method = "updateEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 1),
        locals = LocalCapture.CAPTURE_FAILHARD,
        require = 1)
    private void updateEntities_chunkExists1_getLocals(CallbackInfo cbi, int i, Entity entity, CrashReport crashreport, CrashReportCategory crashreportcategory, int chunkX, int chunkZ) {
        updateEntities_enityChunkBlockY = cubeToMinBlock(entity.chunkCoordY);
    }

    @Redirect(method = "updateEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 1))
    private boolean updateEntities_chunkExists(World world, int chunkX, int chunkZ) {
        assert this == (Object) world;
        if (isCubicWorld()) {
            return this.blockExists(cubeToMinBlock(chunkX), updateEntities_enityChunkBlockY, cubeToMinBlock(chunkZ));
        } else {
            return this.chunkExists(chunkX, chunkZ);
        }
    }

    @Inject(method = "getBiomeGenAt", at = @At("HEAD"), cancellable = true)
    private void getBiome(int x, int y, int z, CallbackInfoReturnable<BiomeGenBase> ci) {
        if (!this.isCubicWorld())
            return;
        ICube cube = this.getCubeCache().getLoadedCube(Coords.blockToCube(x),Coords.blockToCube(y),Coords.blockToCube(z));
        /*
         * Using return here function will keep callback not cancelled,
         * therefore "vanilla" function, which will get biome from chunk, will
         * be called. Since cube is null there is no way to retrieve chunk
         * faster, than using vanilla way.
         */
        if (cube == null)
            return;
        BiomeGenBase biome = cube.getBiome(x, y, z);
        ci.setReturnValue(biome);
    }

    @ModifyConstant(method = {"canSnowAtBody", "canBlockFreezeBody"}, constant = @Constant(intValue = 256), remap = false)
    private int canSnowAt_getMaxHeight(int _256) {
        return getMaxHeight();
    }

    @ModifyConstant(method = {"canSnowAtBody", "canBlockFreezeBody"},
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
        remap = false)
    private int canSnowAt_getMinHeight(int zero) {
        return getMinHeight();
    }
}
