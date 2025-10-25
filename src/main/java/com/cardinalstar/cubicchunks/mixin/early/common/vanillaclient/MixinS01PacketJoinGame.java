package com.cardinalstar.cubicchunks.mixin.early.common.vanillaclient;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S01PacketJoinGame;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.network.ICubicJoinGamePacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mixin(S01PacketJoinGame.class)
public class MixinS01PacketJoinGame implements ICubicJoinGamePacket {

    @Unique
    private int minHeight;

    @Unique
    private int maxHeight;

    @Unique
    private int minGenerationHeight;

    @Unique
    private int maxGenerationHeight;

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMinHeight() {
        return this.minHeight;
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMaxHeight() {
        return this.maxHeight;
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMinGenerationHeight() {
        return minGenerationHeight;
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMaxGenerationHeight() {
        return maxGenerationHeight;
    }

    @Unique
    @Override
    public void InitCubicJoinGamePacket(int minHeight, int maxHeight, int minGenerationHeight,
        int maxGenerationHeight) {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.minGenerationHeight = minGenerationHeight;
        this.maxGenerationHeight = maxGenerationHeight;
    }

    @Inject(method = "writePacketData", at = @At("TAIL"))
    private void injectInfoIntoPacket(PacketBuffer data, CallbackInfo ci) {
        data.writeInt(this.minHeight);
        data.writeInt(this.maxHeight);
        data.writeInt(this.minGenerationHeight);
        data.writeInt(this.maxGenerationHeight);
    }

    @Inject(method = "readPacketData", at = @At("TAIL"))
    private void readInfoFromPacket(PacketBuffer data, CallbackInfo ci) {
        if (data.readableBytes() >= 16) {
            this.minHeight = data.readInt();
            this.maxHeight = data.readInt();
            this.minGenerationHeight = data.readInt();
            this.maxGenerationHeight = data.readInt();
        }
    }
}
