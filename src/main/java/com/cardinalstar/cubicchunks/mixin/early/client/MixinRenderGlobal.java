package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    @Definition(
        id = "theWorld",
        field = "Lnet/minecraft/client/renderer/RenderGlobal;theWorld:Lnet/minecraft/client/multiplayer/WorldClient;")
    @Definition(id = "blockExists", method = "Lnet/minecraft/client/multiplayer/WorldClient;blockExists(III)Z")
    @Expression("this.theWorld.blockExists(?, ?, ?)")
    @WrapOperation(method = "renderEntities", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean atYInRenderEntityCheck(WorldClient instance, int x, int y, int z, Operation<Boolean> original,
        @Local(name = "entity", type = Entity.class) Entity entity) {
        return original.call(instance, x, MathHelper.floor_double(entity.posY), z);
    }
}
