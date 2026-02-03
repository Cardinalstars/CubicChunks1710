package com.cardinalstar.cubicchunks.api.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class Registry<V> {

    private final Map<UniqueIdentifier, V> entries = new HashMap<>();

    @SuppressWarnings("UnusedReturnValue")
    public Registry<V> register(UniqueIdentifier name, V entry) {
        if (entries.containsKey(name)) {
            throw new IllegalStateException("Duplicate entry: " + name);
        }
        entries.put(name, entry);
        return this;
    }

    public V get(UniqueIdentifier name) {
        return entries.get(name);
    }

    public Collection<V> getAll() {
        return entries.values();
    }
}
