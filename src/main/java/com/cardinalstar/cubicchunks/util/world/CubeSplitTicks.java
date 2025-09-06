package com.cardinalstar.cubicchunks.util.world;

import com.cardinalstar.cubicchunks.util.CubePos;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.NextTickListEntry;

import java.util.Collection;

public class CubeSplitTicks {

    private final Object2ObjectOpenHashMap<CubePos, ObjectOpenHashSet<NextTickListEntry>> byCube = new Object2ObjectOpenHashMap<>();

    public ObjectOpenHashSet<NextTickListEntry> getForCube(CubePos coords) {
        return byCube.get(coords);
    }

    public void remove(NextTickListEntry entry)
    {
        CubePos pos = CubePos.fromBlockCoords(entry.xCoord, entry.yCoord, entry.zCoord);
        ObjectOpenHashSet<NextTickListEntry> set = byCube.get(pos);
        set.remove(entry);
        if (set.isEmpty()) {
            byCube.remove(pos);
        }
    }
    public boolean add(NextTickListEntry entry) {
        CubePos pos = CubePos.fromBlockCoords(entry.xCoord, entry.yCoord, entry.zCoord);
        return byCube
            .computeIfAbsent(pos, x -> new ObjectOpenHashSet<>())
            .add(entry);
    }
}
