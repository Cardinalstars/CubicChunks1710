package com.cardinalstar.cubicchunks.server;

import com.cardinalstar.cubicchunks.core.world.cube.Cube;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import java.io.File;
import java.io.IOException;

public class CubicAnvilChunkLoader extends AnvilChunkLoader {
    public CubicAnvilChunkLoader(File file) {
        super(file);
    }

    @Override
    public Chunk loadChunk(World world, int x, int z) throws IOException
    {
        Object[] data = this.loadChunk__Async(world, x, z);

        if (data != null)
        {
            Chunk chunk = (Chunk) data[0];
            NBTTagCompound nbttagcompound = (NBTTagCompound) data[1];
            this.loadEntities(world, nbttagcompound.getCompoundTag("Level"), chunk);
            return chunk;
        }

        return null;
    }

    @Override
    public Object[] loadChunk__Async(World p_75815_1_, int p_75815_2_, int p_75815_3_) throws IOException
    {
        return null;
    }

    public Cube loadCube(World world) throws IOException
    {

    }


    @Override
    protected Chunk checkedReadChunkFromNBT(World p_75822_1_, int p_75822_2_, int p_75822_3_, NBTTagCompound p_75822_4_)
    {
        return null;
    }

    @Override
    protected Object[] checkedReadChunkFromNBT__Async(World p_75822_1_, int p_75822_2_, int p_75822_3_, NBTTagCompound p_75822_4_)
    {
        return null;
    }

    @Override
    public void saveChunk(World p_75816_1_, Chunk p_75816_2_) throws MinecraftException, IOException
    {

    }

    @Override
    protected void addChunkToPending(ChunkCoordIntPair p_75824_1_, NBTTagCompound p_75824_2_)
    {

    }

    @Override
    public boolean writeNextIO()
    {
        return false;
    }




}
