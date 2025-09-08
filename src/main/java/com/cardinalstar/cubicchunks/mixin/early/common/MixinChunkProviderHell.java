package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderHell;
import net.minecraft.world.gen.structure.MapGenNetherBridge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkProviderHell.class)
public class MixinChunkProviderHell {

    @Shadow
    public MapGenNetherBridge genNetherBridge;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void setWorldObjs(World world, long seed, CallbackInfo ci) {
        ((MixinMapGenBase) this.genNetherBridge).setWorldObj(world);
    }

}
