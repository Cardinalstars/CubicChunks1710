package com.cardinalstar.cubicchunks.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class was partially taken from forge 1.12 and rewired to fit my needs.
 */
public abstract class SubCommandBase extends CommandBase
{

    private final Map<String, ICommand> commandMap = new HashMap<>();
    private final Map<String, ICommand> commandAliasMap = new HashMap<>();

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
            return getListOfStringsMatchingLastWord(keys, args);
        }

        ICommand cmd = getSubCommand(args[0]);

        if(cmd != null)
        {
            return cmd.getTabCompletions(server, sender, shiftArgs(args), pos);
        }

        return super.getTabCompletions(server, sender, args, pos);
    }
}
