package com.cardinalstar.cubicchunks.util.biome3d;

import net.minecraft.world.biome.BiomeGenBase;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.biome3d.NaiveCompression.NaiveCompressionDataInput;
import com.cardinalstar.cubicchunks.util.biome3d.NaiveCompression.NaiveCompressionDataOutput;

/// This is a [BiomeArray] implementation that dynamically switches its backing storage to the most optimal format as
/// needed. By default it will use a [PalettizedBiomeArray], and once its palette is completely full, it will migrate
/// the data to a [ReferenceBiomeArray].
public class DynamicBiomeArray implements BiomeArray {

    private BiomeArray backing = new PalettizedBiomeArray();

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public BiomeGenBase put(int key, BiomeGenBase value) {
        try {
            // Try to insert the biome value into the storage
            backing.put(key, value);
        } catch (PaletteFullError e) {
            // If we're using a palette array and its palette is full, migrate the data
            BiomeArray reference = new ReferenceBiomeArray();

            int size = size();

            for (int i = 0; i < size; i++) {
                reference.put(i, backing.get(i));
            }

            backing = reference;

            backing.put(key, value);
        }

        // Boilerplate for Int2ObjectFunction.put
        return null;
    }

    @Override
    public BiomeGenBase get(int key) {
        return backing.get(key);
    }

    /// Changes the 'default' biome, since we don't want null biomes in these arrays
    @Override
    public void defaultReturnValue(BiomeGenBase rv) {
        backing.defaultReturnValue(rv);
    }

    @Override
    public BiomeGenBase defaultReturnValue() {
        return backing.defaultReturnValue();
    }

    public void write(CCPacketBuffer buffer) {
        NaiveCompression.compress(new NaiveCompressionDataInput() {

            @Override
            public int size() {
                return DynamicBiomeArray.this.size();
            }

            @Override
            public int get(int index) {
                return DynamicBiomeArray.this.get(index).biomeID;
            }
        }, buffer);
    }

    public void read(CCPacketBuffer buffer) {
        NaiveCompression.decompress(buffer, new NaiveCompressionDataOutput() {

            @Override
            public int size() {
                return DynamicBiomeArray.this.size();
            }

            @Override
            public void set(int index, int value) {
                DynamicBiomeArray.this.put(index, BiomeGenBase.getBiome(value));
            }
        });
    }
}
