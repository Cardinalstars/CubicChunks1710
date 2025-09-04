package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.world.NextTickListEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NextTickListEntry.class)
public class MixinNextTickListEntry {

    @Redirect(
        method = "equals",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;isEqualTo(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)Z"))
    public boolean cc$noopBlockEquals(Block left, Block right) {
        return true;
    }
}
