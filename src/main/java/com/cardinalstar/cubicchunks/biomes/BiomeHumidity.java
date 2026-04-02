package com.cardinalstar.cubicchunks.biomes;

import java.util.Arrays;

public enum BiomeHumidity {
    ARID(0.1),
    DRY(0.25),
    NORMAL(0.5),
    HUMID(0.7),
    FOGGY(0.85),
    WATER(1d);

    private static final BiomeHumidity[] VALUES = values();
    private final double amount;

    BiomeHumidity(double amount) {
        this.amount = amount;
    }

    public static BiomeHumidity get(double humidity) {
        for (BiomeHumidity h : VALUES) {
            if (humidity <= h.amount) return h;
        }

        return BiomeHumidity.FOGGY;
    }

    public static BiomeHumidity[] between(BiomeHumidity low, BiomeHumidity high) {
        return Arrays.copyOfRange(VALUES, low.ordinal(), high.ordinal() + 1);
    }
}
