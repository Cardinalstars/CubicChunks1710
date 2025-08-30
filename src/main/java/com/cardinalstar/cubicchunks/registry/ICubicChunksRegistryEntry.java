package com.cardinalstar.cubicchunks.registry;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

/**
 * This class is basically a simplified recreation of the forge registry system from 1.12. I liked how it works
 * and keeps things organized, so I wanted to do it as well.
 * 
 * @param <V> The base class of the registries.
 */
public interface ICubicChunksRegistryEntry<V> {

    V setRegistryName(ResourceLocation name);

    @Nullable
    ResourceLocation getRegistryName();

    Class<V> getRegistryType();
}
