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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent.Load;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.cardinalstar.cubicchunks.world.column.ColumnTileEntityMap;
import com.cardinalstar.cubicchunks.world.column.CubeMap;
import com.cardinalstar.cubicchunks.world.column.EmptyEBS;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.core.ServerHeightMap;
import com.cardinalstar.cubicchunks.world.core.StagingHeightMap;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Modifies vanilla code in Chunk to use Cubes
 */
// TODO: redirect isChunkLoaded where needed
@ParametersAreNonnullByDefault
@Mixin(value = Chunk.class, priority = 999)
// soft implements for IColumn and IColumnInternal
// we can't implement them directly as that causes FG6+ to reobfuscate IColumn#getHeightValue(int, int)
// into vanilla SRG name, which breaks API and mixins
@Implements({ @Interface(iface = IColumn.class, prefix = "chunk$"),
    @Interface(iface = IColumnInternal.class, prefix = "chunk_internal$") })
public abstract class MixinChunk_Cubes {

    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;

    @Shadow
    private boolean hasEntities;
    @Shadow
    @Final
    public int xPosition;
    @Shadow
    @Final
    public int zPosition;
    @Shadow
    @Final
    private List<Entity>[] entityLists;

    @Shadow
    @Final
    @Mutable
    public Map<net.minecraft.world.ChunkPosition, net.minecraft.tileentity.TileEntity> chunkTileEntityMap;

    @Shadow
    @Final
    private int[] heightMap;
    @Shadow
    @Final
    private World worldObj;
    @Shadow
    private boolean isChunkLoaded;
    @Shadow
    private boolean isLightPopulated;
    @Shadow
    private boolean isModified;
    @Shadow
    private boolean field_150815_m;
    /*
     * WARNING: WHEN YOU RENAME ANY OF THESE 3 FIELDS RENAME CORRESPONDING
     * FIELDS IN "cubicchunks.asm.mixin.core.client.MixinChunk_Cubes" and
     * "cubicchunks.asm.mixin.core.common.MixinChunk_Columns".
     */
    @Unique
    private CubeMap cubeMap;
    @Unique
    private IHeightMap opacityIndex;
    @Unique
    private Cube cachedCube; // todo: make it always nonnull using BlankCube
    @Unique
    private StagingHeightMap stagingHeightMap;
    private boolean isColumn = false;

    private Block[] compatGenerationBlockArray;
    private byte[] compatGenerationByteArray;

    @Shadow
    public abstract byte[] getBiomeArray();

    @SuppressWarnings({ "deprecation", "RedundantSuppression" })
    @Shadow
    public abstract int getHeightValue(int x, int z);

    @Shadow
    public abstract int getSavedLightValue(EnumSkyBlock p_76614_1_, int p_76614_2_, int p_76614_3_, int p_76614_4_);

    @Unique
    @SuppressWarnings({ "unchecked", "AddedMixinMembersNamePattern" })
    public <T extends World & ICubicWorldInternal> T getWorldObj() {
        return (T) this.worldObj;
    }

    // TODO: make it go through cube raw access methods
    // TODO: make cube an interface, use the implementation only here
    @Unique
    @Nullable
    private ExtendedBlockStorage getEBS_CubicChunks(int index) {
        if (!isColumn) {
            // vanilla case, subtract minHeight for extended height support
            return storageArrays[index - blockToCube(getWorldObj().getMinHeight())];
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            return cachedCube.getStorage();
        }
        Cube cube = getWorldObj().getCubeCache()
            .getCube(this.xPosition, index, this.zPosition);
        if (!(cube instanceof BlankCube)) {
            cachedCube = cube;
        }
        return cube.getStorage();
    }

