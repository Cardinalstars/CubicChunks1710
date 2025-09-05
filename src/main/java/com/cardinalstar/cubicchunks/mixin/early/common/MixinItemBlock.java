package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.item.ItemBlock;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.sugar.Local;

@Mixin(ItemBlock.class)
public class MixinItemBlock {

    @ModifyConstant(method = "onItemUse", constant = @Constant(intValue = 255), require = 1)
    private int allowPlacingOnY255(int constant, @Local(argsOnly = true) World world) {
        return world.getHeight();
    }
}
