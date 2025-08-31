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

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.entity.ICubicEntityTracker;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry implements ICubicEntityTracker.Entry {

    @Shadow
    public int blocksDistanceThreshold;
    @Shadow
    public int lastScaledYPosition;

    @Shadow
    public Entity myEntity;
    @Unique
    private int cubic_chunks$maxVertRange;

    @ModifyExpressionValue(
        method = "tryStartWachingThis",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/EntityTrackerEntry;blocksDistanceThreshold:I",
            ordinal = 0))
    private int modifyThreshold(int original, @Local(argsOnly = true) EntityPlayerMP player,
        @Local(ordinal = 0) double d0, @Local(ordinal = 1) double d1) {
        if (d0 >= (double) (-original) && ((ICubicWorld) player.worldObj).isCubicWorld()) {
            int rangeY = Math.min(this.blocksDistanceThreshold, this.cubic_chunks$maxVertRange);
            double dy = player.posY - this.lastScaledYPosition / 32.0D;
            if (dy < -rangeY || dy > rangeY) {
                return -(int) Math.floor(d0) - 1;
            }
        }
        return original;
    }

    @Redirect(
        method = "tryStartWachingThis",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityTrackerEntry;blocksDistanceThreshold:I"))
    private int redirectThreshold(EntityTrackerEntry entry, EntityPlayerMP player) {
        int original = entry.blocksDistanceThreshold;

        // do your cubic-world-aware check
        if (((ICubicWorld) player.worldObj).isCubicWorld()) {
            int rangeY = Math.min(original, this.cubic_chunks$maxVertRange);
            double dy = player.posY - this.lastScaledYPosition / 32.0D;

            if (dy < -rangeY || dy > rangeY) {
                // hack: return an exaggerated threshold so the vanilla checks fail early
                return -(original + 1);
            }
        }

        return original;
    }

    @Inject(method = "isPlayerWatchingThisChunk", cancellable = true, at = @At("HEAD"))
    private void isPlayerWatchingThisChunkCubic(EntityPlayerMP player, CallbackInfoReturnable<Boolean> cir) {
        // workaround for transfer between cubicchunks and non-cubic-chunks dimension
        if (((ICubicWorld) player.worldObj).isCubicWorld()) {
            boolean ret = ((CubicPlayerManager) player.getServerForPlayer()
                .getPlayerManager()).isPlayerWatchingCube(
                    player,
                    this.myEntity.chunkCoordX,
                    this.myEntity.chunkCoordY,
                    this.myEntity.chunkCoordZ);
            cir.setReturnValue(ret);
        }
    }

    @Override
    public void setMaxVertRange(int maxVertTrackingDistanceThreshold) {
        this.cubic_chunks$maxVertRange = maxVertTrackingDistanceThreshold;
    }
}
