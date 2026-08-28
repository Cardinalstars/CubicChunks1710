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

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Caps the depth of the synchronous neighbour-notify cascade that overflows the
 * stack during cubic world generation (issue #61).
 *
 * <p>Vanilla block updates propagate support-dependency chains (snow, reeds,
 * leaves, cacti, ...) through {@code markAndNotifyBlock} -&gt;
 * {@code notifyBlockOfNeighborChange} -&gt; {@code onNeighborBlockChange} -&gt;
 * {@code setBlockToAir} -&gt; {@code markAndNotifyBlock} ..., recursively. Vanilla
 * keeps that chain shallow with hard height bounds; a cubic world removes those
 * bounds, so a large patch of support-dependent blocks losing support at once
 * (e.g. GTNH's {@code BFSLeafDecay} clearing a canopy) can recurse deep enough to
 * overflow the (small) thread stack.
 *
 * <p>This mixin parks neighbour notifies past a depth cap in a thread-local FIFO
 * instead of dropping them, and drains the FIFO iteratively when the outermost
 * frame returns. Notify semantics are preserved (no floating snow, no missed
 * updates); only the recursion is flattened.
 */
@Mixin(World.class)
public class MixinWorld_NeighborNotify {

    @Unique
    private static final int CC_MAX_NOTIFY_DEPTH = 64;

    @Unique
    private static final ThreadLocal<int[]> cc$notifyDepth = ThreadLocal.withInitial(() -> new int[1]);

    @Unique
    private static final ThreadLocal<LongArrayFIFOQueue> cc$pendingNotifies = ThreadLocal.withInitial(LongArrayFIFOQueue::new);

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

    @Inject(method = "notifyBlockOfNeighborChange", at = @At("HEAD"), cancellable = true)
    private void cc$capNotifyDepthHead(int x, int y, int z, Block neighbor, CallbackInfo ci) {
        int[] depth = cc$notifyDepth.get();
        depth[0]++;
        if (depth[0] > CC_MAX_NOTIFY_DEPTH) {
            // Too deep: park this neighbour notify and skip this frame. The
            // RETURN handler still runs and balances the depth counter.
            cc$pendingNotifies.get().enqueue(cc$key(x, y, z));
            ci.cancel();
        }
    }

    @Inject(method = "notifyBlockOfNeighborChange", at = @At("RETURN"))
    private void cc$capNotifyDepthReturn(int x, int y, int z, Block neighbor, CallbackInfo ci) {
        int[] depth = cc$notifyDepth.get();
        depth[0]--;
        if (depth[0] == 0) {
            // Outermost frame: drain the parked notifies iteratively (no recursion).
            LongArrayFIFOQueue pending = cc$pendingNotifies.get();
            while (pending.size() > 0) {
                long key = pending.dequeueLong();
                ((World) (Object) this).notifyBlockOfNeighborChange(cc$x(key), cc$y(key), cc$z(key), neighbor);
            }
        }
    }
}
