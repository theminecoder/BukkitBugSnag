package me.theminecoder.bug.command;

import me.theminecoder.bug.BukkitBugSnag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author theminecoder
 * @version 1.0
 */
public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        BukkitBugSnag plugin = JavaPlugin.getPlugin(BukkitBugSnag.class);

        commandSender.sendMessage(ChatColor.GREEN+"This will throw a test exception in all of the supported exception areas to verify if bugsnag works and is configured correctly.");
        BukkitBugSnag.getBugsnagClient().notify(new RuntimeException("Manual Exception Test"));
        Bukkit.getScheduler().runTask(plugin, ()-> {
            throw new RuntimeException("Task Exception Test");
        });
        Listener listener = new Listener() {
            @EventHandler(ignoreCancelled = true)
            public void onExceptionTest(ExceptionTestEvent event) {
                throw new RuntimeException("Event Exception Test");
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        Bukkit.getPluginManager().callEvent(new ExceptionTestEvent());
        HandlerList.unregisterAll(listener);
        throw new RuntimeException("Command Exception Test");
    }

    @EventHandler(ignoreCancelled = true)
    public void onExceptionTest(ExceptionTestEvent event) {
    }

    private static class ExceptionTestEvent extends Event {
        private static final HandlerList handlers = new HandlerList();

        public HandlerList getHandlers() {
            return handlers;
        }

        public static HandlerList getHandlerList() {
            return handlers;
        }
    }

}
