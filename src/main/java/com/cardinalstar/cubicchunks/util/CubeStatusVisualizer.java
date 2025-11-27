package com.cardinalstar.cubicchunks.util;

import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.joml.primitives.AABBd;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
// import com.gtnewhorizon.gtnhlib.visualization.BoxVisualizer;
// import com.gtnewhorizon.gtnhlib.visualization.VisualizedBox;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;

@EventBusSubscriber
public class CubeStatusVisualizer {

    public enum CubeStatus {
        None,
        Generated,
        Populated,
        Lit,
        Dirty,
        Synced
    }

    private static final ConcurrentHashMap<CubePos, CubeStatus> cubeStatus = new ConcurrentHashMap<>();
    private static final AtomicBoolean dirty = new AtomicBoolean();

    private static boolean wasSent = false;

    public static void reset() {
        cubeStatus.clear();
        dirty.set(false);
    }

    @SubscribeEvent
    public static void sync(ServerTickEvent event) {
        if (event.phase != Phase.END) return;
        if (!dirty.compareAndSet(true, false)) return;

        if (!CubicChunksConfig.enableChunkStatusDebugging) {
            if (wasSent) {
                wasSent = false;
                for (EntityPlayerMP player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
                    // BoxVisualizer.sendBoxes(player, Duration.ofMinutes(0), new ArrayList<>(), true);
                }
            }

            return;
        }

        // List<VisualizedBox> boxes = new ArrayList<>();

        cubeStatus.forEach((pos, status) -> {
            // boxes.add(new VisualizedBox(
            //     switch (status) {
            //         case None -> new Color(100, 50, 100, 50);
            //         case Generated -> new Color(50, 200, 50, 50);
            //         case Populated -> new Color(50, 50, 200, 50);
            //         case Lit -> new Color(200, 200, 50, 50);
            //         case Dirty -> new Color(14, 229, 187, 50);
            //         case Synced -> new Color(200, 50, 50, 50);
            //     },
            //     new AABBd(
            //         pos.getMinBlockX() - 0.5, pos.getMinBlockY() + 0.5, pos.getMinBlockZ() - 0.5,
            //         pos.getMaxBlockX() - 0.5, pos.getMaxBlockY() - 0.5, pos.getMaxBlockZ() - 0.5)
            // ));
        });

        wasSent = true;

        for (EntityPlayerMP player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            // BoxVisualizer.sendBoxes(player, Duration.ofMinutes(5), boxes, true);
        }
    }

    public static void put(CubePos pos, CubeStatus status) {
        if (pos.getY() != 4) return;

        cubeStatus.put(pos, status);
        dirty.set(true);
    }

    public static void cmpexc(CubePos pos, CubeStatus expected, CubeStatus desired) {
        if (pos.getY() != 4) return;

        cubeStatus.compute(pos, (key, existing) -> existing == expected ? desired : existing);
        dirty.set(true);
    }

    public static void remove(CubePos pos) {
        if (pos.getY() != 4) return;

        cubeStatus.remove(pos);
        dirty.set(true);
    }
}
