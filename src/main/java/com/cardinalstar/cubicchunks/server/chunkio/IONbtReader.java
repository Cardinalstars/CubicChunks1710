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
package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.Constants;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.core.ServerHeightMap;
import com.cardinalstar.cubicchunks.world.cube.Cube;

@ParametersAreNonnullByDefault
public class IONbtReader {

    @Nullable
    static Chunk readColumn(World world, int x, int z, NBTTagCompound nbt) {
        NBTTagCompound level = nbt.getCompoundTag("Level");
        Chunk column = readBaseColumn(world, x, z, level);
        if (column == null) {
            return null;
        }
        readBiomes(level, column);
        readOpacityIndex(level, column);

        column.isModified = false; // its exactly the same as on disk so its not modified
        return column; // TODO: use Chunk, not IColumn, whenever possible
    }

    @Nullable
    private static Chunk readBaseColumn(World world, int x, int z, NBTTagCompound nbt) {// check the version number
        byte version = nbt.getByte("v");
        if (version != 1) {
            throw new IllegalArgumentException(String.format("Column has wrong version: %d", version));
        }

        // check the coords
        int xCheck = nbt.getInteger("x");
        int zCheck = nbt.getInteger("z");
        if (xCheck != x || zCheck != z) {
            CubicChunks.LOGGER.warn(
                String.format(
                    "Column is corrupted! Expected (%d,%d) but got (%d,%d). Column will be regenerated.",
                    x,
                    z,
                    xCheck,
                    zCheck));
            return null;
        }

        // create the column
        Chunk column = new Chunk(world, x, z);

        // read the rest of the column properties
        column.inhabitedTime = nbt.getLong("InhabitedTime");

        // if (column.getCapabilities() != null && nbt.hasKey("ForgeCaps")) {
        // column.getCapabilities().deserializeNBT(nbt.getCompoundTag("ForgeCaps"));
        // }
        return column;
    }

    private static void readBiomes(NBTTagCompound nbt, Chunk column) {// biomes
        System.arraycopy(nbt.getByteArray("Biomes"), 0, column.getBiomeArray(), 0, Cube.SIZE * Cube.SIZE);
    }

    private static void readOpacityIndex(NBTTagCompound nbt, Chunk chunk) {// biomes
        IHeightMap hmap = ((IColumn) chunk).getOpacityIndex();
        ((ServerHeightMap) hmap).readData(nbt.getByteArray("OpacityIndex"));
    }

    static Cube readCube(Chunk column, final int cubeX, final int cubeY, final int cubeZ, NBTTagCompound nbt) throws IOException {
        if (column.xPosition != cubeX || column.zPosition != cubeZ) {
            throw new IllegalArgumentException(
                String.format(
                    "Invalid column (%d, %d) for cube at (%d, %d, %d)",
                    column.xPosition,
                    column.zPosition,
                    cubeX,
                    cubeY,
                    cubeZ));
        }

        World world = column.worldObj;
        NBTTagCompound level = nbt.getCompoundTag("Level");

        // check the version number
        byte version = level.getByte("v");
        if (version != 1) {
            throw new IllegalArgumentException(
                String.format("Cube at CubePos:(%d, %d, %d), has wrong version! %d", cubeX, cubeY, cubeZ, version));
        }

        // check the coordinates
        int xCheck = level.getInteger("x");
        int yCheck = level.getInteger("y");
        int zCheck = level.getInteger("z");

        if (xCheck != cubeX || yCheck != cubeY || zCheck != cubeZ) {
            throw new IOException(String.format(
                "Cube is corrupted! Expected (%d,%d,%d) but got (%d,%d,%d). Cube will be regenerated.",
                cubeX,
                cubeY,
                cubeZ,
                xCheck,
                yCheck,
                zCheck));
        }

        // build the cube
        final Cube cube = new Cube(column, cubeY);

        // set the worldgen stage
        cube.setPopulated(level.getBoolean("populated"));

        // previous versions will get their surface tracking redone. This is intended
        cube.setSurfaceTracked(level.getBoolean("isSurfaceTracked"));
        cube.setFullyPopulated(level.getBoolean("fullyPopulated"));

        // this status will get unset again in readLightingInfo() if the lighting engine is changed (LightingInfoType).
        cube.setInitialLightingDone(level.getBoolean("initLightDone"));

        // if (cube.getCapabilities() != null && nbt.hasKey("ForgeCaps")) {
        // cube.getCapabilities().deserializeNBT(nbt.getCompoundTag("ForgeCaps"));
        // }

        readBiomes(cube, level);
        readBlocks(level, world, cube);
        readEntities(level, world, cube);
        readTileEntities(level, world, cube);
        readScheduledBlockTicks(level, world);
        readLightingInfo(cube, level, world);

        cube.getColumn().preCacheCube(cube);

        return cube;
    }

