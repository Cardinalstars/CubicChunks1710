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

import static com.cardinalstar.cubicchunks.util.Coords.cubeToMinBlock;

import java.util.Arrays;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.Bits;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;

import gnu.trove.list.TShortList;

// this class exists only for network compatibility with older servers
@ParametersAreNonnullByDefault
public class PacketEncoderCubeSkyLightUpdates
    extends CCPacketEncoder<PacketEncoderCubeSkyLightUpdates.PacketCubeSkyLightUpdates> {

    @Desugar
    public record PacketCubeSkyLightUpdates(CubePos pos, boolean isFullRelight, @Nullable byte[] data)
        implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.CubeSkyLightUpdates.id;
        }
    }

    public PacketEncoderCubeSkyLightUpdates() {}

    public static PacketCubeSkyLightUpdates createPacket(Cube cube, TShortList updates) {
        if (cube.getStorage() == null) {
            // no light
            return new PacketCubeSkyLightUpdates(cube.getCoords(), true, null);
        }

        byte[] data = new byte[updates.size() * 2];

        for (int i = 0; i < updates.size(); i++) {
            short packed = updates.get(i);
            int localX = AddressTools.getLocalX(packed);
            int localY = AddressTools.getLocalY(packed);
            int localZ = AddressTools.getLocalZ(packed);

            int value = cube.getStorage()
                .getExtSkylightValue(localX, localY, localZ);

            data[i * 2] = (byte) (Bits.packUnsignedToInt(localX, 4, 0) | Bits.packUnsignedToInt(localY, 4, 4));
            data[i * 2 + 1] = (byte) (Bits.packUnsignedToInt(localZ, 4, 0) | Bits.packUnsignedToInt(value, 4, 4));
        }

        return new PacketCubeSkyLightUpdates(cube.getCoords(), false, data);
    }

    public static PacketCubeSkyLightUpdates createPacket(Cube cube) {
        if (cube.getStorage() == null) {
            // no light
            return new PacketCubeSkyLightUpdates(cube.getCoords(), true, null);
        }

        byte[] data = Arrays.copyOf(
            cube.getStorage()
                .getSkylightArray().data,
            Cube.SIZE * Cube.SIZE * Cube.SIZE / 2);

        return new PacketCubeSkyLightUpdates(cube.getCoords(), true, data);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.CubeSkyLightUpdates.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCubeSkyLightUpdates packet) {
        buffer.writeCubePos(packet.pos);
        buffer.writeBoolean(packet.isFullRelight);

        if (packet.data == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeByteArray(packet.data);
        }
    }

    @Override
    public PacketCubeSkyLightUpdates readPacket(CCPacketBuffer buffer) {
        CubePos pos = buffer.readCubePos();

        boolean isFullRelight = buffer.readBoolean();
        boolean hasData = buffer.readBoolean();

        byte[] data = hasData ? buffer.readByteArray() : null;

        return new PacketCubeSkyLightUpdates(pos, isFullRelight, data);
    }

    @Override
    public void process(World world, PacketCubeSkyLightUpdates packet) {
        WorldClient worldClient = (WorldClient) world;
        CubeProviderClient cubeCache = (CubeProviderClient) worldClient.getChunkProvider();

        // get the cube
        Cube cube = cubeCache.getCube(packet.pos);

        if (packet.data == null) {
            // this means the EBS was null serverside. So it needs to be null clientside
            cube.setStorage(null);
            return;
        }

        ExtendedBlockStorage storage = cube.getStorage();
        if (cube.getStorage() == null) {
            cube.setStorage(
                storage = new ExtendedBlockStorage(cubeToMinBlock(cube.getY()), !worldClient.provider.hasNoSky));
        }

        assert storage != null;
        if (packet.isFullRelight) {
            storage.setSkylightArray(new NibbleArray(packet.data, 4));
        } else {
            int updateCount = packet.data.length >> 1;

            for (int i = 0; i < updateCount; i++) {
                int packed1 = packet.data[i * 2] & 0xFF;
                int packed2 = packet.data[i * 2 + 1] & 0xFF;
                storage.setExtSkylightValue(
                    Bits.unpackUnsigned(packed1, 4, 0),
                    Bits.unpackUnsigned(packed1, 4, 4),
                    Bits.unpackUnsigned(packed2, 4, 0),
                    Bits.unpackUnsigned(packed2, 4, 4));
            }
        }
        cube.markForRenderUpdate();
    }
}
