package com.cardinalstar.cubicchunks.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

public class DependencyGraph<T> {

    private final Object2ObjectOpenHashMap<String, T> objects = new Object2ObjectOpenHashMap<>();

    private final Multimap<String, String> dependencies = MultimapBuilder.hashKeys().hashSetValues().build();

    private List<T> cachedSorted;

    public void addObject(String name, T value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");

        objects.put(name, value);
        cachedSorted = null;
    }

    public void addDependency(String object, String dependsOn) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");

        dependencies.put(object, dependsOn);
        cachedSorted = null;
    }

    public void removeDependency(String object, String dependsOn) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");

        dependencies.remove(object, dependsOn);
        cachedSorted = null;
    }

    public List<T> sorted() {
        if (cachedSorted != null) return cachedSorted;

        ObjectLinkedOpenHashSet<String> path = new ObjectLinkedOpenHashSet<>();

        for (String node : dependencies.keys()) {
            preventCyclicDeps(node, path);
        }

        List<T> out = new ArrayList<>();
        ObjectLinkedOpenHashSet<String> added = new ObjectLinkedOpenHashSet<>();

        ObjectLinkedOpenHashSet<String> remaining = new ObjectLinkedOpenHashSet<>(objects.keySet());

        while (!remaining.isEmpty()) {
            Iterator<String> iter = remaining.iterator();

            iterdeps:
            while (iter.hasNext()) {
                String curr = iter.next();

                for (String dep : dependencies.get(curr)) {
                    if (!added.contains(dep)) {
                        continue iterdeps;
                    }
                }

                iter.remove();

                added.add(curr);
                out.add(objects.get(curr));
            }
        }

        cachedSorted = ImmutableList.copyOf(out);

        return cachedSorted;
    }

    private void preventCyclicDeps(String node, Set<String> path) {
        if (path.contains(node)) {
            throw new IllegalStateException(node
                + " has a cyclic dependency with itself. The path is: " + path.stream().reduce("", (s, s2) -> s + ", " + s2));
        }

        if (!objects.containsKey(node)) {
            throw new IllegalStateException(node + " is present in the dependency graph but does not have a matching object");
        }

        path.add(node);

        for (String deps : dependencies.get(node)) {
            preventCyclicDeps(deps, path);
        }

        path.remove(node);
    }
}
