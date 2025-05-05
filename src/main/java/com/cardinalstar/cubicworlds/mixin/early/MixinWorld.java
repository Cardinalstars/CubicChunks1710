package com.cardinalstar.cubicworlds.mixin.early;

import com.cardinalstar.cubicworlds.world.ICubicWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
public class MixinWorld implements ICubicWorld
{

    @Override
    public boolean isCubicWorld() {
        return true;
    }
}
