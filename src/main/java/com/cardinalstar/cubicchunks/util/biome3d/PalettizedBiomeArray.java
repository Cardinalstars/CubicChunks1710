package com.cardinalstar.cubicchunks.util.biome3d;

import java.util.Arrays;

import net.minecraft.world.biome.BiomeGenBase;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class PalettizedBiomeArray implements BiomeArray {

    private int freePaletteIndices = ~0b1;
    private final BiomeGenBase[] palette = new BiomeGenBase[16];
    private final Object2IntOpenHashMap<BiomeGenBase> paletteReversed = new Object2IntOpenHashMap<>();

    {
        paletteReversed.defaultReturnValue(-1);
        paletteReversed.put(null, 0);
        palette[0] = null;
    }

    private final byte[] data = new byte[16 * 16 * 16 / 2];

    @Override
    public boolean isEmpty() {
        for (byte b : data) {
            if (b != 0) return false;
        }

        return true;
    }

    @Override
    public int size() {
        return 16 * 16 * 16;
    }

    @Override
    public void clear() {
        Arrays.fill(data, (byte) 0);
    }

    @Override
    public BiomeGenBase put(int key, BiomeGenBase value) {
        int paletteIndex = paletteReversed.getInt(value);

        if (paletteIndex == -1) {
            int free = Integer.lowestOneBit(freePaletteIndices);

            if (free >= 16) {
                cleanPalette();

                free = Integer.lowestOneBit(freePaletteIndices);

                if (free >= 16) {
                    throw new PaletteFullError();
                }
            }

            paletteIndex = free;
            palette[free] = value;
            paletteReversed.put(value, free);
            freePaletteIndices &= ~(0b1 << paletteIndex);
        }

        byte b = data[key >> 1];

        if ((key & 0b1) == 0) {
            b = (byte) ((b & 0xF0) | paletteIndex);
        } else {
            b = (byte) ((paletteIndex << 4) | (b & 0xF));
        }

        data[key >> 1] = b;

        return null;
    }

    @Override
    public BiomeGenBase get(int key) {
        byte b = data[key >> 1];

        if ((key & 0b1) == 0) {
            b >>= 4;
        }

        b &= 0xF;

        return palette[b];
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
        freePaletteIndices = ~0b1;

        for (byte b : data) {
            int lower = b & 0xF;
            int upper = (b >> 4) & 0xF;

            freePaletteIndices &= ~(0b1 << lower);
            freePaletteIndices &= ~(0b1 << upper);
        }

        paletteReversed.clear();

        // default biome
        paletteReversed.put(palette[0], 0);

        for (int i = 1; i < 16; i++) {
            if ((freePaletteIndices & (0b1 << i)) != 0) {
                palette[i] = null;
            } else {
                if (palette[i] != null) {
                    paletteReversed.put(palette[i], i);
                }
            }
        }
    }
}
