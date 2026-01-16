package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.network.play.server.S23PacketBlockChange;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S23PacketBlockChange.class)
public interface AccessorS23PacketBlockChange {

    @Accessor("field_148887_a")
    int getX();

    @Accessor("field_148885_b")
    int getY();

    @Accessor("field_148886_c")
    int getZ();

    @Accessor("field_148887_a")
    void setX(int x);

    @Accessor("field_148885_b")
    void setY(int y);

    @Accessor("field_148886_c")
    void setZ(int z);
}
