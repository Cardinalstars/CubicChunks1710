package com.cardinalstar.cubicchunks.world.storage;

// Storage 2d is basically a normal minecraft chunk, but it doesn't have block data present.
public class Storage2d implements IStorageFormat {

    public static final int LOCATION_BITS = 24; // 0-31
    // Used to convert the world chunk position to the position inside of the region file.
    public static final int LOCATION_MASK = (1 << LOCATION_BITS) - 1;
    public static final int HEADER_ENTRY_SIZE_BYTES = 4;
    public static final int ENTRIES_PER_REGION = 1024; // 1024

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
    public Entry parseEntry(int readInt) {
        int length = readInt >>> LOCATION_BITS;
        int offset = readInt & LOCATION_MASK;

        return new Entry(offset, length);
    }
}
