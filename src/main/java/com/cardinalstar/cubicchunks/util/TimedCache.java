package com.cardinalstar.cubicchunks.util;

import java.time.Duration;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.LongObjectMutablePair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class TimedCache<K, V> {

    private final PriorityQueue<LongObjectMutablePair<K>> timing = new PriorityQueue<>(Comparator.comparingLong(LongObjectMutablePair::leftLong));

    public final Object2ObjectOpenHashMap<K, V> values = new Object2ObjectOpenHashMap<>();

    private final Function<K, V> fetcher;

    private final long timeoutNS;
    @Nullable
    private final BiConsumer<K, V> release;
    private final Function<K, K> clone;

    private long lastCleanup;

    private static final long CLEANUP_INTERVAL = Duration.ofMillis(1).toNanos();

    public TimedCache(Function<K, V> fetcher, Duration timeout, @Nullable BiConsumer<K, V> release, Function<K, K> clone) {
        this.fetcher = fetcher;
        this.timeoutNS = timeout.toNanos();
        this.release = release;
        this.clone = clone;
    }

    public final V get(K key) {
        V value = values.get(key);

        if (value == null) {
            long now = System.nanoTime();

            doCleanup(now);

            if (clone != null) {
                key = clone.apply(key);
            }

            value = fetcher.apply(key);

            values.put(key, value);
            timing.add(LongObjectMutablePair.of(now, key));
        }

        return value;
    }

    public final void doCleanup() {
        doCleanup(System.nanoTime());
    }

    private void doCleanup(long now) {
        if (!timing.isEmpty() && (now - lastCleanup) > CLEANUP_INTERVAL) {
            lastCleanup = now;

            LongObjectMutablePair<K> front;

            while ((front = timing.peek()) != null && front.leftLong() + timeoutNS < now) {
                V value = values.remove(front.right());
                timing.poll();

                if (release != null) release.accept(front.right(), value);
            }
        }
    }

    public final void clear() {
        timing.clear();
        values.clear();
    }
}
