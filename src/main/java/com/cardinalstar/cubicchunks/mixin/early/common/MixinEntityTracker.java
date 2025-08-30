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

import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.entity.ICubicEntityTracker;
import com.cardinalstar.cubicchunks.server.ICubicPlayerList;

@Mixin(EntityTracker.class)
public class MixinEntityTracker implements ICubicEntityTracker {

    @Shadow
    private Set<EntityTrackerEntry> trackedEntities;
    @Unique
    private int maxVertTrackingDistanceThreshold;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(WorldServer world, CallbackInfo ci) {
        setVertViewDistance(
            ((ICubicPlayerList) world.func_73046_m()
                .getConfigurationManager()).getVerticalViewDistance());
    }

    @Redirect(
        method = "addEntityToTracker(Lnet/minecraft/entity/Entity;IIZ)V",
        at = @At(value = "NEW", target = "(Lnet/minecraft/entity/Entity;IIZ)Lnet/minecraft/entity/EntityTrackerEntry;"))
    private EntityTrackerEntry onCreateEntry(Entity entityIn, int rangeIn, int updateFrequencyIn,
        boolean sendVelocityUpdatesIn) {
        EntityTrackerEntry e = new EntityTrackerEntry(entityIn, rangeIn, updateFrequencyIn, sendVelocityUpdatesIn);
        // noinspection ConstantConditions
        ((ICubicEntityTracker.Entry) e).setMaxVertRange(maxVertTrackingDistanceThreshold);
        return e;
    }

    // Previous version of this function contain code which force Minecraft to send all SPacketEntityAttach before any
    // SPacketSetPassengers
    @Override
    public void sendLeashedEntitiesInCube(EntityPlayerMP player, ICube cubeIn) {
        for (EntityTrackerEntry entitytrackerentry : this.trackedEntities) {
            Entity entity = entitytrackerentry.myEntity;
            if (entity != player && entity.chunkCoordX == cubeIn.getX()
                && entity.chunkCoordZ == cubeIn.getZ()
                && entity.chunkCoordY == cubeIn.getY()) {

                entitytrackerentry.tryStartWachingThis(player);
                // noinspection ConstantConditions
                if (entity instanceof EntityLiving && ((EntityLiving) entity).getLeashedToEntity() != null) {
                    player.playerNetServerHandler
                        .sendPacket(new S1BPacketEntityAttach(1, entity, ((EntityLiving) entity).getLeashedToEntity()));
                }

                if (entity.riddenByEntity != null) {
                    player.playerNetServerHandler.sendPacket(new S1BPacketEntityAttach(0, entity, entity.ridingEntity));
                }
            }
        }
    }

    @Override
    public void setVertViewDistance(int viewDistance) {
        this.maxVertTrackingDistanceThreshold = (viewDistance - 1) * 16;
        for (EntityTrackerEntry e : this.trackedEntities) {
            ((ICubicEntityTracker.Entry) e).setMaxVertRange(this.maxVertTrackingDistanceThreshold);
        }
    }
}
