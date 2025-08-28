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
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.server.ICubicPlayerList;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

//    @Redirect(method = "playerLoggedOut",
//        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;setChunkModifiedV", ordinal = 0),
//        require = 1)
//    private void setChunkModifiedOnPlayerLoggedOut(Chunk chunkIn, EntityPlayerMP playerIn) {
//        ICubicWorldInternal world = (ICubicWorldInternal) playerIn.getServerForPlayer();
//        if (world.isCubicWorld()) {
//            world.getCubeFromCubeCoords(playerIn.chunkCoordX, playerIn.chunkCoordY, playerIn.chunkCoordZ).markDirty();
//        } else {
//            ((World) world).getChunkFromBlockCoords(playerIn.chunkCoordX, playerIn.chunkCoordZ).setChunkModified();
//        }
//    }

    @Inject(method = "respawnPlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/gen/ChunkProviderServer;loadChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private void createPlayerChunk(EntityPlayerMP playerIn, int dimension, boolean conqueredEnd, CallbackInfoReturnable<EntityPlayerMP> cir) {
        if (!((ICubicWorld) playerIn.worldObj).isCubicWorld()) {
            return;
        }
        for (int dCubeY = -8; dCubeY <= 8; dCubeY++) {
            ((ICubicWorld) playerIn.worldObj).getCubeFromBlockCoords(playerIn.chunkCoordX, MathHelper.floor_double(playerIn.chunkCoordY + 0.5) + Coords.cubeToMinBlock(dCubeY), playerIn.chunkCoordZ);
        }
    }

//    @ModifyConstant(method = "respawnPlayer",
//        constant = @Constant(doubleValue = 256))
//    private double getMaxHeight(double _256, EntityPlayerMP playerIn, int dimension, boolean conqueredEnd) {
//        // +/- 8 chunks around the original position are loaded because of an inject above
//        if (!playerIn.worldObj.isBlockLoaded(new BlockPos(playerIn))) {
//            return Double.NEGATIVE_INFINITY;
//        }
//        return ((ICubicWorld) playerIn.worldObj).getMaxHeight();
//    }

}
