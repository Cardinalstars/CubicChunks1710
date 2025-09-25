package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer {

    @Redirect(
        method = "processPlayerBlockPlacement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"))
    public int noopHeightChecks(MinecraftServer instance) {
        return Integer.MAX_VALUE;
    }

}
