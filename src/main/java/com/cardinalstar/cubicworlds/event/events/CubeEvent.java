package com.cardinalstar.cubicworlds.event.events;

import com.cardinalstar.cubicworlds.world.cube.Cube;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

public class CubeEvent extends WorldEvent
{
    public CubeEvent(Cube cube) {
        super(cube.worldObj);
    }
}
