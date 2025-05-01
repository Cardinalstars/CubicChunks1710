package com.cardinalstar.cubicworlds.mixin.early;

import com.cardinalstar.cubicworlds.server.CubicAnvilChunkLoader;
import com.cardinalstar.cubicworlds.world.ICubicWorld;
import com.cardinalstar.cubicworlds.world.ICubicWorldProvider;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

@Mixin(AnvilSaveHandler.class)
public class MixinAnvilSaveHandler
{
    @Redirect(
        method = "getChunkLoader",
        at = @At(
            value = "NEW",
            target = "net/minecraft/world/chunk/storage/AnvilChunkLoader"))
    private AnvilChunkLoader getChunkLoader(File file, WorldProvider p_75763_1_)
    {
        ICubicWorld world = ((ICubicWorld) ((ICubicWorldProvider) p_75763_1_).getWorld());
        if (world.isCubicWorld()) {
            return new CubicAnvilChunkLoader(file);
        } else {
            return new AnvilChunkLoader(file);
        }
    }
}
