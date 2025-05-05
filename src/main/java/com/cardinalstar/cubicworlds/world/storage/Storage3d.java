package com.cardinalstar.cubicworlds.world.storage;

import java.util.BitSet;

// Storage 3d is used to hold chunks and block data
public class Storage3d implements IStorageFormat
{
    public static final int LOCATION_BITS = 23;
    public static final int LOCATION_MASK = (1 << LOCATION_BITS) - 1;
    public static final int ENTRIES_PER_REGION = 4096; // 16x16x16 regions
    public static final int HEADER_ENTRY_SIZE_BYTES = 4;


    @Override
    public String getRegionFileKey() {
        return null;
    }

    @Override
    public int getHeaderEntrySizeBytes() {
        return HEADER_ENTRY_SIZE_BYTES;
    }

    @Override
    public int getNumEntries() {
        return ENTRIES_PER_REGION;
    }

    @Override
    public Entry parseEntry(int readInt)
    {
        int length = readInt >>> LOCATION_BITS;
        int offset = readInt & LOCATION_MASK;

        return new Entry(offset, length);
    }
}
