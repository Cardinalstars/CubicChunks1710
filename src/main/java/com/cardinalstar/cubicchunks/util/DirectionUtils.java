package com.cardinalstar.cubicchunks.util;

import com.gtnewhorizon.gtnhlib.client.renderer.quad.Axis;
import net.minecraft.util.EnumFacing;
import org.joml.Vector3i;

public class DirectionUtils
{

    public static EnumFacing getOpposite(EnumFacing dir)
    {
        return switch (dir) {
            case DOWN -> EnumFacing.UP;
            case UP -> EnumFacing.DOWN;
            case NORTH -> EnumFacing.SOUTH;
            case SOUTH -> EnumFacing.NORTH;
            case WEST -> EnumFacing.EAST;
            case EAST -> EnumFacing.WEST;
        };
    }

    public static Vector3i getDirectionVec(EnumFacing dir)
    {
        return switch (dir) {
            case DOWN -> new Vector3i(0, -1, 0);
            case UP -> new Vector3i(0, 1, 0);
            case NORTH -> new Vector3i(0, 0, -1);
            case SOUTH -> new Vector3i(0, 0, 1);
            case EAST -> new Vector3i(-1, 0, 0);
            case WEST -> new Vector3i(1, 0, 0);
        };
    }

    public static Axis fromFacing(EnumFacing dir)
    {
        return switch (dir) {
            case DOWN, UP -> Axis.Y;
            case NORTH, SOUTH -> Axis.Z;
            case WEST, EAST -> Axis.X;
        };
    }
}
