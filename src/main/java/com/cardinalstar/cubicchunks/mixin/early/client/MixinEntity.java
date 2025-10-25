package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    public double posY;

    @Definition(id = "worldObj", field = "Lnet/minecraft/entity/Entity;worldObj:Lnet/minecraft/world/World;")
    @Definition(id = "blockExists", method = "Lnet/minecraft/world/World;blockExists(III)Z")
    @Expression("this.worldObj.blockExists(?, ?, ?)")
    @WrapOperation(method = "getBrightnessForRender", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean atYInRenderEntityCheck(World instance, int x, int y, int z, Operation<Boolean> original) {
        return original.call(instance, x, MathHelper.floor_double(this.posY), z);
    }
}
