package com.cardinalstar.cubicchunks.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
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

    public static void sync(ServerTickEvent event) {
        if (event.phase != Phase.END) return;
        if (!dirty.compareAndSet(true, false)) return;

//        List<VisualizedBox> boxes = new ArrayList<>();
//
//        cubeStatus.forEach((pos, status) -> {
//            boxes.add(new VisualizedBox(
//                switch (status) {
//                    case None -> new Color(100, 50, 100);
//                    case Generated -> new Color(50, 200, 50);
//                    case Populated -> new Color(50, 50, 200);
//                    case Lit -> new Color(200, 200, 50);
//                    case Synced -> new Color(200, 50, 50);
//                },
//                new AABBf(
//                    pos.getMinBlockX(), pos.getMinBlockY(), pos.getMinBlockZ(),
//                    pos.getMaxBlockX(), pos.getMaxBlockY(), pos.getMaxBlockZ())
//            ));
//        });
//
//        for (EntityPlayerMP player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
//            BoxVisualizer.sendBoxes(player, Duration.ofMinutes(5), boxes, true);
//        }
    }

    public static void put(CubePos pos, CubeStatus status) {
        cubeStatus.put(pos, status);
        dirty.set(true);
    }

    public static void cmpexc(CubePos pos, CubeStatus expected, CubeStatus desired) {
        cubeStatus.compute(pos, (key, existing) -> existing == expected ? desired : existing);
        dirty.set(true);
    }

    public static void remove(CubePos pos) {
        cubeStatus.remove(pos);
        dirty.set(true);
    }
}
