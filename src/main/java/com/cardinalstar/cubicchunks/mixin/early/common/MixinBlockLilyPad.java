package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.BlockLilyPad;
import net.minecraft.world.World;

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

@Mixin(BlockLilyPad.class)
public class MixinBlockLilyPad {

    @Expression("y >= 0")
    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @WrapOperation(method = "canBlockStay", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean canBlockStayHeightLimitFixesMin(int left, int right, Operation<Boolean> original, World world) {
        return left >= ((IMinMaxHeight) world).getMinHeight();
    }

    @ModifyConstant(method = "canBlockStay", constant = @Constant(intValue = 256))
    int canBlockStayHeightLimitFixesMin(int constant, World world) {
        return ((IMinMaxHeight) world).getMaxHeight();
    }
}
