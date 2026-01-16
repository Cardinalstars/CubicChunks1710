package com.cardinalstar.cubicchunks.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.registry.IDependencyGraph;
import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class DependencyGraph<T> implements IDependencyGraph<T> {

    @Desugar
    private record DepInfo(String dependency, boolean optional) {

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DepInfo depInfo)) return false;

            return Objects.equals(dependency, depInfo.dependency);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(dependency);
        }
    }

    private final Object2ObjectOpenHashMap<String, Optional<T>> objects = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<String> targets = new ObjectOpenHashSet<>();

    private final Multimap<String, DepInfo> dependencies = MultimapBuilder.hashKeys()
        .hashSetValues()
        .build();

    private List<T> cachedSorted;

    public DependencyGraph() {
        objects.defaultReturnValue(null);
    }

    @Override
    public void addObject(String name, T value, String... deps) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");

        objects.put(name, Optional.of(value));
        cachedSorted = null;

        for (String dep : deps) {
            addUnparsedDependency(name, dep);
        }
    }

    public void addUnparsedDependency(String object, String dep) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dep, "dep must not be null");

        boolean optional = dep.endsWith("?");

        if (optional) {
            dep = dep.substring(0, dep.length() - 1);
        }

        if (dep.startsWith(REQUIRES)) {
            dep = dep.substring(REQUIRES.length())
                .trim();
            dependencies.put(object, new DepInfo(dep, optional));
        } else if (dep.startsWith(REQUIRED_BY)) {
            dep = dep.substring(REQUIRED_BY.length())
                .trim();
            dependencies.put(dep, new DepInfo(object, optional));
        } else if (dep.startsWith(AFTER)) {
            dep = dep.substring(AFTER.length())
                .trim();
            dependencies.put(object, new DepInfo(dep, true));
        } else if (dep.startsWith(BEFORE)) {
            dep = dep.substring(BEFORE.length())
                .trim();
            dependencies.put(dep, new DepInfo(object, true));
        } else {
            throw new IllegalArgumentException(
                "Invalid dependency specification for object '" + object + "': '" + dep + "'");
        }

        cachedSorted = null;
    }

    @Override
    public void addDependency(String object, String dependsOn, boolean optional) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");

        dependencies.put(object, new DepInfo(dependsOn, optional));
        cachedSorted = null;
    }

    @Override
    public boolean removeDependency(String object, String dependsOn) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");

        boolean successful = dependencies.remove(object, new DepInfo(dependsOn, false));
        cachedSorted = null;

        return successful;
    }

    @Override
    public void addTarget(String targetName, String... deps) {
        Objects.requireNonNull(targetName, "targetName must not be null");

        objects.put(targetName, Optional.empty());
        targets.add(targetName);

        for (String dep : deps) {
            addUnparsedDependency(targetName, dep);
        }
    }

    /// Not made public because there is no well-defined initialization timing.
    @Nullable
    public Optional<T> get(String name) {
        return objects.get(name);
    }

    public List<T> sorted() {
        if (cachedSorted != null) return cachedSorted;

        ObjectLinkedOpenHashSet<String> path = new ObjectLinkedOpenHashSet<>();

        for (String node : dependencies.keys()) {
            preventCyclicDeps(node, false, path);
        }

        List<T> out = new ArrayList<>();
        ObjectLinkedOpenHashSet<String> added = new ObjectLinkedOpenHashSet<>();

        ObjectLinkedOpenHashSet<String> remaining = new ObjectLinkedOpenHashSet<>(objects.keySet());
        while (!remaining.isEmpty()) {
            Iterator<String> iter = remaining.iterator();

            iterdeps: while (iter.hasNext()) {
                String curr = iter.next();

                for (DepInfo dep : dependencies.get(curr)) {
                    if (!added.contains(dep.dependency)) {
                        continue iterdeps;
                    }
                }

                iter.remove();

                added.add(curr);

                Optional<T> value = objects.get(curr);

                if (value == null) {
                    throw new IllegalStateException("Missing value for key: " + curr);
                }

                value.ifPresent(out::add);
            }
        }

        cachedSorted = ImmutableList.copyOf(out);

        return cachedSorted;
    }

    private void preventCyclicDeps(String node, boolean optional, Set<String> path) {
        if (path.contains(node)) {
            throw new IllegalStateException(
                node + " has a cyclic dependency with itself. The path is: "
                    + path.stream()
                        .reduce("", (s, s2) -> s + ", " + s2));
        }

        if (!optional && !targets.contains(node) && !objects.containsKey(node)) {
            throw new IllegalStateException(
                node + " is present in the dependency graph but does not have a matching object");
        }

        path.add(node);

        for (DepInfo deps : dependencies.get(node)) {
            preventCyclicDeps(deps.dependency, deps.optional, path);
        }

        path.remove(node);
    }
}
