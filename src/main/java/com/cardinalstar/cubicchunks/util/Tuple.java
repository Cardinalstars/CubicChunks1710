package com.cardinalstar.cubicchunks.util;

// Class that actually uses typed objects as first and second.
public class Tuple<T, V> {

    /** First Object in the Tuple */
    private final T first;
    /** Second Object in the Tuple */
    private final V second;

    public Tuple(T first, V second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Get the first Object in the Tuple
     */
    public T getFirst() {
        return this.first;
    }

    /**
     * Get the second Object in the Tuple
     */
    public V getSecond() {
        return this.second;
    }
}
