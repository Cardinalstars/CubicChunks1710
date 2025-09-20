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

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.github.bsideup.jabel.Desugar;

@ParametersAreNonnullByDefault
public class PacketEncoderCubicWorldData extends CCPacketEncoder<PacketEncoderCubicWorldData.PacketCubicWorldData> {

    @Desugar
    public record PacketCubicWorldData(int minHeight, int maxHeight, int minGenerationHeight, int maxGenerationHeight)
        implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.CubicWorldData.id;
        }
    }

    public PacketEncoderCubicWorldData() {}

    public static PacketCubicWorldData createPacket(WorldServer world) {
        int minHeight = ((ICubicWorld) world).getMinHeight();
        int maxHeight = ((ICubicWorld) world).getMaxHeight();

        int minGenerationHeight;
        int maxGenerationHeight;

        if (world.getWorldInfo()
            .getTerrainType() instanceof ICubicWorldType type) {
            IntRange range = type.calculateGenerationHeightRange(world);
            minGenerationHeight = range.getMin();
            maxGenerationHeight = range.getMax();
        } else {
            minGenerationHeight = 0;
            maxGenerationHeight = 256;
        }

        return new PacketCubicWorldData(minHeight, maxHeight, minGenerationHeight, maxGenerationHeight);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.CubicWorldData.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCubicWorldData packet) {
        buffer.writeInt(packet.minHeight);
        buffer.writeInt(packet.maxHeight);
        buffer.writeInt(packet.minGenerationHeight);
        buffer.writeInt(packet.maxGenerationHeight);
    }

    @Override
    public PacketCubicWorldData readPacket(CCPacketBuffer buffer) {
        return new PacketCubicWorldData(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    @Override
    public void process(World world, PacketCubicWorldData packet) {
        ((ICubicWorldInternal.Client) world).initCubicWorldClient(
            new IntRange(packet.minHeight, packet.maxHeight),
            new IntRange(packet.minGenerationHeight, packet.maxGenerationHeight));

        // Update stale ViewFrustum/RenderChunk-related state, as it was previously set for non-CC world
        Minecraft.getMinecraft().renderGlobal.setWorldAndLoadRenderers((WorldClient) world);
    }
}
