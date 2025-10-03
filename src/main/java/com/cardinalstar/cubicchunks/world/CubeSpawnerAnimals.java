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
package com.cardinalstar.cubicchunks.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.ForgeEventFactory;

import com.cardinalstar.cubicchunks.server.CubeWatcher;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import cpw.mods.fml.common.eventhandler.Event;

@ParametersAreNonnullByDefault
public class CubeSpawnerAnimals implements ISpawnerAnimals {

    private static final int CUBES_PER_CHUNK = 16;
    private static final int MOB_COUNT_DIV = (int) Math.pow(17.0D, 2.0D) * CUBES_PER_CHUNK;
    private static final int SPAWN_RADIUS = 8;

    @Nonnull
    private Set<CubePos> cubesForSpawn = new HashSet<>();

    @Override
    public int findChunksForSpawning(WorldServer world, boolean hostileEnable, boolean peacefulEnable,
        boolean spawnOnSetTickRate) {
        if (!hostileEnable && !peacefulEnable) {
            return 0;
        }
        this.cubesForSpawn.clear();

        int chunkCount = addEligibleCubes(world, this.cubesForSpawn);
        int totalSpawnCount = 0;

        for (EnumCreatureType mobType : EnumCreatureType.values()) {
            if (!shouldSpawnType(mobType, hostileEnable, peacefulEnable, spawnOnSetTickRate)) {
                continue;
            }
            int worldEntityCount = world.countEntities(mobType, true);
            int maxEntityCount = mobType.getMaxNumberOfCreature() * chunkCount / MOB_COUNT_DIV;

            if (worldEntityCount > maxEntityCount) {
                continue;
            }
            List<CubePos> shuffled = getShuffledCopy(this.cubesForSpawn)
                .subList(0, Math.min(this.cubesForSpawn.size(), 2 * (2 * SPAWN_RADIUS + 1)));
            totalSpawnCount += spawnCreatureTypeInAllChunks(mobType, world, shuffled);
        }
        return totalSpawnCount;
    }

    private int addEligibleCubes(WorldServer world, Set<CubePos> possibleCubes) {
        int chunkCount = 0;
        Random r = world.rand;
        Set<CubePos> allCubes = new HashSet<>();
        for (EntityPlayer player : world.playerEntities) {
            CubePos center = CubePos.fromEntity(player);

            for (int cubeXRel = -SPAWN_RADIUS; cubeXRel <= SPAWN_RADIUS; ++cubeXRel) {
                for (int cubeYRel = -SPAWN_RADIUS; cubeYRel <= SPAWN_RADIUS; ++cubeYRel) {
                    for (int cubeZRel = -SPAWN_RADIUS; cubeZRel <= SPAWN_RADIUS; ++cubeZRel) {
                        CubePos cubePos = center.add(cubeXRel, cubeYRel, cubeZRel);

                        if (allCubes.contains(cubePos)) {
                            continue;
                        }
                        assert !possibleCubes.contains(cubePos);
                        ++chunkCount;

                        boolean isEdge = cubeXRel == -SPAWN_RADIUS || cubeXRel == SPAWN_RADIUS
                            || cubeYRel == -SPAWN_RADIUS
                            || cubeYRel == SPAWN_RADIUS
                            || cubeZRel == -SPAWN_RADIUS
                            || cubeZRel == SPAWN_RADIUS;

                        if (isEdge) {
                            continue;
                        }
                        CubeWatcher cubeInfo = ((CubicPlayerManager) world.getPlayerManager()).getCubeWatcher(cubePos);

                        if (cubeInfo != null && cubeInfo.isSentToPlayers()) {
                            allCubes.add(cubePos);

                            // TODO Make this spawn culling more intelligent. Also
                            // maybe make this biased for the surface?
                            if (!cubeInfo.getCube()
                                .isEmpty()) {
                                possibleCubes.add(cubePos);
                            }
                        }
                    }
                }
            }
        }
        return chunkCount;
    }