    // setEBS is unlikely to be used extremely frequently, no caching
    @Unique
    private void setEBS_CubicChunks(int index, ExtendedBlockStorage ebs) {
        if (!isColumn) {
            // vanilla case, subtract minHeight for extended height support
            storageArrays[index - blockToCube(getWorldObj().getMinHeight())] = ebs;
            return;
        }
        if (index >= 0 && index < 16) {
            storageArrays[index] = ebs;
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            cachedCube.setStorage(ebs);
            return;
        }
        Cube loaded = getWorldObj().getCubeCache()
            .getLoadedCube(this.xPosition, index, this.zPosition);
        if (loaded == null) {
            // BlankCube clientside. This is the only case where getEBS doesn't create cube
            return;
        }
        if (loaded.getStorage() == null) {
            loaded.setStorage(ebs);
        } else {
            throw new IllegalStateException(
                String.format(
                    "Attempted to set a Cube ExtendedBlockStorage that already exists. " + "This is not supported. "
                        + "CubePos(%d, %d, %d), loadedCube(%s), loadedCubeStorage(%s)",
                    this.xPosition,
                    index,
                    this.zPosition,
                    loaded,
                    loaded.getStorage()));
        }
    }

    // modify vanilla:

    @ModifyConstant(
        method = "<init>(Lnet/minecraft/world/World;II)V",
        constant = @Constant(intValue = 16),
        slice = @Slice(
            to = @At(
                value = "FIELD",
                target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
                opcode = Opcodes.PUTFIELD)),
        allow = 1,
        require = 1)
    private int modifySectionArrayLength(int sixteen, World worldIn, int x, int z) {
        if (worldIn == null) {
            // Some mods construct chunks with null world, ignore them
            return sixteen;
        }
        if (!((ICubicWorld) worldIn).isCubicWorld()) {
            IMinMaxHeight y = (IMinMaxHeight) worldIn;
            return Coords.blockToCube(y.getMaxHeight()) - Coords.blockToCube(y.getMinHeight());
        }
        return sixteen;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At(value = "RETURN"))
    private void cubicChunkColumn_construct(World world, int x, int z, CallbackInfo cbi) {
        if (world == null) {
            // Some mods construct chunks with null world, ignore them
            return;
        }
        if (!((ICubicWorld) world).isCubicWorld()) {
            return;
        }
        this.isColumn = true;
        // this.lightManager = world.getLightingManager();

        this.cubeMap = new CubeMap();
        // clientside we don't really need that much data. we actually only need top and bottom block Y positions
        if (world.isRemote) {
            this.opacityIndex = new ClientHeightMap((Chunk) (Object) this, heightMap);
        } else {
            this.opacityIndex = new ServerHeightMap(heightMap);
        }
        this.stagingHeightMap = new StagingHeightMap();
        // instead of redirecting access to this map, just make the map do the work
        this.chunkTileEntityMap = new ColumnTileEntityMap((IColumn) this);

        // this.chunkSections = null;
        // this.skylightUpdateMap = null;

        Arrays.fill(getBiomeArray(), (byte) -1);
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;II)V",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage init_getStorage(ExtendedBlockStorage[] ebs, int y) {
        return ebs[y - (this.isColumn ? 0 : blockToCube(((IMinMaxHeight) worldObj).getMinHeight()))];
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;II)V",
        at = @At(
            value = "FIELD",
            args = "array=set",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private void init_getMaxHeight(ExtendedBlockStorage[] ebs, int y, ExtendedBlockStorage val) {
        ebs[y - (this.isColumn ? 0 : blockToCube(((IMinMaxHeight) worldObj).getMinHeight()))] = val;
    }

    @ModifyConstant(
        method = "<init>(Lnet/minecraft/world/World;[Lnet/minecraft/block/Block;[BII)V",
        constant = @Constant(intValue = 16, ordinal = 0),
        require = 1)
    private int getInitChunkLoopEnd(int _16, World world, Block[] blocks, byte[] metaDatas, int x, int z) {
        if (((ICubicWorldInternal.Server) world).isCompatGenerationScope()) {
            this.compatGenerationBlockArray = blocks;
            this.compatGenerationByteArray = metaDatas;
            return -1;
        }
        return _16;
    }

    public Block[] chunk_internal$getCompatGenerationBlockArray() {
        return compatGenerationBlockArray;
    }

    public byte[] chunk_internal$getCompatGenerationByteArray() {
        return compatGenerationByteArray;
    }

    // this method can't be saved by just redirecting EBS access
    @Inject(method = "getTopFilledSegment", at = @At(value = "HEAD"), cancellable = true)
    private void getTopFilledSegment_CubicChunks(CallbackInfoReturnable<Integer> cbi) {
        if (!isColumn) {
            return;
        }
        int blockY = Coords.NO_HEIGHT;
        for (int localX = 0; localX < Cube.SIZE; localX++) {
            for (int localZ = 0; localZ < Cube.SIZE; localZ++) {
                int y = this.opacityIndex.getTopBlockY(localX, localZ);
                if (y > blockY) {
                    blockY = y;
                }
            }
        }
        if (blockY < getWorldObj().getMinHeight()) {
            // PANIC!
            // this column doesn't have any blocks in it that aren't air!
            // but we can't return null here because vanilla code expects there to be a surface down there somewhere
            // we don't actually know where the surface is yet, because maybe it hasn't been generated
            // but we do know that the surface has to be at least at sea level,
            // so let's go with that for now and hope for the best

            int ret = Coords.cubeToMinBlock(blockToCube(this.getWorldObj().provider.getAverageGroundLevel()));
            cbi.setReturnValue(ret);
            return;
        }
        int ret = Coords.cubeToMinBlock(blockToCube(blockY)); // return the lowest block in the Cube (kinda weird I
                                                              // know)
        cbi.setReturnValue(ret);
    }

    /*
     * Light update code called from this:
     * if (addedNewCube) {
     * generateSkylightMap();
     * } else {
     * if (placingOpaque) {
     * if (placingNewTopBlock) {
     * relightBlock(x, y + 1, zPosition);
     * } else if (removingTopBlock) {
     * relightBlock(x, y, zPosition);
     * }
     * }
     * // equivalent to opacityDecreased || (opacityChanged && receivesLight)
     * // which means: propagateSkylight if it lets more light through, or (it receives any light and opacity changed)
     * if (opacityChanged && (opacityDecreased || blockReceivesLight)) {
     * propagateSkylightOcclusion(x, zPosition);
     * }
     * }
     */
    // ==============================================
    // generateSkylightMap
    // ==============================================

    @Inject(method = "generateSkylightMap", at = @At(value = "HEAD"), cancellable = true)
    private void generateSkylightMap_CubicChunks_Replace(CallbackInfo cbi) {
        if (isColumn) {
            // TODO: update skylight in cubes marked for update
            cbi.cancel();
        }
    }

    @Nullable
    @Redirect(
        method = "generateSkylightMap",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage generateSkylightMapRedirectEBSAccess(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    // propagateSkylightOcclusion
    // ==============================================

    @Inject(method = "propagateSkylightOcclusion", at = @At(value = "HEAD"), cancellable = true)
    private void propagateSkylightOcclusion_CubicChunks_Replace(int x, int z, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    // recheckGaps
    // ==============================================

    @Inject(method = "recheckGaps", at = @At(value = "HEAD"), cancellable = true)
    private void recheckGaps_CubicChunks_Replace(boolean p_150803_1_, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // private void checkSkylightNeighborHeight(int x, int zPosition, int maxValue) - shouldn't be used by anyone

    // private void updateSkylightNeighborHeight(int x, int zPosition, int startY, int endY) - shouldn't be used by
    // anyone

    // ==============================================
    // relightBlock
    // ==============================================

    // TODO is this too brittle?
    /**
     * Modifies the flag variable so that the code always gets into the branch with Chunk.relightBlock redirected below
     */
    @ModifyVariable(method = "func_150807_a", at = @At(value = "STORE"), ordinal = 0, name = "flag")
    private boolean setBlockStateInjectGenerateSkylightMapVanilla(boolean generateSkylight) {
        if (!isColumn) {
            return generateSkylight;
        }
        return false;
    }

    @Inject(
        method = "func_150807_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;relightBlock(III)V"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void setBlockState_CubicChunks_relightBlockReplace(int localX, int localY, int localZ, Block p_150807_4_,
        int newMeta, CallbackInfoReturnable<Boolean> cir, int packedXZ, int oldHeightValue, Block oldBlock, int oldMeta,
        ExtendedBlockStorage ebs, boolean createdNewEbsAboveTop, int globalX, int globalZ, int oldOpacity,
        int newOpacity) {

        if (isColumn && ((IColumn) this).getCube(blockToCube(localY))
            .isInitialLightingDone()) {
            if (oldHeightValue == localY + 1) { // oldHeightValue is the previous block Y above the top block, so this
                                                // is the "removing to block" case
                getWorldObj().getLightingManager()
                    .doOnBlockSetLightUpdates(
                        (Chunk) (Object) this,
                        localX,
                        getHeightValue(localX, localZ),
                        localY,
                        localZ);
            } else {
                getWorldObj().getLightingManager()
                    .doOnBlockSetLightUpdates((Chunk) (Object) this, localX, oldHeightValue, localY, localZ);
            }
        }
    }

    @Redirect(
        method = "func_150807_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I"))
    private int setBlockState_CubicChunks_noGetLightFor(Chunk instance, EnumSkyBlock type, int x, int y, int z) {
        if (!isColumn) {
            return instance.getSavedLightValue(type, x, y, z);
        }
        return 0;
    }

    // make relightBlock no-op for cubic chunks, handles by injection above
    @Inject(method = "relightBlock", at = @At(value = "HEAD"), cancellable = true)
    private void relightBlock_CubicChunks_Replace(int x, int y, int z, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    // getBlockLightOpacity
    // ==============================================

    // TODO Maybe check this. For some reason there is no isLoaded check on this.
    // @Redirect(method = "func_150808_b(III)I", at = @At(value = "FIELD", target =
    // "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    // private boolean getBlockLightOpacity_isChunkLoadedCubeRedirect(Chunk chunk, int x, int y, int z) {
    // if (!isColumn) {
    // return isChunkLoaded;
    // }
    // ICube cube = ((IColumn) this).getLoadedCube(blockToCube(y));
    // return cube != null && cube.isCubeLoaded();
    // }

    // ==============================================
    // getBlock
    // ==============================================

    // This check isn't made in 1.7.10
    // @ModifyConstant(method = "getBlock(III)Lnet/minecraft/block/Block;",
    // constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO),
    // require = 1)
    // private int getBlockState_getMinHeight(int zero) {
    // return isColumn ? Integer.MIN_VALUE : getWorldObj().getMinHeight(); // this one is in block coords, max is in
    // cube coords. Mojang logic.
    // }
    @Redirect(
        method = "getBlock(III)Lnet/minecraft/block/Block;",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private int getBlock_getMaxHeight(ExtendedBlockStorage[] ebs) {
        return isColumn ? Integer.MAX_VALUE : (ebs.length - blockToCube(getWorldObj().getMinHeight()));
    }

    @Redirect(
        method = "getBlock(III)Lnet/minecraft/block/Block;",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage getBlock_getStorage(ExtendedBlockStorage[] ebs, int y) {
        return getEBS_CubicChunks(y);
    }

    // ==============================================
    // getBlockMetadata
    // ==============================================

    @Redirect(
        method = "getBlockMetadata(III)I",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private int getBlockMetadata_getMaxHeight(ExtendedBlockStorage[] ebs) {
        return isColumn ? Integer.MAX_VALUE : (ebs.length - blockToCube(getWorldObj().getMinHeight()));
    }

    @Redirect(
        method = "getBlockMetadata(III)I",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage getBlockMetadata_getStorage(ExtendedBlockStorage[] ebs, int y) {
        return getEBS_CubicChunks(y);
    }

    // ==============================================
    // setBlockWithMeta
    // ==============================================

    @Inject(
        method = "func_150807_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;setExtBlockMetadata" + "(IIII)V",
            shift = At.Shift.AFTER))
    private void onEBSSet_setBlockWithMeta_setOpacity(int x, int y, int z, Block block, int meta,
        CallbackInfoReturnable<Boolean> cir) {
        if (!isColumn) {
            return;
        }
        this.isModified = true;
        if (((IColumn) this).getCube(blockToCube(y))
            .isSurfaceTracked()) {
            opacityIndex.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
            getWorldObj().getLightingManager()
                .onHeightUpdate(x, y, z);
        } else {
            stagingHeightMap.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
        }
    }

    @Redirect(
        method = "func_150807_a",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage setBlockWithMeta_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    @Definition(
        id = "storageArrays",
        field = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;")
    @Expression("this.storageArrays[? >> 4] = ?")
    @WrapOperation(method = "func_150807_a", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void setBlockWithMeta_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index,
        ExtendedBlockStorage value, Operation<Void> original) {
        setEBS_CubicChunks(index, value);
    }

    @Redirect(
        method = "func_150807_a",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetBlockWithMeta_Field(Chunk chunk, boolean isModifiedIn, int x, int y, int z,
        Block block, int meta) {
        if (isColumn) {
            getWorldObj().getCubeFromBlockCoords(x, y, z)
                .markDirty();
        } else {
            isModified = isModifiedIn;
        }
    }

    // ==============================================
    // setBlockMetadata
    // ==============================================

    @Inject(
        method = "setBlockMetadata",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;setExtBlockMetadata" + "(IIII)V",
            shift = At.Shift.AFTER))
    private void onEBSSet_setBlockMetadata_setOpacity(int x, int y, int z, int meta,
        CallbackInfoReturnable<Boolean> cir) {
        if (!isColumn) {
            return;
        }
        Block block = getEBS_CubicChunks(blockToCube(y))
            .getBlockByExtId(Coords.blockToLocal(x), Coords.blockToLocal(y), Coords.blockToLocal(z)); // TODO WATCH
        this.isModified = true;
        if (((IColumn) this).getCube(blockToCube(y))
            .isSurfaceTracked()) {
            opacityIndex.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
            getWorldObj().getLightingManager()
                .onHeightUpdate(x, y, z);
        } else {
            stagingHeightMap.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
        }
    }

    @Redirect(
        method = "setBlockMetadata",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage setBlockMetadata_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    // TODO Not sure how these got here.

    // @Redirect(method = "setBlockMetadata", at = @At(
    // value = "FIELD",
    // target =
    // "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
    // args = "array=set"
    // ))
    // private void setBlockMetadata_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index,
    // ExtendedBlockStorage val) {
    // setEBS_CubicChunks(index, val);
    // }
    //
    // @Inject(method = "setBlockMetadata", at = @At(
    // value = "FIELD",
    // target =
    // "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
    // args = "array=set"
    // ), cancellable = true)
    // private void setBlockMetadata_CubicChunks_EBSSetInject(int x, int y, int z, Block block, int meta,
    // CallbackInfoReturnable<Boolean> cir) {
    // if (isColumn && getWorldObj().getCubeCache().getLoadedCube(CubePos.fromBlockCoords(x, y, z)) == null) {
    // cir.setReturnValue(null);
    // }
    // }

    @Redirect(
        method = "setBlockMetadata",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetBlockMetadata_Field(Chunk chunk, boolean isModifiedIn, int x, int y, int z,
        int meta) {
        if (isColumn) {
            getWorldObj().getCubeFromBlockCoords(x, y, z)
                .markDirty();
        } else {
            isModified = isModifiedIn;
        }
    }

    // ==============================================
    // getLightFor
    // ==============================================

    @Inject(method = "getSavedLightValue", at = @At("HEAD"), cancellable = true)
    private void replacedGetSavedLightValueForCC(EnumSkyBlock type, int x, int y, int z,
        CallbackInfoReturnable<Integer> cir) {
        if (!isColumn) {
            return;
        }
        ((ICubicWorldInternal) worldObj).getLightingManager()
            .onGetLight(type, x, y, z);
        cir.setReturnValue(((Cube) ((IColumn) this).getCube(blockToCube(y))).getCachedLightFor(type, x, y, z));
    }

    @Nullable
    @Redirect(
        method = "getSavedLightValue",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage getLightFor_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    // LIGHTING HOOKS
    // ==============================================

    @Inject(method = "getBlockLightValue", at = @At("HEAD"))
    private void onGetBlockLightValue(int x, int y, int z, int amount, CallbackInfoReturnable<Integer> cir) {
        if (!isColumn) {
            return;
        }
        getWorldObj().getLightingManager()
            .onGetLightSubtracted(x, y, z);
    }

    // ==============================================
    // setLightFor
    // ==============================================

    @Redirect(
        method = "setLightValue",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    @Nullable
    private ExtendedBlockStorage setLightValue_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        ExtendedBlockStorage ebs = getEBS_CubicChunks(index);

        return ebs == null ? EmptyEBS.INSTANCE : ebs;
    }

    @Definition(
        id = "storageArrays",
        field = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;")
    @Expression("this.storageArrays[? >> 4] = ?")
    @WrapOperation(method = "setLightValue", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void setLightValue_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index,
        ExtendedBlockStorage value, Operation<Void> original) {
        setEBS_CubicChunks(index, value);
    }

    @Redirect(
        method = "setLightValue",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetLightValue_Field(Chunk chunk, boolean isModifiedIn, EnumSkyBlock type, int x,
        int y, int z, int value) {
        if (isColumn) {
            getWorldObj().getCubeFromBlockCoords(x, y, z)
                .markDirty();
        } else {
            isModified = isModifiedIn;
        }
    }

    // ==============================================
    // getBlockLightValue
    // ==============================================

    @Nullable
    @Redirect(
        method = "getBlockLightValue",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage getBlockLightValue_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array,
        int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    // addEntity
    // ==============================================

    @ModifyConstant(
        method = "addEntity",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0),
        require = 1)
    private int addEntity_getMinY(int zero) {
        return blockToCube(getWorldObj().getMinHeight());
    }

    @Redirect(
        method = "addEntity",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 2)
    private int addEntity_getMaxHeight(List<Entity>[] entityLists) {
        return isColumn ? blockToCube(getWorldObj().getMaxHeight())
            : (entityLists.length - blockToCube(getWorldObj().getMinHeight()));
    }

    @Redirect(
        method = "addEntity",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 1)
    private List<Entity> addEntity_getEntityList(List<Entity>[] entityLists, int idx, Entity entity) {
        if (!isColumn) {
            return entityLists[idx - blockToCube(getWorldObj().getMinHeight())];
        } else if (cachedCube != null && cachedCube.getY() == idx) {
            cachedCube.getEntityContainer()
                .add(entity);
            return null;
        } else {
            getWorldObj().getCubeCache()
                .getCube(this.xPosition, idx, this.zPosition)
                .getEntityContainer()
                .add(entity);
            return null;
        }
    }

    @Redirect(
        method = "addEntity",
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
        require = 1)
    private boolean addEntity_getEntityList(List<Object> obj, Object entity) {
        if (!isColumn) {
            return obj.add(entity);
        }
        assert obj == null;
        return true; // ignored
    }

    // ==============================================
    // removeEntityAtIndex
    // ==============================================

    @ModifyConstant(
        method = "removeEntityAtIndex",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0),
        require = 2,
        slice = @Slice(
            from = @At("HEAD"),
            to = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z")))
    private int removeEntityAtIndex_getMinY(int zero) {
        return blockToCube(getWorldObj().getMinHeight());
    }

    @Redirect(
        method = "removeEntityAtIndex",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 2)
    private int removeEntityAtIndex_getMaxHeight(List<Entity>[] entityLists) {
        return isColumn ? blockToCube(getWorldObj().getMaxHeight())
            : (entityLists.length - blockToCube(getWorldObj().getMinHeight()));
    }

    @Redirect(
        method = "removeEntityAtIndex",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 1)
    private List<Entity> removeEntityAtIndex_getEntityList(List<Entity>[] entityLists, int idx, Entity entity,
        int index) {
        if (!isColumn) {
            return entityLists[idx - blockToCube(getWorldObj().getMinHeight())];
        } else if (cachedCube != null && cachedCube.getY() == idx) {
            cachedCube.getEntityContainer()
                .remove(entity);
            return null;
        } else {
            getWorldObj().getCubeCache()
                .getCube(this.xPosition, idx, this.zPosition)
                .getEntityContainer()
                .remove(entity);
            return null;
        }
    }

    @Redirect(
        method = "removeEntityAtIndex",
        at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z"),
        require = 1)
    private boolean removeEntityAtIndex_getEntityList(List<Object> obj, Object entity) {
        if (!isColumn) {
            return obj.remove(entity);
        }
        assert obj == null;
        return true; // ignored
    }

    // ==============================================
    // addTileEntity
    // ==============================================

    @Redirect(
        method = "addTileEntity(Lnet/minecraft/tileentity/TileEntity;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    private boolean addTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, TileEntity te) {
        if (!isColumn) {
            return isChunkLoaded;
        }
        ICube cube = ((IColumn) this).getLoadedCube(blockToCube(te.yCoord));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    // removeTileEntity
    // ==============================================

    @Redirect(
        method = "removeTileEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    private boolean removeTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, int x, int y, int z) {
        if (!isColumn) {
            return isChunkLoaded;
        }
        ICube cube = ((IColumn) this).getLoadedCube(blockToCube(y));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    // onLoad
    // ==============================================

    @Inject(method = "onChunkLoad", at = @At("HEAD"), cancellable = true)
    private void onChunkLoad_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();
        this.isChunkLoaded = true;
        MinecraftForge.EVENT_BUS.post(new Load((Chunk) (Object) this));
    }

    // ==============================================
    // onUnload
    // ==============================================

    @Inject(method = "onChunkUnload", at = @At("HEAD"), cancellable = true)
    private void onChunkUnload_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();
        this.isChunkLoaded = false;

        for (Cube cube : cubeMap) {
            cube.onCubeUnload();
        }
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkEvent.Unload((Chunk) (Object) this));
    }

    // ==============================================
    // getEntitiesWithinAABBForEntity
    // ==============================================

    @Inject(method = "getEntitiesWithinAABBForEntity", at = @At("HEAD"), cancellable = true)
    private void getEntitiesWithinAABBForEntity_CubicChunks(@Nullable Entity entityIn, AxisAlignedBB aabb,
        List<Entity> listToFill, IEntitySelector filter, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();

        int minY = MathHelper.floor_double((aabb.minY - World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        int maxY = MathHelper.floor_double((aabb.maxY + World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        minY = MathHelper
            .clamp_int(minY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));
        maxY = MathHelper
            .clamp_int(maxY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));

        for (Cube cube : cubeMap.cubes(minY, maxY)) {
            if (cube.getEntityContainer()
                .isEmpty()) {
                continue;
            }
            for (Entity entity : cube.getEntityContainer()) {
                if (!entity.boundingBox.intersectsWith(aabb) || entity == entityIn) {
                    continue;
                }
                if (filter == null || filter.isEntityApplicable(entity)) {
                    listToFill.add(entity);
                }

                Entity[] parts = entity.getParts();

                if (parts != null) {
                    for (Entity part : parts) {
                        if (part != entityIn && part.boundingBox.intersectsWith(aabb)
                            && (filter == null || filter.isEntityApplicable(entity))) {
                            listToFill.add(part);
                        }
                    }
                }
            }
        }
    }

    // ==============================================
    // getEntitiesOfTypeWithinAABB
    // ==============================================

    @Inject(method = "getEntitiesOfTypeWithinAAAB", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void getEntitiesOfTypeWithinAAAB_CubicChunks(Class<? extends T> entityClass,
        AxisAlignedBB aabb, List<T> listToFill, IEntitySelector filter, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();

        int minY = MathHelper.floor_double((aabb.minY - World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        int maxY = MathHelper.floor_double((aabb.maxY + World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        minY = MathHelper
            .clamp_int(minY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));
        maxY = MathHelper
            .clamp_int(maxY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));

        for (Cube cube : cubeMap.cubes(minY, maxY)) {
            for (Entity entity : cube.getEntityContainer()) {
                if (entityClass.isAssignableFrom(entity.getClass()) && entity.boundingBox.intersectsWith(aabb)
                    && (filter == null || filter.isEntityApplicable(entity))) {
                    listToFill.add((T) entity);
                }
            }
        }
    }

    // public boolean needsSaving(boolean p_76601_1_) - TODO: needsSaving

    // ==============================================
    // getPrecipitationHeight
    // ==============================================

    // TODO WATCH THIS ONE
    @Inject(method = "getPrecipitationHeight", at = @At(value = "HEAD"), cancellable = true)
    private void getPrecipitationHeight_CubicChunks_Replace(int x, int z, CallbackInfoReturnable<Integer> cbi) {
        if (isColumn) {
            // TODO: precipitationHeightMap
            int ret = ((IColumn) this).getHeightValue(blockToLocal(x), 0, blockToLocal(z));
            cbi.setReturnValue(ret);
        }
    }

    // ==============================================
    // onTick
    // ==============================================

    // TODO: check if we are out of time earlier?
    @Inject(method = "func_150804_b", at = @At(value = "RETURN"))
    private void onTick_CubicChunks_TickCubes(boolean tryToTickFaster, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        this.field_150815_m = true;
        this.isLightPopulated = true;
        // do nothing, we tick cubes directly
    }

    // ==============================================
    // isEmptyBetween
    // ==============================================

    /**
     * @param startY bottom Y coordinate
     * @param endY   top Y coordinate
     * @return true if the specified height range is empty
     * @author Barteks2x
     * @reason original function limited to storage arrays.
     */
    @Overwrite
    public boolean getAreLevelsEmpty(int startY, int endY) {
        if (startY < getWorldObj().getMinHeight()) {
            startY = getWorldObj().getMinHeight();
        }

        if (endY >= getWorldObj().getMaxHeight()) {
            endY = getWorldObj().getMaxHeight() - 1;
        }

        for (int i = startY; i <= endY; i += Cube.SIZE) {
            ExtendedBlockStorage extendedblockstorage = getEBS_CubicChunks(blockToCube(i));

            if (extendedblockstorage != null && !extendedblockstorage.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    // ==============================================
    // setStorageArrays
    // ==============================================

    @Inject(method = "setStorageArrays", at = @At(value = "HEAD"))
    private void setStorageArrays_CubicChunks_NotSupported(ExtendedBlockStorage[] newStorageArrays, CallbackInfo cbi) {
        if (isColumn) {
            throw new UnsupportedOperationException("setting storage arrays it not supported with cubic chunks");
        }
    }

    // ==============================================
    // checkLight
    // ==============================================

    @Inject(method = "func_150809_p()V", at = @At(value = "HEAD"), cancellable = true)
    private void checkLight_CubicChunks_NotSupported(CallbackInfo cbi) {
        if (isColumn) {
            // we use FirstLightProcessor instead
            cbi.cancel();
        }
    }

    // private void setSkylightUpdated() - noone should use it

    // private void checkLightSide(EnumFacing facing) - noone should use it

    // private boolean checkLight(int x, int zPosition) - TODO: checkLight

    // ==============================================
    // removeInvalidTileEntity
    // ==============================================

    @Redirect(
        method = "removeInvalidTileEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    private boolean removeInvalidTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, int x, int y, int z) {
        if (!isColumn) {
            return isChunkLoaded;
        }
        ICube cube = ((IColumn) this).getLoadedCube(blockToCube(y));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    // enqueueRelightChecks
    // ==============================================

    @Inject(method = "enqueueRelightChecks", at = @At(value = "HEAD"), cancellable = true)
    private void enqueueRelightChecks_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;

        }
        cbi.cancel();
        if (CubicChunksConfig.relightChecksPerTickPerColumn > 0
            && (!worldObj.isRemote || CubicChunksConfig.doClientLightFixes)) {
            cubeMap.enqueueRelightChecks();
        }
    }
}
