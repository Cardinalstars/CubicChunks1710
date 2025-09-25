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

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;

// TODO Watch implementation packet io functions for block data arrays and serialization length
@ParametersAreNonnullByDefault
class WorldEncoder {

    static void encodeColumn(CCPacketBuffer out, Chunk column) {
        // 1. biomes
        out.writeBytes(column.getBiomeArray());
        ((IColumnInternal) column).writeHeightmapDataForClient(out);
    }

    static void decodeColumn(CCPacketBuffer in, Chunk column) {
        // 1. biomes
        in.readBytes(column.getBiomeArray());
        if (in.readableBytes() > 0) {
            ((IColumnInternal) column).loadClientHeightmapData(in);
        }
    }

    static void encodeCubes(CCPacketBuffer out, Collection<Cube> cubes) {
        // write first all the flags, then all the block data, then all the light data etc for better compression

        // 1. emptiness
        cubes.forEach(cube -> {
            byte flags = 0;
            if (cube.isEmpty()) flags |= 1;
            if (cube.getStorage() != null) flags |= 2;
            if (cube.getBiomeArray() != null) flags |= 4;
            out.writeByte(flags);
        });

        // 2. block IDs and metadata
        cubes.forEach(cube -> {
            if (!cube.isEmpty()) {
                // noinspection ConstantConditions
                ExtendedBlockStorage storage = cube.getStorage();
                out.writeBytes(storage.getBlockLSBArray());
                NibbleArray msb = storage.getBlockMSBArray();
                out.writeBoolean(msb != null);
                if (msb != null) {
                    out.writeBytes(msb.data);
                }
                out.writeBytes(storage.getMetadataArray().data);
            }
        });

        // 3. block light
        cubes.forEach(cube -> {
            ExtendedBlockStorage storage = cube.getStorage();
            if (storage != null) {
                out.writeBytes(storage.getBlocklightArray().data);
            }
        });

        // 4. sky light
        cubes.forEach(cube -> {
            ExtendedBlockStorage storage = cube.getStorage();
            if (storage != null && !cube.getWorld().provider.hasNoSky) {
                out.writeBytes(storage.getSkylightArray().data);
            }
        });

        // 5. heightmap and bottom-block-y. Each non-empty cube has a chance
        // to update this data.
        // trying to keep track of when it changes would be complex, so send
        // it wil all cubes
        cubes.forEach(cube -> {
            if (!cube.isEmpty()) {
                ((IColumnInternal) cube.getColumn()).writeHeightmapDataForClient(out);
            }
        });

        // 6. biomes
        cubes.forEach(cube -> { if (cube.getBiomeArray() != null) out.writeBytes(cube.getBiomeArray()); });
    }

    static void decodeCube(CCPacketBuffer in, List<Cube> cubes) {
        cubes.stream()
            .filter(Objects::nonNull)
            .forEach(Cube::setClientCube);

        // 1. emptiness
        boolean[] isEmpty = new boolean[cubes.size()];
        boolean[] hasStorage = new boolean[cubes.size()];
        boolean[] hasCustomBiomeMap = new boolean[cubes.size()];

        for (int i = 0; i < cubes.size(); i++) {
            byte flags = in.readByte();
            isEmpty[i] = (flags & 1) != 0 || cubes.get(i) == null;
            hasStorage[i] = (flags & 2) != 0 && cubes.get(i) != null;
            hasCustomBiomeMap[i] = (flags & 4) != 0 && cubes.get(i) != null;
        }

        for (int i = 0; i < cubes.size(); i++) {
            if (hasStorage[i]) {
                Cube cube = cubes.get(i);
                ExtendedBlockStorage storage = new ExtendedBlockStorage(
                    Coords.cubeToMinBlock(cube.getY()),
                    !cube.getWorld().provider.hasNoSky);
                cube.setStorageFromSave(storage);
            }
        }

        // 2. Block IDs and metadata
        for (int i = 0; i < cubes.size(); i++) {
            if (!isEmpty[i]) {
                // noinspection ConstantConditions
                ExtendedBlockStorage storage = cubes.get(i)
                    .getStorage();
                if (storage != null) {
                    byte[] lsbData = storage.getBlockLSBArray();
                    in.readBytes(lsbData);

                    boolean hasMsb = in.readBoolean();
                    if (hasMsb) {
                        if (storage.getBlockMSBArray() == null) {
                            storage.createBlockMSBArray();
                        }

                        byte[] msbData = storage.getBlockMSBArray().data;
                        in.readBytes(msbData);
                    }
                    byte[] meta = storage.getMetadataArray().data;
                    in.readBytes(meta);
                }
            }
        }

        // 3. block light
        for (int i = 0; i < cubes.size(); i++) {
            if (hasStorage[i]) {
                // noinspection ConstantConditions
                byte[] data = cubes.get(i)
                    .getStorage()
                    .getBlocklightArray().data;
                in.readBytes(data);
            }
        }

        // 4. sky light
        for (int i = 0; i < cubes.size(); i++) {
            if (hasStorage[i] && !cubes.get(i)
                .getWorld().provider.hasNoSky) {
                // noinspection ConstantConditions
                byte[] data = cubes.get(i)
                    .getStorage()
                    .getSkylightArray().data;
                in.readBytes(data);
            }
        }

        int[] oldHeights = new int[Cube.SIZE * Cube.SIZE];
        // 5. heightmaps and after all that - update ref counts
        for (int i = 0; i < cubes.size(); i++) {
            if (!isEmpty[i]) {
                Cube cube = cubes.get(i);
                ILightingManager lm = ((ICubicWorldInternal) cube.getWorld()).getLightingManager();
                IColumnInternal column = cube.getColumn();
                ClientHeightMap coi = (ClientHeightMap) column.getOpacityIndex();
                for (int dx = 0; dx < Cube.SIZE; dx++) {
                    for (int dz = 0; dz < Cube.SIZE; dz++) {
                        oldHeights[AddressTools.getLocalAddress(dx, dz)] = coi.getTopBlockY(dx, dz);
                    }
                }
                column.loadClientHeightmapData(in);
                for (int dx = 0; dx < Cube.SIZE; dx++) {
                    for (int dz = 0; dz < Cube.SIZE; dz++) {
                        int oldY = oldHeights[AddressTools.getLocalAddress(dx, dz)];
                        int newY = coi.getTopBlockY(dx, dz);
                        if (oldY != newY) {
                            lm.updateLightBetween(cube.getColumn(), dx, oldY, newY, dz);
                        }
                    }
                }
                // noinspection ConstantConditions
                cube.getStorage()
                    .removeInvalidBlocks();
            }
        }

        // 6. biomes
        for (int i = 0; i < cubes.size(); i++) {
            if (!hasCustomBiomeMap[i]) continue;
            Cube cube = cubes.get(i);
            byte[] blockBiomeArray = new byte[Coords.BIOMES_PER_CUBE];
            in.readBytes(blockBiomeArray);
            cube.setBiomeArray(blockBiomeArray);
        }
    }
}
