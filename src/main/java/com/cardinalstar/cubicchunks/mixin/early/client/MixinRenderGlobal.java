package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Shadow
    private int renderChunksTall;

    @Unique
    int RenderDistanceCubesVertical = -1;

    @Inject(method = "markRenderersForNewPosition", at = @At("HEAD"))
    private void calculateYRenderSpans(int entityX, int entityY, int entityZ, CallbackInfo ci,
        @Share("yRenderSpan") LocalIntRef yRenderSpanRef, @Share("halfYRenderSpan") LocalIntRef halfYRenderSpanRef) {
        yRenderSpanRef.set(this.renderChunksTall * 16);
        halfYRenderSpanRef.set(yRenderSpanRef.get() / 2);
    }

    @ModifyVariable(method = "markRenderersForNewPosition", at = @At("STORE"), index = 13)
    private int modifyYPositionForGlobalRendering(int blockY, @Share("yRenderSpan") LocalIntRef yRenderSpanRef,
        @Share("halfYRenderSpan") LocalIntRef halfYRenderSpanRef, @Local(argsOnly = true, index = 2) int entityY) {
        int tempOffsetY = blockY + halfYRenderSpanRef.get() - entityY;

        if (tempOffsetY < 0) {
            tempOffsetY -= yRenderSpanRef.get() - 1;
        }

        tempOffsetY /= yRenderSpanRef.get();

        blockY -= tempOffsetY * yRenderSpanRef.get();
        return blockY;
    }

    @Definition(
        id = "renderDistanceChunks",
        field = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I")
    @Definition(
        id = "gameSettings",
        field = "Lnet/minecraft/client/Minecraft;gameSettings:Lnet/minecraft/client/settings/GameSettings;")
    @Definition(id = "mc", field = "Lnet/minecraft/client/renderer/RenderGlobal;mc:Lnet/minecraft/client/Minecraft;")
    @Expression("this.mc.gameSettings.renderDistanceChunks")
    @Inject(method = "loadRenderers", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void setVerticalRenderCubesDistance(CallbackInfo ci) {
        this.RenderDistanceCubesVertical = CubicChunksConfig.verticalCubeLoadDistance;
    }

    @ModifyConstant(method = "loadRenderers", constant = @Constant(intValue = 16, ordinal = 0))
    private int modifyRenderHeight(int Original) {
        return this.RenderDistanceCubesVertical * 2 + 1;
    }

    @Definition(
        id = "thisRenderDistanceChunks",
        field = "Lnet/minecraft/client/renderer/RenderGlobal;renderDistanceChunks:I")
    @Definition(
        id = "renderDistanceChunks",
        field = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I")
    @Definition(
        id = "gameSettings",
        field = "Lnet/minecraft/client/Minecraft;gameSettings:Lnet/minecraft/client/settings/GameSettings;")
    @Definition(id = "mc", field = "Lnet/minecraft/client/renderer/RenderGlobal;mc:Lnet/minecraft/client/Minecraft;")
    @Expression("this.mc.gameSettings.renderDistanceChunks != this.thisRenderDistanceChunks")
    @ModifyExpressionValue(method = "sortAndRender", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean checkForReloadRenderer(boolean original) {
        return original || (this.RenderDistanceCubesVertical != CubicChunksConfig.verticalCubeLoadDistance);
    }

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 16))
    private int modifyRenderheight(int Original) {
        return 33 * 2; // TODO STORE THIS MAX RENDER DISTANCE SOMEWHERE
    }
}
