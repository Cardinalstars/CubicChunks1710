package com.cardinalstar.cubicchunks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber
public class WorldDiagnostics {

    @SubscribeEvent
    public static void onRenderGameOverlayTextEvent(RenderGameOverlayEvent.Text event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) {

            int cX = Coords.blockToCube(MathHelper.floor_double(mc.thePlayer.posX));
            int cY = Coords.blockToCube(MathHelper.floor_double(mc.thePlayer.posY));
            int cZ = Coords.blockToCube(MathHelper.floor_double(mc.thePlayer.posZ));

            Cube cube = (Cube) ((ICubicWorld) mc.theWorld).getCubeFromCubeCoords(cX, cY, cZ);

            event.left.add("");

            event.left.add("Cube X: " + cX);
            event.left.add("Cube Y: " + cY);
            event.left.add("Cube Z: " + cZ);

            event.left.add("");

            event.left.add("Cube: " + cube);
        }
    }
}
