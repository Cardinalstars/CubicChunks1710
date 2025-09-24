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

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import gnu.trove.TShortCollection;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

@ParametersAreNonnullByDefault
public class PacketEncoderCubeBlockChange extends CCPacketEncoder<PacketEncoderCubeBlockChange.PacketCubeBlockChange> {

    @Desugar
    public record PacketCubeBlockChange(CubePos cubePos, short[] localAddresses, Block[] blocks, int[] blockMetas,
        int[] heightValues) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.CubeBlockChange.id;
        }
    }

    public PacketEncoderCubeBlockChange() {}

    public static PacketCubeBlockChange createPacket(Cube cube, TShortCollection localAddresses) {
        CubePos cubePos = cube.getCoords();
        short[] localAddressArray = localAddresses.toArray();
        Block[] blocks = new Block[localAddressArray.length];
        int[] blockMetas = new int[localAddressArray.length];

        TIntSet xzAddresses = new TIntHashSet();

        for (int i = 0; i < localAddressArray.length; i++) {
            short localAddress = localAddressArray[i];
            int x = AddressTools.getLocalX(localAddress);
            int y = AddressTools.getLocalY(localAddress);
            int z = AddressTools.getLocalZ(localAddress);
            blocks[i] = cube.getBlock(x, y, z);
            blockMetas[i] = cube.getBlockMetadata(x, y, z);
            xzAddresses.add(AddressTools.getLocalAddress(x, z));
        }

        int[] heightValues = new int[xzAddresses.size()];

        int i = 0;
        TIntIterator it = xzAddresses.iterator();
        while (it.hasNext()) {
            int v = it.next();
            int x = AddressTools.getLocalX(v);
            int z = AddressTools.getLocalZ(v);
            int height = ((IColumnInternal) cube.getColumn()).getTopYWithStaging(x, z);
            v |= height << 8;
            heightValues[i] = v;
            i++;
        }

        return new PacketCubeBlockChange(cubePos, localAddressArray, blocks, blockMetas, heightValues);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.CubeBlockChange.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCubeBlockChange packet) {
        buffer.writeCubePos(packet.cubePos);

        buffer.writeShort(packet.localAddresses.length);

        for (int i = 0; i < packet.localAddresses.length; i++) {
            buffer.writeShort(packet.localAddresses[i]);
            buffer.writeBlock(packet.blocks[i]);
            buffer.writeBlockMeta(packet.blockMetas[i]);
        }

        buffer.writeByte(packet.heightValues.length);

        for (int v : packet.heightValues) {
            buffer.writeInt(v);
        }
    }

    @Override
    public PacketCubeBlockChange readPacket(CCPacketBuffer buffer) {
        CubePos pos = buffer.readCubePos();

        int blockCount = buffer.readShort();

        short[] addresses = new short[blockCount];
        Block[] blocks = new Block[blockCount];
        int[] metas = new int[blockCount];

        for (short i = 0; i < blockCount; i++) {
            addresses[i] = buffer.readShort();
            blocks[i] = buffer.readBlock();
            metas[i] = buffer.readBlockMeta();
        }

        int heightmapCount = buffer.readUnsignedByte();
        int[] heightValues = new int[heightmapCount];

        for (int i = 0; i < heightmapCount; i++) {
            heightValues[i] = buffer.readInt();
        }

        return new PacketCubeBlockChange(pos, addresses, blocks, metas, heightValues);
    }

    @Override
    public void process(World world, PacketCubeBlockChange packet) {
        WorldClient worldClient = (WorldClient) world;
        CubeProviderClient cubeCache = (CubeProviderClient) worldClient.getChunkProvider();

        // get the cube
        Cube cube = cubeCache.getCube(packet.cubePos);
        if (cube instanceof BlankCube) {
            CubicChunks.LOGGER.error("Ignored block update to blank cube {}", packet.cubePos);
            return;
        }

        ClientHeightMap index = (ClientHeightMap) cube.getColumn()
            .getOpacityIndex();
        for (int hmapUpdate : packet.heightValues) {
            int x = hmapUpdate & 0xF;
            int z = (hmapUpdate >> 4) & 0xF;
            // height is signed, so don't use unsigned shift
            int height = hmapUpdate >> 8;
            index.setHeight(x, z, height);
        }
        // apply the update
        for (int i = 0; i < packet.localAddresses.length; i++) {
            BlockPos pos = cube.localAddressToBlockPos(packet.localAddresses[i]);
            worldClient
                .invalidateBlockReceiveRegion(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
            worldClient.setBlock(pos.getX(), pos.getY(), pos.getZ(), packet.blocks[i], packet.blockMetas[i], 3);
        }
    }
}