    private static void readBlocks(NBTTagCompound nbt, World world, Cube cube) {
        boolean isEmpty = !nbt.hasKey("Sections");// is this an empty cube?
        if (!isEmpty) {
            NBTTagList sectionList = nbt.getTagList("Sections", 10);
            nbt = sectionList.getCompoundTagAt(0);

            ExtendedBlockStorage ebs = new ExtendedBlockStorage(
                Coords.cubeToMinBlock(cube.getY()),
                !cube.getWorld().provider.hasNoSky);

            ebs.setBlockLSBArray(nbt.getByteArray("BlocksLSB"));
            if (nbt.hasKey("BlocksMSB")) {
                ebs.setBlockMSBArray(new NibbleArray(nbt.getByteArray("BlocksMSB"), 4));
            }

            ebs.setBlockMetadataArray(new NibbleArray(nbt.getByteArray("DataMeta"), 4));

            ebs.setBlocklightArray(new NibbleArray(nbt.getByteArray("BlockLight"), 4));

            if (!world.provider.hasNoSky) {
                ebs.setSkylightArray(new NibbleArray(nbt.getByteArray("SkyLight"), 4));
            }

            ebs.removeInvalidBlocks();
            cube.setStorageFromSave(ebs);
        }
    }

    private static void readEntities(NBTTagCompound cubeNbt, World world, Cube cube) {// entities
        NBTTagList entityTagList = cubeNbt.getTagList("Entities", 10);

        if (entityTagList != null) {
            for (int l = 0; l < entityTagList.tagCount(); ++l) {
                NBTTagCompound entityNBT = entityTagList.getCompoundTagAt(l);
                Entity rootEntity = EntityList.createEntityFromNBT(entityNBT, world);
                cube.hasEntities = true;

                if (rootEntity != null) {
                    cube.addEntity(rootEntity);

                    int entityCubeX = Coords.getCubeXForEntity(rootEntity);
                    int entityCubeY = Coords.getCubeYForEntity(rootEntity);
                    int entityCubeZ = Coords.getCubeZForEntity(rootEntity);
                    if (entityCubeX != cube.getX() || entityCubeY != cube.getY() || entityCubeZ != cube.getZ()) {
                        CubicChunks.LOGGER.warn(
                            String.format(
                                "Loaded entity %s in cube (%d,%d,%d) to cube (%d,%d,%d)!",
                                rootEntity.getClass()
                                    .getName(),
                                entityCubeX,
                                entityCubeY,
                                entityCubeZ,
                                cube.getX(),
                                cube.getY(),
                                cube.getZ()));
                    }

                    // The entity needs to know what Cube it is in, this is normally done in Cube.addEntity()
                    // but Cube.addEntity() is not used when loading entities
                    // (unlike vanilla which uses Chunk.addEntity() even when loading entities)
                    rootEntity.addedToChunk = true;
                    rootEntity.chunkCoordX = cube.getX();
                    rootEntity.chunkCoordY = cube.getY();
                    rootEntity.chunkCoordZ = cube.getZ();

                    // Riding stuff
                    Entity currentEntity = rootEntity;

                    for (NBTTagCompound ridingNBT = entityNBT; ridingNBT
                        .hasKey("Riding", 10); ridingNBT = ridingNBT.getCompoundTag("Riding")) {
                        Entity entityThatIsRiding = EntityList
                            .createEntityFromNBT(ridingNBT.getCompoundTag("Riding"), world);

                        if (entityThatIsRiding != null) {
                            cube.addEntity(entityThatIsRiding);
                            currentEntity.mountEntity(entityThatIsRiding);
                        }

                        currentEntity = entityThatIsRiding;
                    }
                }
            }
        }
    }

