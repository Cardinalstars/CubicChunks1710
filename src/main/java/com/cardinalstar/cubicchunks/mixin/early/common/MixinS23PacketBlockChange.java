package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S23PacketBlockChange;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import io.netty.buffer.ByteBuf;

@Mixin(S23PacketBlockChange.class)
public class MixinS23PacketBlockChange {

    @Shadow
    private int field_148885_b;

    @Redirect(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readUnsignedByte()S", ordinal = 0))
    private short noopReadUnsignedByte(PacketBuffer instance, @Local(argsOnly = true) PacketBuffer data) {
        return 0;
    }

    @Inject(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readInt()I", ordinal = 1))
    private void assignYValue(PacketBuffer data, CallbackInfo ci) {
        this.field_148885_b = data.readInt();
    }

    @Definition(
        id = "field_148885_b",
        field = "Lnet/minecraft/network/play/server/S23PacketBlockChange;field_148885_b:I")
    @Expression("?.?(this.field_148885_b)")
    @WrapOperation(method = "writePacketData", at = @At("MIXINEXTRAS:EXPRESSION"))
    private ByteBuf wrapWriteByte(PacketBuffer instance, int p_writeByte_1_, Operation<ByteBuf> original,
        @Local(argsOnly = true) PacketBuffer data) {
        data.writeInt(field_148885_b);
        return data;
    }
}
