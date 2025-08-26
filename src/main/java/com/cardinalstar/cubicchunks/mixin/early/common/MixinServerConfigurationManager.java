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
package com.cardinalstar.cubicchunks.mixin.early.common;

import com.cardinalstar.cubicchunks.entity.ICubicEntityTracker;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.server.ICubicPlayerList;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@Mixin(ServerConfigurationManager.class)
public abstract class MixinServerConfigurationManager implements ICubicPlayerList {

    @Shadow private int viewDistance;

    @Shadow @Final private MinecraftServer mcServer;

    protected int verticalViewDistance = -1;

    @Override public int getVerticalViewDistance() {
        return verticalViewDistance < 0 ? viewDistance : verticalViewDistance;
    }

    @Override public int getRawVerticalViewDistance() {
        return verticalViewDistance;
    }

    @Override public void setVerticalViewDistance(int dist) {
        this.verticalViewDistance = dist;

        if (this.mcServer.worldServers != null) {
            for (WorldServer worldserver : this.mcServer.worldServers) {
                if (worldserver != null && ((ICubicWorld) worldserver).isCubicWorld()) {
                    ((CubicPlayerManager) worldserver.getPlayerManager()).setPlayerViewDistance(viewDistance, dist);
                    ((ICubicEntityTracker) worldserver.getEntityTracker()).setVertViewDistance(dist);
                }
            }
        }
    }
}
