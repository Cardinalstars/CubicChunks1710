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
import java.util.Random;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubeTerrainGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubicPopulator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.worldgen.MapGenCavesCubic;
import com.cardinalstar.cubicchunks.worldgen.VanillaCompatibilityGenerator;
import com.github.bsideup.jabel.Desugar;
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

    private static final TreeSet<GeneratorWrapper<VanillaCompatibilityGenerator>> sortedVanillaGeneratorList = new TreeSet<>();

    private static final TreeSet<PopulatorWrapper> sortedPopulatorList = new TreeSet<>();

    private static final TreeSet<PopulatorWrapper> sortedVanillaPopulatorList = new TreeSet<>();

    @Desugar
    private record GeneratorWrapper<T extends ICubeGenerator>(ICubeTerrainGenerator<T> generator, int weight) implements Comparable<GeneratorWrapper<T>> {

        @Override
        public int compareTo(GeneratorWrapper<T> o) {
            return Integer.compare(weight, o.weight);
        }
    }

    @Desugar
    private record PopulatorWrapper(ICubicPopulator populator, int weight) implements Comparable<PopulatorWrapper> {

        @Override
        public int compareTo(PopulatorWrapper o) {
            return Integer.compare(weight, o.weight);
        }
    }

    /**
     * Register a world generator that runs exclusively in the vanilla compatibility generator. This will not run for
     * other world types.
     */
    public static void registerVanillaGenerator(ICubeTerrainGenerator<VanillaCompatibilityGenerator> generator, int priority) {
        Preconditions.checkNotNull(generator);
        sortedVanillaGeneratorList.add(new GeneratorWrapper<>(generator, priority));
    }

    /**
     * Callback hook for cube gen - if your mod wishes to add extra mod related
     * generation to the world call this
     *
     * @param generator The generator that invoked this event
     * @param cube The cube to generate
     */
    public static void generateVanillaCube(VanillaCompatibilityGenerator generator, World world, Cube cube) {
        for (GeneratorWrapper<VanillaCompatibilityGenerator> wrapper : sortedVanillaGeneratorList) {
            wrapper.generator.generate(generator, world, cube);
        }
    }

    /**
     * Register a world populator - something that inserts new block types into the world on population stage
     *
     * @param populator the populator
     * @param weight    a weight to assign to this populator. Heavy weights tend to sink to the bottom of
     *                  list of world populator (i.e. they run later)
     */
    public static void registerVanillaPopulator(ICubicPopulator populator, int weight) {
        Preconditions.checkNotNull(populator);
        sortedVanillaPopulatorList.add(new PopulatorWrapper(populator, weight));
    }

    /**
     * Callback hook for cube gen - if your mod wishes to add extra mod related
     * generation to the world call this
     *
     * @param world  The {@link ICubicWorld} we're generating for
     * @param pos    is position of the populated cube
     */
    public static void populateVanillaWorld(World world, CubePos pos) {
        for (PopulatorWrapper wrapper : sortedVanillaPopulatorList) {
            wrapper.populator.generate(world, pos);
        }
    }

    /**
     * Register a world populator - something that inserts new block types into the world on population stage
     *
     * @param populator the populator
     * @param weight    a weight to assign to this populator. Heavy weights tend to sink to the bottom of
     *                  list of world populator (i.e. they run later)
     */
    public static void registerPopulator(ICubicPopulator populator, int weight) {
        Preconditions.checkNotNull(populator);
        sortedPopulatorList.add(new PopulatorWrapper(populator, weight));
    }

    /**
     * Callback hook for cube gen - if your mod wishes to add extra mod related
     * generation to the world call this
     *
     * @param random the cube specific {@link Random}.
     * @param pos    is position of the populated cube
     * @param world  The {@link ICubicWorld} we're generating for
     */
    public static void populateWorld(World world, Random random, CubePos pos) {
        for (PopulatorWrapper wrapper : sortedPopulatorList) {
            wrapper.populator.generate(world, pos);
        }
    }

    /**
     * Populators added here will be launched prior to any other. It is
     * recommended to use this function in init or pre init event of a mod.
     *
     * @param populator populator instance to register
     */
    public static void registerForCompatibilityGenerator(ICubicPopulator populator) {
        if (!customPopulatorsForFlatCubicGenerator.contains(populator))
            customPopulatorsForFlatCubicGenerator.add(populator);
    }

    static {
        registerVanillaGenerator(new MapGenCavesCubic(), 1);
    }

    public static void populateVanillaCubic(World world, ICube cube) {
        for (ICubicPopulator populator : customPopulatorsForFlatCubicGenerator) {
            populator.generate(world, cube.getCoords());
        }
    }

    /**
     * Registers a callback invoked after loading cube NBT from disk. This callback will get called even if no data is
     * found, potentially allowing
     * to prepare data for world generation asynchronously in a cache.
     *
     * @param cubeCallback the callback to be registered
     */
    public static void registerCubeAsyncLoadingCallback(
        BiConsumer<? super World, ? super LoadingData<CubePos>> cubeCallback) {
        cubeLoadingCallbacks.add(cubeCallback);
    }

    /**
     * Registers a callback invoked after loading column NBT from disk. This callback will get called even if no data is
     * found, potentially allowing
     * to prepare data for world generation asynchronously in a cache.
     *
     * @param columnCallback the callback to be registered
     */
    public static void registerColumnAsyncLoadingCallback(
        BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>> columnCallback) {
        columnLoadingCallbacks.add(columnCallback);
    }

    public static Collection<BiConsumer<? super World, ? super LoadingData<CubePos>>> getCubeAsyncLoadingCallbacks() {
        return cubeLoadingCallbacksView;
    }

    public static Collection<BiConsumer<? super World, ? super LoadingData<ChunkCoordIntPair>>> getColumnAsyncLoadingCallbacks() {
        return columnLoadingCallbacksView;
    }
}