    private int spawnCreatureTypeInAllChunks(EnumCreatureType mobType, WorldServer world, List<CubePos> cubeList) {
        ChunkCoordinates spawnPoint = world.getSpawnPoint();
        int posX, posY, posZ;

        int totalSpawned = 0;

        nextChunk: for (CubePos currentCubePos : cubeList) {
            BlockPos blockpos = getRandomCubePosition(world, currentCubePos);
            if (blockpos == null) {
                continue;
            }
            Block block = world.getBlock(blockpos.x, blockpos.y, blockpos.z);

            if (block.isNormalCube() || !(block.getMaterial() == mobType.getCreatureMaterial())) {
                continue;
            }
            int blockX = blockpos.getX();
            int blockY = blockpos.getY();
            int blockZ = blockpos.getZ();

            int currentPackSize = 0;

            for (int spawnAttempt = 0; spawnAttempt < 3; ++spawnAttempt) {
                int entityBlockX = blockX;
                int entityY = blockY;
                int entityBlockZ = blockZ;
                int searchRadius = 6;
                BiomeGenBase.SpawnListEntry biomeMobs = null;
                IEntityLivingData entityData = null;

                Random rand = world.rand;
                for (int attempt = 0; attempt < 4; ++attempt) {
                    entityBlockX += rand.nextInt(searchRadius) - rand.nextInt(searchRadius);
                    entityY += rand.nextInt(1) - rand.nextInt(1);
                    entityBlockZ += rand.nextInt(searchRadius) - rand.nextInt(searchRadius);
                    posX = entityBlockX;
                    posY = entityY;
                    posZ = entityBlockZ;

                    if (!SpawnerAnimals.canCreatureTypeSpawnAtLocation(mobType, world, posX, posY, posZ)) continue;

                    float entityX = (float) entityBlockX + 0.5F;
                    float entityZ = (float) entityBlockZ + 0.5F;

                    if (world.getClosestPlayer(entityX, entityY, entityZ, 24.0D) != null
                        || MathUtil.distanceSq(entityX, entityY, entityZ, spawnPoint) < 576.0D) {
                        continue;
                    }
                    if (biomeMobs == null) {
                        biomeMobs = world.spawnRandomCreature(mobType, posX, posY, posZ);

                        if (biomeMobs == null) {
                            break;
                        }
                    }

                    EntityLiving toSpawn;

                    try {
                        toSpawn = biomeMobs.entityClass.getConstructor(new Class[] { World.class })
                            .newInstance(world);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        // TODO: throw when entity creation fails
                        return totalSpawned;
                    }

                    toSpawn.setLocationAndAngles(entityX, entityY, entityZ, rand.nextFloat() * 360.0F, 0.0F);

                    Event.Result canSpawn = ForgeEventFactory.canEntitySpawn(toSpawn, world, entityX, entityY, entityZ);
                    if (canSpawn == Event.Result.ALLOW || (canSpawn == Event.Result.DEFAULT && toSpawn.getCanSpawnHere()
                        && toSpawn.getCanSpawnHere())) {
                        if (!ForgeEventFactory.doSpecialSpawn(toSpawn, world, entityX, entityY, entityZ)) {
                            entityData = toSpawn.onSpawnWithEgg(entityData);
                        }

                        if (toSpawn.getCanSpawnHere()) {
                            ++currentPackSize;
                            world.spawnEntityInWorld(toSpawn);
                        } else {
                            toSpawn.setDead();
                        }

                        if (blockZ >= ForgeEventFactory.getMaxSpawnPackSize(toSpawn)) {
                            continue nextChunk;
                        }
                    }

                    totalSpawned += currentPackSize;
                }
            }
        }
        return totalSpawned;
    }

    private static <T> ArrayList<T> getShuffledCopy(Collection<T> collection) {
        ArrayList<T> list = new ArrayList<>(collection);
        Collections.shuffle(list);
        return list;
    }

    private static boolean shouldSpawnType(EnumCreatureType type, boolean hostile, boolean peaceful,
        boolean spawnOnSetTickRate) {
        return !((type.getPeacefulCreature() && !peaceful) || (!type.getPeacefulCreature() && !hostile)
            || (type.getAnimal() && !spawnOnSetTickRate));
    }

    @Nullable
    private static BlockPos getRandomCubePosition(WorldServer world, CubePos pos) {
        int blockX = pos.getMinBlockX() + world.rand.nextInt(Cube.SIZE);
        int blockZ = pos.getMinBlockZ() + world.rand.nextInt(Cube.SIZE);

        int height = world.getHeightValue(blockX, blockZ);
        if (pos.getMinBlockY() > height) {
            return null;
        }
        int blockY = pos.getMinBlockY() + world.rand.nextInt(Cube.SIZE);
        return new BlockPos(blockX, blockY, blockZ);
    }
}
