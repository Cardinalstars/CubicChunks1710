package com.cardinalstar.cubicchunks.api.worldgen.impl;

import java.util.List;

import com.cardinalstar.cubicchunks.api.worldgen.DependencyRegistry;
import com.cardinalstar.cubicchunks.util.DependencyGraph;
import com.google.common.base.Preconditions;

public class StandardDependencyRegistry<T> implements DependencyRegistry<T> {

    private final DependencyGraph<T> graph = new DependencyGraph<>();

    @Override
    public void register(String name, T value, String... deps) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);

        graph.addObject(name, value);

        for (String dep : deps) {
            graph.addUnparsedDependency(name, dep);
        }
    }

    @Override
    public void registerTarget(String name, String... deps) {
        Preconditions.checkNotNull(name);

        graph.addTarget(name);

        for (String dep : deps) {
            graph.addUnparsedDependency(name, dep);
        }
    }

    @Override
    public void addDependency(String from, String to) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(to);

        graph.addDependency(from, to);
    }

    @Override
    public void removeDependency(String from, String to) {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(to);

        graph.removeDependency(from, to);
    }

    @Override
    public List<T> sorted() {
        return graph.sorted();
    }
}
