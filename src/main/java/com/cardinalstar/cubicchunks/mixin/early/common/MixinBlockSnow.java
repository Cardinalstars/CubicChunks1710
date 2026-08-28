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

import net.minecraft.block.BlockSnow;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Converts the synchronous deep recursion of vanilla snow removal into a
 * bounded recursion plus an iterative tail pass (issue #61).
 *
 * <p>Vanilla {@code BlockSnow.onNeighborBlockChange} -> {@code func_150155_m} ->
 * {@code setBlockToAir} -> neighbour notify -> next snow ... recurses
 * synchronously. That notify is required (otherwise the snow above a removed
 * layer would float), and in a vanilla world the chain is shallow. But cubic
 * world generation can produce a large patch of snow whose support disappears
 * at once (e.g. GTNH's {@code BFSLeafDecay} removing a whole leaf canopy), and
 * the synchronous recursion can overflow the stack.
 *
 * <p>This mixin caps the recursion depth without changing removal semantics:
 * frames past the cap park their coordinate in a thread-local FIFO instead of
 * being dropped, and when the outermost frame returns (depth back to zero) the
 * parked coordinates are removed iteratively through the same vanilla path
 * ({@code func_150155_m} semantics: remove if no support), so no snow is lost
 * and neighbour notifies still happen - just not recursively.
 *
 * <p>Cost: two int increments per removal plus, in the pathological case, an
 * enqueue/dequeue; normal removals never reach the cap.
 */
@Mixin(BlockSnow.class)
public class MixinBlockSnow {

    /** Max nested {@code func_150155_m} frames before frames park in the queue. */
    @Unique
    private static final int CC_MAX_SNOW_REMOVAL_DEPTH = 64;

    @Unique
    private static final ThreadLocal<int[]> cc$removalDepth = ThreadLocal.withInitial(() -> new int[1]);

    @Unique
    private static final ThreadLocal<LongArrayFIFOQueue> cc$pendingRemovals = ThreadLocal.withInitial(LongArrayFIFOQueue::new);

    @Unique
    private static long cc$key(int x, int y, int z) {
        return ((long) x & 0xFFFFFFL) << 40 | ((long) z & 0xFFFFFFL) << 16 | (y & 0xFFFFL);
    }

    @Unique
    private static int cc$x(long key) {
        return (int) (key >> 40 & 0xFFFFFFL) << 24 >> 24;
    }

    @Unique
    private static int cc$z(long key) {
        return (int) (key >> 16 & 0xFFFFFFL) << 24 >> 24;
    }

    @Unique
    private static int cc$y(long key) {
        return (short) (key & 0xFFFFL);
    }

    @Inject(method = "func_150155_m", at = @At("HEAD"), cancellable = true)
    private void cc$limitRemovalDepthHead(World world, int x, int y, int z, CallbackInfoReturnable<Boolean> ci) {
        int[] depth = cc$removalDepth.get();
        depth[0]++;
        if (depth[0] > CC_MAX_SNOW_REMOVAL_DEPTH) {
            // Too deep: park this coordinate and cancel this frame. The RETURN
            // handler still runs and balances the depth counter.
            cc$pendingRemovals.get().enqueue(cc$key(x, y, z));
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "func_150155_m", at = @At("RETURN"))
    private void cc$limitRemovalDepthReturn(World world, int x, int y, int z, CallbackInfoReturnable<Boolean> ci) {
        int[] depth = cc$removalDepth.get();
        depth[0]--;
        if (depth[0] == 0) {
            // Outermost frame: drain the parked removals iteratively through the
            // vanilla semantics (check support, remove if gone) - no recursion.
            LongArrayFIFOQueue pending = cc$pendingRemovals.get();
            while (pending.size() > 0) {
                long key = pending.dequeueLong();
                int px = cc$x(key);
                int py = cc$y(key);
                int pz = cc$z(key);
                if (world.getBlock(px, py, pz) instanceof BlockSnow && !world.getBlock(px, py, pz)
                    .canPlaceBlockAt(world, px, py, pz)) {
                    world.setBlockToAir(px, py, pz);
                }
            }
        }
    }
}
