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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

@ParametersAreNonnullByDefault
public class PacketEncoderCubes extends CCPacketEncoder<PacketEncoderCubes.PacketCubes> {

    @Desugar
    public record PacketCubes(CubePos[] cubePos, byte[] data, List<List<NBTTagCompound>> tileEntityTags) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.Cubes.id;
        }
    }

    public PacketEncoderCubes() {}

    public static PacketCubes createPacket(List<Cube> cubes) {
        CubicChunks.LOGGER.info("Sending packet with {} cubes", cubes.size());
        cubes.sort(
            Comparator.comparingInt(Cube::getY)
                .thenComparingInt(Cube::getX)
                .thenComparingInt(Cube::getZ));

        CubePos[] cubePos = new CubePos[cubes.size()];
        for (int i = 0; i < cubes.size(); i++) {
            cubePos[i] = cubes.get(i)
                .getCoords();
        }

        ByteBuf cubeData = Unpooled.buffer();

        WorldEncoder.encodeCubes(new CCPacketBuffer(cubeData), cubes);

        byte[] data = new byte[cubeData.writerIndex()];

        cubeData.readBytes(data);

        List<List<NBTTagCompound>> tileEntityTags = new ArrayList<>();

        cubes.forEach(cube -> {
            if (cube.getTileEntityMap().isEmpty()) {
                tileEntityTags.add(Collections.emptyList());
            } else {
                List<NBTTagCompound> list = new ArrayList<>();

                for (TileEntity tileEntity : cube.getTileEntityMap().values()) {
                    NBTTagCompound tag = new NBTTagCompound();
                    tileEntity.writeToNBT(tag);
                    list.add(tag);
                }

                tileEntityTags.add(list);
            }
        });

        return new PacketCubes(cubePos, data, tileEntityTags);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.Cubes.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCubes packet) {
        buffer.writeArray(packet.cubePos, CCPacketBuffer::writeCubePos);

        buffer.writeByteArray(packet.data);

        buffer.writeList(packet.tileEntityTags, (buf2, list) -> {
            buf2.writeList(list, CCPacketBuffer::writeCompoundTag);
        });
    }

    @Override
    public PacketCubes readPacket(CCPacketBuffer buf) {
        CubePos[] cubePos = buf.readArray(new CubePos[0], CCPacketBuffer::readCubePos);

        byte[] data = buf.readByteArray();

        List<List<NBTTagCompound>> tileEntityTags = buf.readList(buf2 -> {
            return buf2.readList(CCPacketBuffer::readCompoundTag);
        });

        return new PacketCubes(cubePos, data, tileEntityTags);
    }

    @Override
    public void process(World world, PacketCubes packet) {
        CubeProviderClient cubeCache = (CubeProviderClient) world.getChunkProvider();

        List<Cube> cubes = new ArrayList<>();

        for (CubePos pos : packet.cubePos) {
            Cube cube = cubeCache.loadCube(pos); // new cube
            // isEmpty actually checks if the column is a BlankColumn
            if (cube == null) {
                CubicChunks.LOGGER.error("Out of order cube received! No column for cube at {} exists!", pos);
            }
            cubes.add(cube);
        }

        ByteBuf buf = Unpooled.wrappedBuffer(packet.data);
        WorldEncoder.decodeCube(new CCPacketBuffer(buf), cubes);

        cubes.stream()
            .filter(Objects::nonNull)
            .forEach(Cube::markForRenderUpdate);

        packet.tileEntityTags.forEach(list -> {
            list.forEach(tag -> {
                int blockX = tag.getInteger("x");
                int blockY = tag.getInteger("y");
                int blockZ = tag.getInteger("z");
                TileEntity tileEntity = world.getTileEntity(blockX, blockY, blockZ);

                if (tileEntity != null) {
                    tileEntity.readFromNBT(tag);
                }
            });
        });
    }
}
