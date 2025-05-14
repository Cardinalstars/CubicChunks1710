package com.cardinalstar.cubicchunks.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class was partially taken from forge 1.12 and rewired to fit my needs.
 */
public abstract class SubCommandBase extends CubicCommandBase
{

    private final Map<String, ICommand> commandMap = new HashMap<>();
    private final Map<String, ICommand> commandAliasMap = new HashMap<>();

    public SubCommandBase(PermissionLevel permissionLevelRequired) {
        super(permissionLevelRequired);
    }

    public void addSubcommand(ICommand commandToAdd)
    {
        commandMap.put(commandToAdd.getCommandName(), commandToAdd);
        for (String alias : commandToAdd.getCommandAliases())
        {
            commandAliasMap.put(alias, commandToAdd);
        }
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args)
    {
        if (args.length < 1)
        {
            String subCommandsString = getAvailableSubCommandsString(sender);
            sender.addChatMessage(new ChatComponentTranslation("commands.tree_base.available_subcommands", subCommandsString));
        }
        else
        {
            ICommand cmd = getSubCommand(args[0]);

            if(cmd == null)
            {
                String subCommandsString = getAvailableSubCommandsString(sender);
                throw new CommandException("commands.tree_base.invalid_cmd.list_subcommands", args[0], subCommandsString);
            }
            else if(!cmd.canCommandSenderUseCommand(sender))
            {
                throw new CommandException("commands.generic.permission");
            }
            else
            {
                cmd.processCommand(sender, shiftArgs(args));
            }
        }
    }

    public Collection<ICommand> getSubCommands()
    {
        return commandMap.values();
    }

    @Nullable
    public ICommand getSubCommand(String command)
    {
        ICommand cmd = commandMap.get(command);
        if (cmd != null)
        {
            return cmd;
        }
        return commandAliasMap.get(command);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args)
    {
        if(args.length == 1)
        {
            List<String> keys = new ArrayList<>();

            for (ICommand c : getSubCommands())
            {
                if(c.canCommandSenderUseCommand(sender))
                {
                    keys.add(c.getCommandName());
                }
            }

            keys.sort(null);
            return getListOfStringsFromIterableMatchingLastWord(args, keys);
        }

        ICommand cmd = getSubCommand(args[0]);

        if(cmd != null)
        {
            return cmd.addTabCompletionOptions(sender, shiftArgs(args));
        }

        return super.addTabCompletionOptions(sender, args);
    }

    private static String[] shiftArgs(@Nullable String[] s)
    {
        if(s == null || s.length == 0)
        {
            return new String[0];
        }

        String[] s1 = new String[s.length - 1];
        System.arraycopy(s, 1, s1, 0, s1.length);
        return s1;
    }

    private String getAvailableSubCommandsString(ICommandSender sender)
    {
        Collection<String> availableCommands = new ArrayList<>();
        for (ICommand command : getSubCommands())
        {
            if (command.canCommandSenderUseCommand(sender))
            {
                availableCommands.add(command.getCommandName());
            }
        }
        return CommandBase.joinNiceStringFromCollection(availableCommands);
    }
}
