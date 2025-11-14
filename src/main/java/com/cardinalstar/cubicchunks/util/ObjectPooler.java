package com.cardinalstar.cubicchunks.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Copied from gtnhlib, with some modifications */
public class ObjectPooler<T> {

    private final Supplier<T> instanceSupplier;
    private final Consumer<T> resetter;
    private final ObjectArrayList<T> availableInstances;

    private final int maxStored;

    public ObjectPooler(Supplier<T> instanceSupplier) {
        this(instanceSupplier, null, Integer.MAX_VALUE);
    }

    public ObjectPooler(Supplier<T> instanceSupplier, Consumer<T> resetter, int maxStored) {
        this.instanceSupplier = instanceSupplier;
        this.resetter = resetter;
        this.maxStored = maxStored;
        this.availableInstances = new ObjectArrayList<>();
    }

    public final void clear() {
        this.availableInstances.clear();
    }

    public final T getInstance() {
        if (this.availableInstances.isEmpty()) {
            return this.instanceSupplier.get();
        }
        return this.availableInstances.pop();
    }

    public final void releaseInstance(T instance) {
        if (instance == null) return;
        if (this.availableInstances.size() < maxStored) {
            if (resetter != null) resetter.accept(instance);
            this.availableInstances.add(instance);
        }
    }

    public final void releaseInstances(Collection<T> instances) {
        instances.forEach(this::releaseInstance);
        instances.clear();
    }

    /**
     * Uses arraycopy instead of a loop. Faster, but doesn't check that the input is nonnull. Use with care!
     */
    public final void releaseInstances(@Nonnull T[] instances) {
        this.availableInstances.addElements(availableInstances.size(), instances);
        Arrays.fill(instances, null);
    }
}
