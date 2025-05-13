package com.cardinalstar.cubicchunks.world.storage;

import java.util.BitSet;

// A storage format has all the relevant methods for reading in and writing to locations in a region file
public interface IStorageFormat
{
    String getRegionFileKey();

    // Returns the size in bytes, of each header entry.
    int getHeaderEntrySizeBytes();

    // Should be a multiple of 4
    int getNumEntries();

    Entry parseEntry(int readInt);

    public static class Entry
    {
        // Which sector does this entry refer to
        public int locationOffset;

        // How many sectors does this entry refer to
        public int locationSize;

        public Entry(int locationOffset, int locationSize)
        {
            this.locationOffset = locationOffset;
            this.locationSize = locationSize;
        }
    }
}
