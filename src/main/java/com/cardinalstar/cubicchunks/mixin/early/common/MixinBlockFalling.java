package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.BlockFalling;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(BlockFalling.class)
public class MixinBlockFalling {

    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y >= 0")
    @WrapOperation(method = "func_149830_m", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean blockFallingMinHeighFix1(int left, int right, Operation<Boolean> original, World world) {
        return left >= ((IMinMaxHeight) world).getMinHeight();
    }

    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y > 0")
    @WrapOperation(method = "func_149830_m", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean blockFallingMinHeighFix2(int left, int right, Operation<Boolean> original, World world) {
        return left >= ((IMinMaxHeight) world).getMinHeight();
    }
}
