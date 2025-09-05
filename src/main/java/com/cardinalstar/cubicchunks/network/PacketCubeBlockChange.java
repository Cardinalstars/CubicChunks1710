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

import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gnu.trove.TShortCollection;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import io.netty.buffer.ByteBuf;

@ParametersAreNonnullByDefault
public class PacketCubeBlockChange implements IMessage {

    int[] heightValues;
    CubePos cubePos;
    short[] localAddresses;
    Block[] blocks;
    int[] blockMetas;

    public PacketCubeBlockChange() {}

    public PacketCubeBlockChange(Cube cube, TShortCollection localAddresses) {
        this.cubePos = cube.getCoords();
        this.localAddresses = localAddresses.toArray();
        this.blocks = new Block[localAddresses.size()];
        this.blockMetas = new int[localAddresses.size()];
        int i = localAddresses.size() - 1;
        TIntSet xzAddresses = new TIntHashSet();
        for (; i >= 0; i--) {
            int localAddress = this.localAddresses[i];
            int x = AddressTools.getLocalX(localAddress);
            int y = AddressTools.getLocalY(localAddress);
            int z = AddressTools.getLocalZ(localAddress);
            this.blocks[i] = cube.getBlock(x, y, z);
            this.blockMetas[i] = cube.getBlockMetadata(x, y, z);
            xzAddresses.add(AddressTools.getLocalAddress(x, z));
        }
        this.heightValues = new int[xzAddresses.size()];
        i = 0;
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
    }

    @SuppressWarnings("deprecation") // Forge thinks we are trying to register a block or something :P
    @Override
    public void fromBytes(ByteBuf in) {
        this.cubePos = new CubePos(in.readInt(), in.readInt(), in.readInt());
        short numBlocks = in.readShort();
        localAddresses = new short[numBlocks];
        blocks = new Block[numBlocks];
        blockMetas = new int[numBlocks];

        for (int i = 0; i < numBlocks; i++) {
            localAddresses[i] = in.readShort();
            blocks[i] = Block.getBlockById(in.readUnsignedShort());
            blockMetas[i] = in.readUnsignedByte();
        }
        int numHmapChanges = in.readUnsignedByte();
        heightValues = new int[numHmapChanges];
        for (int i = 0; i < numHmapChanges; i++) {
            heightValues[i] = in.readInt();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void toBytes(ByteBuf out) {
        out.writeInt(cubePos.getX());
        out.writeInt(cubePos.getY());
        out.writeInt(cubePos.getZ());
        out.writeShort(localAddresses.length);
        for (int i = 0; i < localAddresses.length; i++) {
            out.writeShort(localAddresses[i]);
            out.writeShort(Block.getIdFromBlock(blocks[i]));
            out.writeByte(blockMetas[i]);
        }
        out.writeByte(heightValues.length);
        for (int v : heightValues) {
            out.writeInt(v);
        }
    }

    public static class Handler extends AbstractClientMessageHandler<PacketCubeBlockChange> {

        @Nullable
        @Override
        public void handleClientMessage(World world, EntityPlayer player, PacketCubeBlockChange packet,
            MessageContext ctx) {
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
                worldClient.invalidateBlockReceiveRegion(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ());
                worldClient.setBlock(pos.getX(), pos.getY(), pos.getZ(), packet.blocks[i], packet.blockMetas[i], 3);
            }
            cube.getTileEntityMap()
                .values()
                .forEach(TileEntity::updateContainingBlockInfo);
        }
    }
}
