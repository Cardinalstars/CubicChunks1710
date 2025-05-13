package com.cardinalstar.cubicchunks.event.events;

import com.cardinalstar.cubicchunks.core.event.events.CubeEvent;
import com.cardinalstar.cubicchunks.core.world.cube.Cube;
import net.minecraft.nbt.NBTTagCompound;

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
