package com.cardinalstar.cubicchunks.util.biome3d;

import java.util.Arrays;

import net.minecraft.world.biome.BiomeGenBase;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class PalettizedBiomeArray implements BiomeArray {

    private final boolean[] usedPaletteIndices = new boolean[16];
    private final BiomeGenBase[] palette = new BiomeGenBase[16];
    private final Object2IntOpenHashMap<BiomeGenBase> paletteReversed = new Object2IntOpenHashMap<>();

    public PalettizedBiomeArray() {
        paletteReversed.defaultReturnValue(-1);
        paletteReversed.put(null, 0);
        palette[0] = null;
        usedPaletteIndices[0] = true;
    }

    private final byte[] nibbleArray = new byte[16 * 16 * 16 / 2];

    @Override
    public boolean isEmpty() {
        for (byte b : nibbleArray) {
            if (b != 0) return false;
        }

        return true;
    }

    @Override
    public void clear() {
        Arrays.fill(nibbleArray, (byte) 0);
    }

    @Override
    public BiomeGenBase put(int key, BiomeGenBase value) {
        int paletteIndex = paletteReversed.getInt(value);

        if (paletteIndex == -1) {
            int free = -1;

            for (int i = 0; i < usedPaletteIndices.length; i++) {
                boolean b = usedPaletteIndices[i];

                if (b) continue;

                free = i;
                break;
            }

            if (free >= 16) {
                cleanPalette();

                for (int i = 0; i < usedPaletteIndices.length; i++) {
                    boolean b = usedPaletteIndices[i];

                    if (b) continue;

                    free = i;
                    break;
                }

                if (free >= 16) {
                    throw new PaletteFullError();
                }
            }

            paletteIndex = free;
            palette[free] = value;
            paletteReversed.put(value, free);
            usedPaletteIndices[paletteIndex] = true;
        }

        byte b = nibbleArray[key >> 1];

        if ((key & 0b1) == 0) {
            b = (byte) ((b & 0xF0) | paletteIndex);
        } else {
            b = (byte) ((paletteIndex << 4) | (b & 0xF));
        }

        nibbleArray[key >> 1] = b;

        return null;
    }

    @Override
    public BiomeGenBase get(int key) {
        int index = key >> 1;

        int id = (key & 1) == 0 ? (this.nibbleArray[index] & 0xF) : (this.nibbleArray[index] >> 4 & 0xF);

        return palette[id];
    }

    @Override
    public void defaultReturnValue(BiomeGenBase rv) {
        paletteReversed.removeInt(defaultReturnValue());

        palette[0] = rv;

        paletteReversed.put(rv, 0);
    }

    @Override
    public BiomeGenBase defaultReturnValue() {
        return palette[0];
    }

    public void cleanPalette() {
        Arrays.fill(usedPaletteIndices, false);
        usedPaletteIndices[0] = true;

        for (byte b : nibbleArray) {
            int lower = b & 0xF;
            int upper = (b >> 4) & 0xF;

            usedPaletteIndices[lower] = true;
            usedPaletteIndices[upper] = true;
        }

        paletteReversed.clear();

        // default biome
        paletteReversed.put(palette[0], 0);

        for (int i = 1; i < 16; i++) {
            if (!usedPaletteIndices[i]) {
                palette[i] = null;
            } else {
                if (palette[i] != null) {
                    paletteReversed.put(palette[i], i);
                }
            }
        }
    }
}
