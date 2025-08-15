package com.cardinalstar.cubicchunks.util;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import net.minecraft.entity.Entity;

public class ChunkPos
{
    public final int x;
    public final int z;

    public ChunkPos(int x, int z)
    {
        this.x = x;
        this.z = z;
    }

    public ChunkPos(BlockPos pos)
    {
        this.x = pos.getX() >> 4;
        this.z = pos.getZ() >> 4;
    }

    public static long asLong(int x, int z)
    {
        return (long)x & 4294967295L | ((long)z & 4294967295L) << 32;
    }

    public int hashCode()
    {
        int i = 1664525 * this.x + 1013904223;
        int j = 1664525 * (this.z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    public boolean equals(Object p_equals_1_)
    {
        if (this == p_equals_1_)
        {
            return true;
        }
        else if (!(p_equals_1_ instanceof ChunkPos))
        {
            return false;
        }
        else
        {
            ChunkPos chunkpos = (ChunkPos)p_equals_1_;
            return this.x == chunkpos.x && this.z == chunkpos.z;
        }
    }

    public double getDistanceSq(Entity entityIn)
    {
        double d0 = (double)(this.x * 16 + 8);
        double d1 = (double)(this.z * 16 + 8);
        double d2 = d0 - entityIn.posX;
        double d3 = d1 - entityIn.posZ;
        return d2 * d2 + d3 * d3;
    }

    public int getXStart()
    {
        return this.x << 4;
    }

    public int getZStart()
    {
        return this.z << 4;
    }

    public int getXEnd()
    {
        return (this.x << 4) + 15;
    }

    public int getZEnd()
    {
        return (this.z << 4) + 15;
    }

    public BlockPos getBlock(int x, int y, int z)
    {
        return new BlockPos((this.x << 4) + x, y, (this.z << 4) + z);
    }

    public String toString()
    {
        return "[" + this.x + ", " + this.z + "]";
    }
}
