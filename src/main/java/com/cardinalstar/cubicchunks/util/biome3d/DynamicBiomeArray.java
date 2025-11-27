package com.cardinalstar.cubicchunks.util.biome3d;

import net.minecraft.world.biome.BiomeGenBase;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class DynamicBiomeArray implements BiomeArray {

    private BiomeArray backing = new PalettizedBiomeArray();

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public int size() {
        return 16 * 16 * 16;
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public BiomeGenBase put(int key, BiomeGenBase value) {
        try {
            backing.put(key, value);
        } catch (PaletteFullError e) {
            BiomeArray reference = new ReferenceBiomeArray();

            int size = size();

            for (int i = 0; i < size; i++) {
                reference.put(i, backing.get(i));
            }

            backing = reference;

            backing.put(key, value);
        }

        return null;
    }

    @Override
    public BiomeGenBase get(int key) {
        return backing.get(key);
    }

    @Override
    public void defaultReturnValue(BiomeGenBase rv) {
        backing.defaultReturnValue(rv);
    }

    @Override
    public BiomeGenBase defaultReturnValue() {
        return backing.defaultReturnValue();
    }

    private static final byte ADD_PALLETE = 0;
    private static final byte DATA = 1;

    public void write(CCPacketBuffer buffer) {
        Object2IntOpenHashMap<BiomeGenBase> palette = new Object2IntOpenHashMap<>();

        palette.defaultReturnValue(-1);

        int start = buffer.writerIndex();

        int ops = 0;

        for (int i = 0; i < size(); i++) {
            BiomeGenBase curr = get(i);

            int paletteIndex = palette.getInt(curr);

            if (paletteIndex == -1) {
                paletteIndex = palette.size();
                palette.put(curr, paletteIndex);

                buffer.writeByte(ADD_PALLETE);
                buffer.writeVarIntToBuffer(curr.biomeID);
                ops++;
            }

            int i2 = i + 1;

            while (i2 < size() && get(i2) == curr) {
                i2++;
            }

            int len = i2 - i;

            buffer.writeByte(DATA);
            buffer.writeVarIntToBuffer(paletteIndex);
            buffer.writeVarIntToBuffer(len);
            ops++;
        }

        int end = buffer.writerIndex();

        buffer.writerIndex(start);
        buffer.writeInt(ops);

        buffer.writerIndex(end);
    }

    public void read(CCPacketBuffer buffer) {
        int ops = buffer.readInt();

        ObjectArrayList<BiomeGenBase> palette = new ObjectArrayList<>();

        int i = 0;

        for (int op = 0; op < ops; op++) {
            byte insn = buffer.readByte();
            switch (insn) {
                case ADD_PALLETE -> {
                    BiomeGenBase biome = BiomeGenBase.getBiome(buffer.readVarIntFromBuffer());

                    palette.add(biome);
                }
                case DATA -> {
                    BiomeGenBase biome = palette.get(buffer.readVarIntFromBuffer());
                    int len = buffer.readVarIntFromBuffer();

                    for (int k = 0; k < len; k++) {
                        put(i + k, biome);
                    }

                    i += len;
                }
                default -> {
                    throw new InvalidBiomeDataException("Unexpected operation " + insn + " at index" + buffer.readerIndex());
                }
            }
        }

        if (i != size()) throw new InvalidBiomeDataException("Expected " + size() + " biomes indices, got " + i);
    }
}
