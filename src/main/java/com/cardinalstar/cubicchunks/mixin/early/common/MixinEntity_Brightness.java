package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Entity.class)
public class MixinEntity_Brightness {

    @Shadow
    public double posY;

    @ModifyConstant(method = "getBrightness", constant = @Constant(intValue = 0))
    private int modifyY(int constant) {
        return MathHelper.floor_double(this.posY);
    }
}
