package com.cardinalstar.cubicworlds.event.events;

import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.world.ChunkDataEvent;

public class CubeDataEvent extends CubeEvent
{

    private final NBTTagCompound data;

    public CubeDataEvent(Cube cube, NBTTagCompound data) {
        super(cube);
        this.data = data;
    }

    public static class Load extends CubeDataEvent
    {
        public Load(Cube cube, NBTTagCompound data)
        {
            super(cube, data);
        }
    }
}
