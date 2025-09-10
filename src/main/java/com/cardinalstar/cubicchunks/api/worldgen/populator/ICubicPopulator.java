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
package com.cardinalstar.cubicchunks.api.worldgen.populator;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.CubeGeneratorsRegistry;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;

/**
 * Implement this interface to your world generators and register them in
 * {@link CubeGeneratorsRegistry} to launch them
 * single time for each generated cube right after terrain and biome specific
 * generators.
 */
public interface ICubicPopulator {

    /**
     * Generate a specific populator feature for a given cube given a biome.
     *
     * To avoid requiring unnecessary amount of Cubes to do population, the population space is offset by 8 blocks in
     * each direction. Instead of generating blocks only in this cube you should generate then in 16x16x16 block space
     * starting from the center of current cube.
     *
     * Example of generating random position coordinate:
     *
     * {@code int x = random.nextInt(16) + 8 + pos.getXCenter();}
     *
     * You can also use {@link CubePos#randomPopulationPos} to generate random position in population space.
     *
     * All block access should be done through the provided {@link ICubicWorld} instance.
     *
     * @param world The {@link World} we're generating for.
     * @param pos   The position of the cube being populated.
     *
     */
    void generate(World world, CubePos pos);
}
