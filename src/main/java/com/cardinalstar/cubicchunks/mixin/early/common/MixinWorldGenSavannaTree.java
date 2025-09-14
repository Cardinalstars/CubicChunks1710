package com.cardinalstar.cubicchunks.mixin.early.common;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenSavannaTree;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(WorldGenSavannaTree.class)
public class MixinWorldGenSavannaTree {

    @ModifyConstant(method = "generate", constant = @Constant(intValue = 256))
    int ModifyGenerateHeightMax(int original, World world, Random rand, int x, int y, int z) {
        return ((IMinMaxHeight) world).getMaxHeight();
    }

    @Expression("y >= 1")
    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @WrapOperation(method = "generate", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean ModifyGenerateHeightMin1(int left, int right, Operation<Boolean> original, World world, Random rand, int x,
        int y, int z) {

        return left >= ((IMinMaxHeight) world).getMinHeight();
    }

    @Expression("adjustedY >= 0")
    @Definition(id = "adjustedY", local = @Local(type = int.class, name = "i1"))
    @WrapOperation(method = "generate", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean ModifyGenerateHeightMin2(int left, int right, Operation<Boolean> original, World world, Random rand, int x,
        int y, int z) {

        return left >= ((IMinMaxHeight) world).getMinHeight();
    }
}
