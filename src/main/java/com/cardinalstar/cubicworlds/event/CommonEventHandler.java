package com.cardinalstar.cubicworlds.event;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

@EventBusSubscriber
public class CommonEventHandler
{
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        if (event.world.isRemote) // Attach client world on join.
        {
            return;
        }
        World world = event.world;

    }
}
