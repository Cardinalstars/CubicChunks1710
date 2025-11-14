package com.cardinalstar.cubicchunks.event.events;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

public class ColumnEvent extends WorldEvent {

    public final ChunkCoordIntPair pos;

    private ColumnEvent(World world, ChunkCoordIntPair pos) {
        super(world);
        this.pos = pos;
    }

    public static class LoadNBT extends ColumnEvent {

        public NBTTagCompound tag;

        public LoadNBT(World world, ChunkCoordIntPair pos, NBTTagCompound tag) {
            super(world, pos);
            this.tag = tag;
        }
    }
}
