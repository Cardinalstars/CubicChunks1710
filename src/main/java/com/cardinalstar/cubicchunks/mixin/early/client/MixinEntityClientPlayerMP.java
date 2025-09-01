package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityClientPlayerMP.class)
public class MixinEntityClientPlayerMP {

    // Even when bottom chunk is unloaded still update player
    @Redirect(method = "onUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;blockExists(III)Z"))
    private boolean alwaysUpdate(World instance, int p_72899_1_, int p_72899_2_, int p_72899_3_) {
        return true;
    }
}
