package com.cardinalstar.cubicchunks.mixin.early.common.worldgen;

import java.util.Random;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.cardinalstar.cubicchunks.api.world.Precalculable;
import com.cardinalstar.cubicchunks.world.worldgen.vanilla.PrecalcedVanillaOctaves;
import com.cardinalstar.cubicchunks.world.worldgen.vanilla.PrecalculableNoise;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    @WrapOperation(
        method = "<init>", at = @At(
        value = "NEW", target = "(Ljava/util/Random;I)Lnet/minecraft/world/gen/NoiseGeneratorOctaves;"))
    public NoiseGeneratorOctaves usePregenerateNoise(Random random, int octaves, Operation<NoiseGeneratorOctaves> original) {
        return new PrecalcedVanillaOctaves(original.call(random, octaves));
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
            } else {
                return;
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
