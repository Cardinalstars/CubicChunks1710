package com.cardinalstar.cubicchunks.mixin.early.server;

import com.cardinalstar.cubicchunks.world.type.VanillaCubicWorldType;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;


@Mixin(DedicatedServer.class)
public class MixinDedicatedServer_DefaultLevelType
{
    @ModifyConstant(method = "startServer", constant = @Constant(stringValue = "DEFAULT"))
    String modifyDefaultLevel(String original)
    {
        return VanillaCubicWorldType.vanillaCubicLevelString;
    }
}
