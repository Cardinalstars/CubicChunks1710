package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cardinalstar.cubicchunks.world.ICubicWorld;

@Mixin(EntityMinecart.class)
public abstract class MixinEntityMinecart extends Entity {

    public MixinEntityMinecart(World worldIn) {
        super(worldIn);
    }

    @ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = -64.0D))
    double minecartKillFix(double orginalConstant) {
        return ((ICubicWorld) worldObj).getMinHeight() + orginalConstant;
    }
}
