package com.cardinalstar.cubicchunks.mixin.early.client;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.ICubicJoinGamePacket;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient
{

    @Shadow
    private WorldClient clientWorldController;


    @Definition(id = "clientWorldController", field = "Lnet/minecraft/client/network/NetHandlerPlayClient;clientWorldController:Lnet/minecraft/client/multiplayer/WorldClient;")
    @Definition(id = "isRemote", field = "Lnet/minecraft/client/multiplayer/WorldClient;isRemote:Z")
    @Expression("this.clientWorldController.isRemote = true")
    @Inject(
        method = "handleJoinGame",
        at = @At("MIXINEXTRAS:EXPRESSION")
    )
    void initClientCubicWorld(S01PacketJoinGame packetIn, CallbackInfo ci)
    {
        if (packetIn instanceof ICubicJoinGamePacket cubicJoinGamePacket)
        {
            ((ICubicWorldInternal.Client) clientWorldController).initCubicWorldClient(
                new IntRange(cubicJoinGamePacket.cubicChunks$getMinHeight(), cubicJoinGamePacket.cubicChunks$getMaxHeight()),
                new IntRange(cubicJoinGamePacket.cubicChunks$getMinGenerationHeight(), cubicJoinGamePacket.cubicChunks$getMaxGenerationHeight()));
            // Update stale ViewFrustum/RenderChunk-related state, as it was previously set for non-CC world
            Minecraft.getMinecraft().renderGlobal.setWorldAndLoadRenderers(clientWorldController);
        }
    }
}
