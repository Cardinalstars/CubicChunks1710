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
package com.cardinalstar.cubicchunks.api.worldgen;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.cube.Cube;

@ParametersAreNonnullByDefault
public interface IWorldGenerator {

    /**
     * Generate a new cube
     *
     * @param cubeX the cube's X coordinate
     * @param cubeY the cube's Y coordinate
     * @param cubeZ the cube's Z coordinate
     *
     * @return A CubePrimer with the generated blocks
     */
    GenerationResult<Cube> provideCube(Chunk chunk, int cubeX, int cubeY, int cubeZ);

    /**
     * Generate column-global information such as biome data
     */
    GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ);

    /**
     * Populate a cube with multi-block structures that can cross cube boundaries such as trees and ore veins.
     * <p />
     * Note: Unlike vanilla this method will NEVER cause recursive generation, thus the area that it populates is not as
     * strict.
     * Generation should still be restricted as the player might see something generate in a chunk they have already
     * been sent.
     * <p />
     * This method should generate all cubes that may be affected by the population of {@code cube} before population.
     * It is also responsible for calling {@link Cube#setPopulated(boolean)} as necessary.
     *
     * @param cube The cube to populate
     */
    void populate(Cube cube);

    /**
     * Called to reload structures that apply to {@code cube}. Mostly used to prepare calls to
     * {@link IWorldGenerator#getPossibleCreatures(EnumCreatureType, int, int, int)} <br>
     *
     * @param cube The cube being loaded
     *
     * @see IWorldGenerator#recreateStructures(Chunk) for the 2D-equivalent of this method
     */
    void recreateStructures(ICube cube);

    /**
     * Called to reload structures that apply to {@code column}. Mostly used to prepare calls to
     * {@link IWorldGenerator#getPossibleCreatures(EnumCreatureType, int, int, int)} <br>
     *
     * @param column The column being loaded
     *
     * @see IWorldGenerator#recreateStructures(ICube) for the 3D-equivalent of this method
     */
    void recreateStructures(Chunk column);

    /**
     * Retrieve a list of creature classes eligible for spawning at the specified location.
     *
     * @param type the creature type that we are interested in spawning
     * @param x    the x position we want to spawn creatures at
     * @param y    the y position we want to spawn creatures at
     * @param z    the z position we want to spawn creatures at
     *
     * @return a list of creature classes that can spawn here. Example: Calling this method inside a nether fortress
     *         returns EntityBlaze, EntityPigZombie, EntitySkeleton, and EntityMagmaCube
     */
    List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z);

    /**
     * Gets the closest structure with name {@code name}. This is primarily used when an eye of ender is trying to find
     * a stronghold.
     *
     * @param name the name of the structure
     *
     * @return the position of the structure, or {@code null} if none could be found
     */
    @Nullable
    ChunkPosition getNearestStructure(String name, int x, int y, int z);
}
