/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2021 OpenCubicChunks
 *  Copyright (c) 2015-2021 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.mixin.early.client;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@Mixin(ChunkCache.class)
public abstract class MixinChunkCache_HeightLimits {

    @Shadow private World worldObj;

    // Interestingly enough this hits both the < 0 or >= 0 calls which sort of makes sense.
    @ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0, intValue = 0))
    private int getSkyBlockTypeBrightness_getMinHeight(int orig)
    {
        return ((ICubicWorld) worldObj).getMinHeight();
    }

    @ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(intValue = 0, ordinal = 0))
    private int getSkyBlockTypeBrightness_getMinHeightDefault(int orig)
    {
        return ((ICubicWorld) worldObj).getMinHeight();
    }

    @ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(intValue = 256))
    private int getSkyBlockTypeBrightness_getMaxHeight(int orig)
    {
        return ((ICubicWorld) worldObj).getMaxHeight();
    }

    @ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(intValue = 255))
    private int getSkyBlockTypeBrightness_getMaxHeightDefault(int orig)
    {
        return ((ICubicWorld) worldObj).getMaxHeight() - 1;
    }

    // Interestingly enough this hits both the < 0 or >= 0 calls which sort of makes sense.
    @ModifyConstant(method = "getSpecialBlockBrightness", constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0))
    private int getSpecialBlockBrightness_getMinHeight(int orig)
    {
        return ((ICubicWorld) worldObj).getMinHeight();
    }

    @ModifyConstant(method = "getSpecialBlockBrightness", constant = @Constant(intValue = 256))
    private int getSpecialBlockBrightness_getMaxHeight(int orig)
    {
        return ((ICubicWorld) worldObj).getMaxHeight();
    }

    @ModifyConstant(method = "getSpecialBlockBrightness", constant = @Constant(intValue = 255))
    private int getSpecialBlockBrightness_getMaxHeightDefault(int orig)
    {
        return ((ICubicWorld) worldObj).getMaxHeight() - 1;
    }

    // getHeight
    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = 256))
    private int getHeightFix(int orig)
    {
        return ((ICubicWorld) worldObj).getMaxHeight();
    }
}
