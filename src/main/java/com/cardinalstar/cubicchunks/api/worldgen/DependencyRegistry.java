package com.cardinalstar.cubicchunks.api.worldgen;

import java.util.List;

public interface DependencyRegistry<T> {

    void register(String name, T value, String... deps);

    void registerTarget(String name, String... deps);

    void addDependency(String from, String to);

    void removeDependency(String from, String to);

    List<T> sorted();
}
