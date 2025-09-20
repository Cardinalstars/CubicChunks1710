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
package com.cardinalstar.cubicchunks.network;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

@ParametersAreNonnullByDefault
public abstract class AbstractMessageHandler<T extends IMessage> implements IMessageHandler<T, IMessage> {

    public abstract void handleClientMessage(World world, EntityPlayer player, T message, MessageContext ctx);

    public abstract void handleServerMessage(EntityPlayer player, T message, MessageContext ctx);

    @Nullable
    @Override
    public final IMessage onMessage(T message, MessageContext ctx) {
        try {
            if (ctx.side.isClient()) {
                Minecraft mc = Minecraft.getMinecraft();
                World world = mc.theWorld;
                if (world == null) {
                    CubicChunks.LOGGER.warn("Received packet when world doesn't exist!");
                    return null;
                }
                EntityPlayer player = mc.thePlayer;
                handleClientMessage(world, player, message, ctx);
            } else {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                World world = player.worldObj;
                if (world == null) {
                    CubicChunks.LOGGER.warn("Received packet when world doesn't exist!");
                    return null;
                }
                handleServerMessage(player, message, ctx);
            }
            return null;
        } catch (Throwable t) {
            CubicChunks.LOGGER.catching(t);
            FMLCommonHandler.instance()
                .exitJava(-1, false);
            throw t;
        }
    }
}
