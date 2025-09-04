package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @ModifyConstant(method = "moveEntityWithHeading", constant = @Constant(intValue = 0))
    private int fixBlockExists(int constant) {
        return (int) posY;
    }
}
