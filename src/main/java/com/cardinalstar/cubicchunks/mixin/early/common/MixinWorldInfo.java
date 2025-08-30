package com.cardinalstar.cubicchunks.mixin.early.common;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldSettings;

@ParametersAreNonnullByDefault
@Mixin(WorldInfo.class)
public class MixinWorldInfo implements ICubicWorldSettings {

    private boolean isCubic;

    // @Inject(method = "populateFromWorldSettings", at = @At("RETURN"))
    // private void onConstructWithSettings(WorldSettings settings, CallbackInfo cbi) {
    // this.isCubic = ((ICubicWorldSettings) (Object) settings).isCubic();
    // }

    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldInfo;)V", at = @At("RETURN"))
    private void onConstructWithSettings(WorldInfo other, CallbackInfo cbi) {
        this.isCubic = ((ICubicWorldSettings) other).isCubic();
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"))
    private void onConstructWithSettings(NBTTagCompound tag, CallbackInfo cbi) {
        this.isCubic = tag.getBoolean("isCubicWorld");
    }

    @Inject(method = "updateTagCompound", at = @At("RETURN"))
    private void onConstructWithSettings(NBTTagCompound nbt, NBTTagCompound playerNbt, CallbackInfo cbi) {
        nbt.setBoolean("isCubicWorld", isCubic);
    }

    @Override
    public boolean isCubic() {
        return isCubic;
    }

    @Override
    public void setCubic(boolean cubic) {
        this.isCubic = cubic;
    }
}
