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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubeTerrainGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubicPopulator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.DependencyGraph;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.worldgen.VanillaCompatibilityGenerator;
import com.google.common.base.Preconditions;

public class CubeGeneratorsRegistry {

    /** List of populators added by other mods to vanilla compatibility generator type */
    private static final List<ICubicPopulator> customPopulatorsForFlatCubicGenerator = new ArrayList<ICubicPopulator>();
    private static final List<BiConsumer<? super World, ? super LoadingData<CubePos>>> cubeLoadingCallbacks = new ArrayList<>(
        2);
    private static final List<BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>>> columnLoadingCallbacks = new ArrayList<>(
        2);

    private static final Collection<BiConsumer<? super World, ? super LoadingData<CubePos>>> cubeLoadingCallbacksView = Collections
        .unmodifiableCollection(cubeLoadingCallbacks);
    private static final Collection<BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>>> columnLoadingCallbacksView = Collections
        .unmodifiableCollection(columnLoadingCallbacks);

    private static final DependencyGraph<ICubeTerrainGenerator<VanillaCompatibilityGenerator>> vanillaGenerators = new DependencyGraph<>();

    private static final DependencyGraph<ICubicPopulator> vanillaPopulators = new DependencyGraph<>();

    /**
     * Register a world generator that runs exclusively in the vanilla compatibility generator. This will not run for
     * other world types.
     */
    public static void registerVanillaGenerator(String name, ICubeTerrainGenerator<VanillaCompatibilityGenerator> generator, String... dependencies) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(generator);

        vanillaGenerators.addObject(name, generator);

        for (String dep : dependencies) {
            vanillaGenerators.addDependency(name, dep);
        }
    }

    /**
     * Adds a dependency between vanilla terrain generators. The dependency will be ran before the given generator.
     */
    public static void addVanillaGeneratorDependency(String generator, String dependency) {
        Preconditions.checkNotNull(generator);
        Preconditions.checkNotNull(dependency);

        vanillaGenerators.addDependency(generator, dependency);
    }

    public static void generateVanillaCube(VanillaCompatibilityGenerator generator, World world, Cube cube) {
        List<ICubeTerrainGenerator<VanillaCompatibilityGenerator>> generators = vanillaGenerators.sorted();

        for (int i = 0, generatorsSize = generators.size(); i < generatorsSize; i++) {
            ICubeTerrainGenerator<VanillaCompatibilityGenerator> cubeGen = generators.get(i);

            cubeGen.generate(generator, world, cube);
        }
    }

    /**
     * Register a world populator that runs exclusively in the vanilla compatibility populator. This will not run for
     * other world types.
     */
    public static void registerVanillaPopulator(String name, ICubicPopulator populator, String... dependencies) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(populator);

        vanillaPopulators.addObject(name, populator);

        for (String dep : dependencies) {
            vanillaPopulators.addDependency(name, dep);
        }
    }

    /**
     * Adds a dependency between vanilla terrain populators. The dependency will be ran before the given populator.
     */
    public static void addVanillaPopulatorDependency(String populator, String dependency) {
        Preconditions.checkNotNull(populator);
        Preconditions.checkNotNull(dependency);

        vanillaPopulators.addDependency(populator, dependency);
    }

    public static void populateVanillaCube(World world, CubePos pos) {
        List<ICubicPopulator> populators = vanillaPopulators.sorted();

        for (int i = 0, populatorsSize = populators.size(); i < populatorsSize; i++) {
            ICubicPopulator cubeGen = populators.get(i);

            cubeGen.generate(world, pos);
        }
    }

    /**
     * Registers a callback invoked after loading cube NBT from disk. This callback will get called even if no data is
     * found, potentially allowing
     * to prepare data for world generation asynchronously in a cache.
     *
     * @param cubeCallback the callback to be registered
     */
    public static void registerCubeAsyncLoadingCallback(BiConsumer<? super World, ? super LoadingData<CubePos>> cubeCallback) {
        cubeLoadingCallbacks.add(cubeCallback);
    }

    /**
     * Registers a callback invoked after loading column NBT from disk. This callback will get called even if no data is
     * found, potentially allowing
     * to prepare data for world generation asynchronously in a cache.
     *
     * @param columnCallback the callback to be registered
     */
    public static void registerColumnAsyncLoadingCallback(BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>> columnCallback) {
        columnLoadingCallbacks.add(columnCallback);
    }

    public static Collection<BiConsumer<? super World, ? super LoadingData<CubePos>>> getCubeAsyncLoadingCallbacks() {
        return cubeLoadingCallbacksView;
    }

    public static Collection<BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>>> getColumnAsyncLoadingCallbacks() {
        return columnLoadingCallbacksView;
    }
}
