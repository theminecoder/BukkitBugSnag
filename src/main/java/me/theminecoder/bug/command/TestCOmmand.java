package me.theminecoder.bug.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * @author theminecoder
 * @version 1.0
 */
public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        commandSender.sendMessage(ChatColor.GREEN+"This will throw a test exception to verify if bugsnag works and is configured correctly.");
        throw new RuntimeException("Test Exception");
    }
}
