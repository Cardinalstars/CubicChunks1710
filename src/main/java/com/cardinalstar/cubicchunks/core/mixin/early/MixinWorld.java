package com.cardinalstar.cubicchunks.core.mixin.early;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.core.mixin.api.ICubicWorldSettings;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(World.class)
public class MixinWorld implements ICubicWorld
{

    @Shadow
    protected WorldInfo worldInfo;
    protected int minHeight = 0, maxHeight = 256, fakedMaxHeight = 0;
    private int minGenerationHeight = 0, maxGenerationHeight = 256;

    @Override
    public boolean isCubicWorld() {
        return true;
    }

    @Override
    public int getMaxGenerationHeight() {
        return this.maxGenerationHeight;
    }

    protected void initCubicWorld(int maxHeight, int minHeight, int minGenerationHeight, int maxGenerationHeight) {
        ((ICubicWorldSettings) worldInfo).setCubic(true);
        // Set the world height boundaries to their highest and lowest values respectively
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.fakedMaxHeight = this.maxHeight;

        this.minGenerationHeight = minGenerationHeight;
        this.maxGenerationHeight = maxGenerationHeight;
    }
}
