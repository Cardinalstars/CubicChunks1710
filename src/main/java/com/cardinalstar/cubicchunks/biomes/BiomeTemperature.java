package com.cardinalstar.cubicchunks.biomes;

import java.util.Arrays;

public enum BiomeTemperature {
    FREEZING(0.1),
    COLD(0.25),
    TEMPERATE(0.5),
    WARM(0.7),
    HOT(0.8),
    BURNING(0.9),
    LAVA(1d);

    private static final BiomeTemperature[] VALUES = values();
    private final double amount;

    BiomeTemperature(double amount) {
        this.amount = amount;
    }

    public static BiomeTemperature get(double temperature) {
        for (BiomeTemperature h : VALUES) {
            if (temperature <= h.amount) return h;
        }

        return BiomeTemperature.LAVA;
    }

    public static BiomeTemperature[] between(BiomeTemperature low, BiomeTemperature high) {
        return Arrays.copyOfRange(VALUES, low.ordinal(), high.ordinal() + 1);
    }
}
