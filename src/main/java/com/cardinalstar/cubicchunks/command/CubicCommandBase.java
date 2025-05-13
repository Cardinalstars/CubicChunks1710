package com.cardinalstar.cubicchunks.command;

import net.minecraft.command.CommandBase;

public abstract class CubicCommandBase extends CommandBase
{
    private final PermissionLevel requiredPermissionLevel;

    public CubicCommandBase(PermissionLevel permissionLevelRequired)
    {
        this.requiredPermissionLevel = permissionLevelRequired;
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return requiredPermissionLevel.ordinal();
    }

    public static enum PermissionLevel
    {
        ALL,
        OP,
        NONE
    }
}
