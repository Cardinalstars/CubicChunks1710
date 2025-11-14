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

    private static class DepInfo {
        public final String dependency;
        public final boolean optional;

        public DepInfo(String dependency, boolean optional) {
            this.dependency = dependency;
            this.optional = optional;
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof DepInfo depInfo)) return false;

            return Objects.equals(dependency, depInfo.dependency);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(dependency);
        }
    }

    private final Object2ObjectOpenHashMap<String, T> objects = new Object2ObjectOpenHashMap<>();

    private final Multimap<String, DepInfo> dependencies = MultimapBuilder.hashKeys().hashSetValues().build();

    private List<T> cachedSorted;

    public void addObject(String name, T value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");

        objects.put(name, value);
        cachedSorted = null;
    }

    public static final String REQUIRES = "requires:";
    public static final String REQUIRED_BY = "required-by:";

    public void addUnparsedDependency(String object, String dep) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dep, "dep must not be null");

        boolean optional = dep.endsWith("?");

        if (optional) {
            dep = dep.substring(0, dep.length() - 1);
        }

        if (dep.startsWith(REQUIRES)) {
            dep = dep.substring(REQUIRES.length()).trim();
            dependencies.put(object, new DepInfo(dep, optional));
        } else if (dep.startsWith(REQUIRED_BY)) {
            dep = dep.substring(REQUIRED_BY.length()).trim();
            dependencies.put(dep, new DepInfo(object, optional));
        } else {
            dependencies.put(object, new DepInfo(dep, optional));
        }

        cachedSorted = null;
    }

    public void addDependency(String object, String dependsOn) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");

        dependencies.put(object, new DepInfo(dependsOn, false));
        cachedSorted = null;
    }

    public void removeDependency(String object, String dependsOn) {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");

        dependencies.remove(object, new DepInfo(dependsOn, false));
        cachedSorted = null;
    }

    public void addTarget(String targetName) {
        Objects.requireNonNull(targetName, "targetName must not be null");

        objects.put(targetName, null);
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

            iterdeps:
            while (iter.hasNext()) {
                String curr = iter.next();

                for (DepInfo dep : dependencies.get(curr)) {
                    if (!added.contains(dep.dependency)) {
                        continue iterdeps;
                    }
                }

                iter.remove();

                added.add(curr);

                T value = objects.get(curr);

                // Only targets are null
                if (value != null) {
                    out.add(value);
                }
            }
        }

        cachedSorted = ImmutableList.copyOf(out);

        return cachedSorted;
    }

    private void preventCyclicDeps(String node, boolean optional, Set<String> path) {
        if (path.contains(node)) {
            throw new IllegalStateException(node
                + " has a cyclic dependency with itself. The path is: " + path.stream().reduce("", (s, s2) -> s + ", " + s2));
        }

        if (!optional && !objects.containsKey(node)) {
            throw new IllegalStateException(node + " is present in the dependency graph but does not have a matching object");
        }

        path.add(node);

        for (DepInfo deps : dependencies.get(node)) {
            preventCyclicDeps(deps.dependency, deps.optional, path);
        }

        path.remove(node);
    }
}
