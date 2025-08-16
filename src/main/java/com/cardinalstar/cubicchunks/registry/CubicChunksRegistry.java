package com.cardinalstar.cubicchunks.registry;

import net.minecraft.util.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CubicChunksRegistry<V> {
    private final Map<ResourceLocation, V> entries = new HashMap<>();

    public void register(ResourceLocation name, V entry) {
        if (entries.containsKey(name)) {
            throw new IllegalStateException("Duplicate entry: " + name);
        }
        entries.put(name, entry);
    }

    public V get(ResourceLocation name) {
        return entries.get(name);
    }

    public Collection<V> getAll() {
        return entries.values();
    }
}
