package com.cardinalstar.cubicchunks.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

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
        return this.requiredPermissionLevel.ordinal();
    }

    public PermissionLevel getRequiredPermissionEnum()
    {
        return this.requiredPermissionLevel;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        if (sender instanceof EntityPlayer) {
            return canCommandSenderUseCommand(sender);
        } else {
            return super.canCommandSenderUseCommand(sender);
        }
    }

    public static enum PermissionLevel
    {
        ALL,
        OP,
        NONE
    }
}
