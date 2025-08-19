package com.cardinalstar.cubicchunks.util;

import net.minecraft.util.EnumFacing;
import org.joml.Vector3i;

public class DirectionUtils
{

    public static EnumFacing getOpposite(EnumFacing dir)
    {
        return switch (dir.ordinal()) {
            case (0) -> EnumFacing.UP;
            case (1) -> EnumFacing.DOWN;
            case (2) -> EnumFacing.SOUTH;
            case (3) -> EnumFacing.NORTH;
            case (4) -> EnumFacing.EAST;
            default -> EnumFacing.WEST;
        };
    }

    public static Vector3i getDirectionVec(EnumFacing dir)
    {
        return switch (dir.ordinal()) {
            case (0) -> new Vector3i(0, -1, 0);
            case (1) -> new Vector3i(0, 1, 0);
            case (2) -> new Vector3i(0, 0, -1);
            case (3) -> new Vector3i(0, 0, 1);
            case (4) -> new Vector3i(-1, 0, 0);
            default -> new Vector3i(1, 0, 0);
        };
    }
}