    private static void readTileEntities(NBTTagCompound nbt, World world, Cube cube) {// tile entities
        NBTTagList nbtTileEntities = nbt.getTagList("TileEntities", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < nbtTileEntities.tagCount(); i++) {
            NBTTagCompound nbtTileEntity = nbtTileEntities.getCompoundTagAt(i);
            // TileEntity.create
            TileEntity blockEntity = TileEntity.createAndLoadEntity(nbtTileEntity);
            if (blockEntity != null) {
                if (!cube.getCoords()
                    .containsBlock(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord)) {
                    CubicChunks.LOGGER.warn(
                        "TileEntity " + blockEntity
                            + " is not in cube at "
                            + cube.getCoords()
                            + ", tile entity will be skipped");
                    continue;
                }
                cube.addTileEntity(blockEntity);
            }
        }
    }

    private static void readScheduledBlockTicks(NBTTagCompound nbt, World world) {
        if (!(world instanceof WorldServer)) {
            // if not server, reading from client cache which doesn't have scheduled ticks
            return;
        }
        NBTTagList nbtScheduledTicks = nbt.getTagList("TileTicks", 10);
        for (int i = 0; i < nbtScheduledTicks.tagCount(); i++) {
            NBTTagCompound nbtScheduledTick = nbtScheduledTicks.getCompoundTagAt(i);
            Block block;
            if (nbtScheduledTick.hasKey("i", Constants.NBT.TAG_STRING)) {
                block = Block.getBlockFromName(nbtScheduledTick.getString("i"));
            } else {
                block = Block.getBlockById(nbtScheduledTick.getInteger("i"));
            }
            if (block == null) {
                continue;
            }
            world.func_147446_b(
                nbtScheduledTick.getInteger("x"),
                nbtScheduledTick.getInteger("y"),
                nbtScheduledTick.getInteger("z"),
                block,
                nbtScheduledTick.getInteger("t"),
                nbtScheduledTick.getInteger("p"));
        }
    }

    private static void readLightingInfo(Cube cube, NBTTagCompound nbt, World world) {
        ILightingManager lightingManager = ((ICubicWorldInternal) cube.getWorld()).getLightingManager();
        String id = lightingManager.getId();
        String savedId = nbt.getString("LightingInfoType");
        if (!id.equals(savedId)) {
            cube.setInitialLightingDone(false);
            ExtendedBlockStorage storage = cube.getStorage();
            if (storage != null) {
                // noinspection ConstantConditions
                if (storage.getSkylightArray() != null) {
                    Arrays.fill(storage.getSkylightArray().data, (byte) 0);
                }
                Arrays.fill(storage.getBlocklightArray().data, (byte) 0);
            }
            cube.setSurfaceTracked(false);
            lightingManager.readFromNbt(cube, new NBTTagCompound());
            return;
        }
        NBTTagCompound lightingInfo = nbt.getCompoundTag("LightingInfo");
        lightingManager.readFromNbt(cube, lightingInfo);
    }

    private static void readBiomes(Cube cube, NBTTagCompound nbt) {// biomes
        if (nbt.hasKey("Biomes3D")) {
            cube.setBiomeArray(nbt.getByteArray("Biomes3D"));
        }
        if (nbt.hasKey("Biomes")) {
            cube.setBiomeArray(convertFromOldCubeBiomes(nbt.getByteArray("Biomes")));
        }
    }

    private static byte[] convertFromOldCubeBiomes(byte[] biomes) {
        byte[] newBiomes = new byte[4 * 4 * 4];
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    // NOTE: spread the biomes from 4 2x2 segments into the 4 vertical 4x4x4 segments
                    // this ensures that no biome data has been lost, but some of it may get arranged weirdly
                    newBiomes[AddressTools.getBiomeAddress3d(x, y, z)] = biomes[getOldBiomeAddress(
                        x << 1 | (y & 1),
                        z << 1 | ((y >> 1) & 1))];
                }
            }
        }
        return newBiomes;
    }

    public static int getOldBiomeAddress(int biomeX, int biomeZ) {
        return biomeX << 3 | biomeZ;
    }
}
