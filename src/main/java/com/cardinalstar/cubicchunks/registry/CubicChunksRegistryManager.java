package com.cardinalstar.cubicchunks.registry;

import java.util.HashMap;
import java.util.Map;

public class CubicChunksRegistryManager {

    private final Map<Class<?>, CubicChunksRegistry<?>> registries = new HashMap<>();

    public <V> void registerRegistry(Class<V> clazz, CubicChunksRegistry<V> registry) {
        registries.put(clazz, registry);
    }

    @SuppressWarnings("unchecked")
    public <V> CubicChunksRegistry<V> getRegistry(Class<V> clazz) {
        return (CubicChunksRegistry<V>) registries.get(clazz);
    }
}
