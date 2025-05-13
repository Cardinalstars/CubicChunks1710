package com.cardinalstar.cubicchunks.world.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;

public class CubicRegionFile
{
    private final int[] chunkTimestamps;
    private long lastModified;
    private final File file;
    private RandomAccessFile dataFile;
    private final IStorageFormat format;
    private BitSet sectorUsed;
    private final int SECTOR_SIZE = 4096;

    public CubicRegionFile(File file, IStorageFormat format)
    {
        this.file = file;
        this.format = format;
        this.chunkTimestamps = new int[format.getNumEntries()];
        try
        {
            if (file.exists())
            {
                this.lastModified = file.lastModified();
            }

            this.dataFile = new RandomAccessFile(file, "rw");

            // Write header if there is none
            if (dataFile.length() < 4096L)
            {
                for (int i = 0; i < this.format.getNumEntries() / 4; ++i)
                {
                    this.dataFile.writeInt(0);
                }
            }

            // Padding to sector length
            long pad = this.dataFile.length() & 4095L;
            if (pad != 0) {
                this.dataFile.seek(this.dataFile.length());
                for (int i = 0; i < SECTOR_SIZE - pad; i++) {
                    this.dataFile.write(0);
                }
            }

            // Set header sectors to used
            this.sectorUsed = new BitSet((int)this.dataFile.length() / SECTOR_SIZE);
            for (int i = 0; i < ((this.format.getHeaderEntrySizeBytes() * this.format.getNumEntries())/SECTOR_SIZE); i ++)
            {
                this.sectorUsed.set(i);
            }

            // Begin to read header in
            this.dataFile.seek(0L);
            for (int i = 0; i < this.format.getNumEntries(); i++)
            {
                IStorageFormat.Entry entry = this.format.parseEntry(this.dataFile.readInt());
                sectorUsed.set(entry.locationOffset, entry.locationSize);
            }

            for (int i  = 0; i < this.format.getNumEntries(); i++)
            {
                this.chunkTimestamps[i] = this.dataFile.readInt();
            }

        }
        catch (IOException ioexception)
        {
            ioexception.printStackTrace();
        }
    }
}
