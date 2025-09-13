/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.mixin.early.common;

import javax.annotation.ParametersAreNonnullByDefault;

import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldSettings;

@ParametersAreNonnullByDefault
@Mixin(WorldSettings.class)
public class MixinWorldSettings implements ICubicWorldSettings
{
//    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldInfo;)V", at = @At("RETURN"))
//    private void onConstruct(WorldInfo info, CallbackInfo cbi) {
//        this.isCubic = ((ICubicWorldSettings) info).isCubic();
//    }
//
//    @Inject(
//        method = "<init>(JLnet/minecraft/world/WorldSettings$GameType;ZZLnet/minecraft/world/WorldType;)V",
//        at = @At("RETURN"))
//    private void onConstruct(long seedIn, WorldSettings.GameType gameType, boolean enableMapFeatures,
//        boolean hardcoreMode, WorldType worldTypeIn, CallbackInfo ci) {
//        this.isCubic = CubicChunksConfig.forceLoadCubicChunks != CubicChunksConfig.ForceCCMode.NONE || worldTypeIn instanceof ICubicWorldType;
//    }
//
//    @Override
//    public boolean isCubic() {
//        return isCubic;
//    }
//
//    @Override
//    public void setCubic(boolean cubic) {
//        this.isCubic = cubic;
//    }
}
