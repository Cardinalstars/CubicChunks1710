package com.cardinalstar.cubicchunks.core.mixin.early;

import com.cardinalstar.cubicchunks.world.ICubeGenerator;
import com.cardinalstar.cubicchunks.world.ICubicWorldProvider;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(WorldProvider.class)
public class MixinWorldProvider implements ICubicWorldProvider
{

    @Shadow
    public World worldObj;

    @Nullable
    @Override
    public ICubeGenerator createCubeGenerator() {
        return null;
    }

    @Override
    public int getOriginalActualHeight() {
        return 0;
    }

    @Override
    public World getWorld() {
        return worldObj;
    }
}
