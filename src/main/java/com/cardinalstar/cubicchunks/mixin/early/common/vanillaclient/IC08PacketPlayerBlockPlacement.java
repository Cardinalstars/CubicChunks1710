package com.cardinalstar.cubicchunks.mixin.early.common.vanillaclient;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C08PacketPlayerBlockPlacement.class)
public interface IC08PacketPlayerBlockPlacement {

    @Accessor("field_149583_a")
    void setX(int x);

    @Accessor("field_149581_b")
    void setY(int y);

    @Accessor("field_149582_c")
    void setZ(int z);
}
