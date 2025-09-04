package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.sugar.Local;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @ModifyVariable(method = "markRenderersForNewPosition", at = @At("STORE"), index = 13)
    private int setYPosition(int y, @Local(index = 2, argsOnly = true) int paramY,
        @Local(index = 4) int renderDistanceInBlocks) {
        int distance = 16 * 16; // 16 chunks with each 16 blocks

        int bottom = y + distance / 2 - paramY;

        if (bottom < 0) {
            bottom -= distance - 1;
        }

        bottom /= distance;
        y -= bottom * distance;
        return y;
    }
}
