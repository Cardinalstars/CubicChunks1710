package com.cardinalstar.cubicchunks.mixin.early.common.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.MapGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;

@Mixin(ChunkProviderGenerate.class)
public class MixinChunkProviderGenerate {

    @Definition(
        id = "caveGenerator", field = "Lnet/minecraft/world/gen/ChunkProviderGenerate;caveGenerator:Lnet/minecraft/world/gen/MapGenBase;")
    @Definition(id = "func_151539_a", method = "Lnet/minecraft/world/gen/MapGenBase;func_151539_a(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/World;II[Lnet/minecraft/block/Block;)V")
    @Expression("this.caveGenerator.func_151539_a(?, ?, ?, ?, ?)")
    @Redirect(method = "provideChunk", at = @At("MIXINEXTRAS:EXPRESSION"))
    public void noopCaveGen(MapGenBase instance, IChunkProvider i2, World k1, int j1, int i, Block[] p_151539_1_) {

    }
}
