package com.cardinalstar.cubicchunks.mixin.early.common;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;

import javax.annotation.Nullable;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.column.EmptyEBS;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;

@Mixin(value = Chunk.class, priority = 999)
public abstract class MixinChunk_Lighting {

    @Shadow
    @Final
    private World worldObj;

    @Shadow
    private boolean isModified;

    // ==============================================
    // getLightFor
    // ==============================================

    @Inject(method = "getSavedLightValue", at = @At("HEAD"), cancellable = true)
    private void replacedGetSavedLightValueForCC(EnumSkyBlock type, int x, int y, int z,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (!((IColumnInternal) this).isColumn()) {
            return;
        }
        if (((ICubicWorldInternal) worldObj).getLightingManager() != null) {
            ((ICubicWorldInternal) worldObj).getLightingManager()
                .onGetLight(type, x, y, z);
        }
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
        return ((IColumnInternal) this).getEBS_CubicChunks(index);
    }

    // ==============================================
    // LIGHTING HOOKS
    // ==============================================

    @Inject(method = "getBlockLightValue", at = @At("HEAD"))
    private void onGetBlockLightValue(int x, int y, int z, int amount, CallbackInfoReturnable<Integer> cir) {
        if (!((IColumnInternal) this).isColumn()) {
            return;
        }
        ICubicWorldInternal world = (ICubicWorldInternal) worldObj;
        if (world.getLightingManager() != null) {
            world.getLightingManager()
                .onGetLightSubtracted(x, y, z);
        }
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
        ExtendedBlockStorage ebs = ((IColumnInternal) this).getEBS_CubicChunks(index);

        return ebs == null ? EmptyEBS.INSTANCE : ebs;
    }

    @Definition(
        id = "storageArrays",
        field = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;")
    @Expression("this.storageArrays[? >> 4] = ?")
    @WrapOperation(method = "setLightValue", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void setLightValue_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index,
                                                          ExtendedBlockStorage value, Operation<Void> original) {
        ((IColumnInternal) this).setEBS_CubicChunks(index, value);
    }

    @Redirect(
        method = "setLightValue",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetLightValue_Field(Chunk chunk, boolean isModifiedIn, EnumSkyBlock type, int x,
                                                      int y, int z, int value) {
        if (((IColumnInternal) this).isColumn()) {
            ICube cube = ((IColumn) this).getCube(blockToCube(y));
            cube.markDirty();
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
        return ((IColumnInternal) this).getEBS_CubicChunks(index);
    }
}
