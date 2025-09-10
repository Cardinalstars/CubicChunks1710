package com.cardinalstar.cubicchunks.mixin.early.common.worldgen;

import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraft.world.gen.feature.WorldGenCanopyTree;
import net.minecraft.world.gen.feature.WorldGenTaiga1;
import net.minecraft.world.gen.feature.WorldGenTaiga2;
import net.minecraft.world.gen.feature.WorldGenTrees;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin({WorldGenTrees.class, WorldGenTaiga2.class, WorldGenTaiga1.class, WorldGenBigMushroom.class, WorldGenCanopyTree.class})
public class MixinWorldGen_HeightChecks {

    @Definition(id = "y", local = @Local(argsOnly = true, type = int.class, ordinal = 1))
    @Expression("y >= 1")
    @Expression("y + ? + 1 <= 256")
    @Expression("? >= 0")
    @Expression("? < 256")
    @Expression("y < 256 - ? - 1")
    @WrapOperation(method = "generate", at = @At("MIXINEXTRAS:EXPRESSION"))
    public boolean noopHeightCheck(int left, int right, Operation<Boolean> original) {
        return true;
    }
}
