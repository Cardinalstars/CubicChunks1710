package com.cardinalstar.cubicchunks.mixin.early.common;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.cardinalstar.cubicchunks.api.worldtype.VanillaCubicWorldType;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(BiomeGenBase.class)
public class MixinBiomeGenBase {

    @Definition(id = "l1", local = @Local(name = "l1", type = int.class))
    @Definition(id = "rand", local = @Local(argsOnly = true, ordinal = 0, name = "p_150560_2_"), type = Random.class)
    @Definition(id = "nextInt", method = "Ljava/util/Random;nextInt(I)I")
    @Expression("l1 <= ?")
    @ModifyExpressionValue(method = "genBiomeTerrain", at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0))
    boolean RemoveBedrockForCubicGen(boolean original, @Local(argsOnly = true) World world) {
        if (world.getWorldInfo()
            .getTerrainType() == VanillaCubicWorldType.INSTANCE) {
            return false;
        }
        return original;
    }
}
