package com.cardinalstar.cubicchunks.mixin.early.common.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cardinalstar.cubicchunks.api.world.Precalculable;
import com.cardinalstar.cubicchunks.world.worldgen.vanilla.PrecalculableNoise;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@Mixin(ChunkProviderGenerate.class)
public class MixinChunkProviderGenerate implements Precalculable {

    @Shadow
    private NoiseGeneratorOctaves field_147431_j;

    @Shadow
    private NoiseGeneratorOctaves field_147432_k;

    @Shadow
    private NoiseGeneratorOctaves field_147429_l;

    @Shadow
    public NoiseGeneratorOctaves noiseGen6;

    // @Redirect(method = "<init>", at = @At(value = "NEW", target =
    // "(Ljava/util/Random;I)Lnet/minecraft/world/gen/NoiseGeneratorOctaves;"))
    // public NoiseGeneratorOctaves usePregenerateNoise(Random random, int octaves) {
    // return new PrecalcedVanillaOctaves(random, octaves);
    // }

    @Definition(
        id = "caveGenerator",
        field = "Lnet/minecraft/world/gen/ChunkProviderGenerate;caveGenerator:Lnet/minecraft/world/gen/MapGenBase;")
    @Definition(
        id = "func_151539_a",
        method = "Lnet/minecraft/world/gen/MapGenBase;func_151539_a(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/World;II[Lnet/minecraft/block/Block;)V")
    @Expression("this.caveGenerator.func_151539_a(?, ?, ?, ?, ?)")
    @Redirect(method = "provideChunk", at = @At("MIXINEXTRAS:EXPRESSION"))
    public void noopCaveGen(MapGenBase instance, IChunkProvider i2, World k1, int j1, int i, Block[] p_151539_1_) {

    }

    @Unique
    private final LongArrayFIFOQueue chunkGenFIFO = new LongArrayFIFOQueue();
    @Unique
    private final LongOpenHashSet chunkGenDebounce = new LongOpenHashSet();

    @Override
    public void precalculate(int cubeX, int cubeY, int cubeZ) {
        long coord = ChunkCoordIntPair.chunkXZ2Int(cubeX, cubeZ);

        synchronized (this) {
            if (chunkGenDebounce.add(coord)) {
                chunkGenFIFO.enqueue(coord);

                while (chunkGenFIFO.size() > 1024) {
                    long chunk = chunkGenFIFO.dequeueLong();
                    chunkGenDebounce.remove(chunk);
                }
            }
        }

        int noiseX = cubeX * 4;
        int noiseZ = cubeZ * 4;

        if (this.field_147431_j instanceof PrecalculableNoise precalc) precalc.precalculate(noiseX, 0, noiseZ);
        if (this.field_147432_k instanceof PrecalculableNoise precalc) precalc.precalculate(noiseX, 0, noiseZ);
        if (this.field_147429_l instanceof PrecalculableNoise precalc) precalc.precalculate(noiseX, 0, noiseZ);
        if (this.noiseGen6 instanceof PrecalculableNoise precalc) precalc.precalculate(noiseX, 10, noiseZ);
    }
}
