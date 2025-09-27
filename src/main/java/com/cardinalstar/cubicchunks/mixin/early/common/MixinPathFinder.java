package com.cardinalstar.cubicchunks.mixin.early.common;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PathFinder.class)
public class MixinPathFinder
{
    @Definition(id = "p_75858_3_", local = @Local(name = "p_75858_3_"))
    @Expression("p_75858_3_ > 0")
    @WrapOperation(method = "getSafePoint", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean redirectLessThan0Comparison(int yPosition, int zero, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) Entity entity)
    {
        return yPosition > ((ICubicWorld)entity.worldObj).getMinHeight();
    }

    @Definition(id = "getBlock", method = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;")
    @Expression("?.getBlock(?, ?, ?)")
    @Redirect(method = "func_82565_a", at = @At("MIXINEXTRAS:EXPRESSION"))
    private static Block CheckWorldLoaded(World world, int x, int y, int z)
    {
        if (((ICubicWorld) world).cubeExists(x, y, z))
        {
            return world.getBlock(x, y, z);
        }
        return Blocks.air;
    }
}
