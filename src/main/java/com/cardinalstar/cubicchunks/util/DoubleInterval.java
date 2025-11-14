package com.cardinalstar.cubicchunks.util;

public class DoubleInterval {

    public final double min, max;

    public DoubleInterval(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }
}
