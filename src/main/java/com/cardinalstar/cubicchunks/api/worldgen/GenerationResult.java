package com.cardinalstar.cubicchunks.api.worldgen;

import java.util.List;

import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.collect.ImmutableList;

public class GenerationResult<T> {

    public final T object;
    public final ImmutableList<Chunk> columnSideEffects;
    public final ImmutableList<Cube> cubeSideEffects;

    public GenerationResult(T object) {
        this.object = object;
        this.columnSideEffects = ImmutableList.of();
        this.cubeSideEffects = ImmutableList.of();
    }

    public GenerationResult(T object, List<Chunk> columnSideEffects, List<Cube> cubeSideEffects) {
        this.object = object;
        this.columnSideEffects = columnSideEffects == null ? ImmutableList.of() : ImmutableList.copyOf(columnSideEffects);
        this.cubeSideEffects = cubeSideEffects == null ? ImmutableList.of() : ImmutableList.copyOf(cubeSideEffects);
    }
}
