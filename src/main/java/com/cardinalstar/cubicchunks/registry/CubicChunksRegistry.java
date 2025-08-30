package com.cardinalstar.cubicchunks.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

public class CubicChunksRegistry<V> {

    private final Map<ResourceLocation, V> entries = new HashMap<>();

    public CubicChunksRegistry<V> register(ResourceLocation name, V entry) {
        if (entries.containsKey(name)) {
            throw new IllegalStateException("Duplicate entry: " + name);
        }
        entries.put(name, entry);
        return this;
    }

    public V get(ResourceLocation name) {
        return entries.get(name);
    }

    public Collection<V> getAll() {
        return entries.values();
    }
}
