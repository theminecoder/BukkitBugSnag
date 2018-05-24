package me.theminecoder.bug.serverhandler;

import com.bugsnag.Severity;
import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerCommandException;
import com.destroystokyo.paper.exception.ServerEventException;
import com.destroystokyo.paper.exception.ServerSchedulerException;
import com.google.common.base.Joiner;
import me.theminecoder.bug.BukkitBugSnag;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static me.theminecoder.bug.BukkitBugSnag.*;

/**
 * @author Jackson
 * @version 1.0
 */
public class PaperSpigotHandler implements ServerHandler {
    @Override
    public boolean canUse(BukkitBugSnag plugin) {
        boolean canUse = false;
        try {
            Class.forName("com.destroystokyo.paper.event.server.ServerExceptionEvent");
            canUse = true;
        } catch (Throwable ignored) {
        }
        return canUse;
    }

    @Override
    public void init(BukkitBugSnag plugin) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerException(ServerExceptionEvent event) {
                System.out.println(event);
                Throwable e = event.getException();
                if (e.getCause() != null) {
                    e = e.getCause();
                }
                if (e instanceof EventException) {
                    e = e.getCause();
                }
                if (e instanceof CommandException) { // As of 2018-05-28 Paper doesn't fire command exceptions correctly.
                    e = e.getCause();
                }
                BukkitBugSnag.getBugsnagClient().notify(e, Severity.ERROR, error -> {
                    if (event.getException() instanceof ServerCommandException) {
                        ServerCommandException exception = (ServerCommandException) event.getException();
                        Command command = exception.getCommand();
                        error.addToTab(COMMAND_INFO_TAB, "Command", command.getName());
                        error.addToTab(COMMAND_INFO_TAB, "Full Command", Joiner.on(" ").join(exception.getArguments()));
                        error.addToTab(COMMAND_INFO_TAB, "Command Sender", exception.getCommandSender() + "");
                        if (command instanceof PluginCommand) {
                            PluginCommand pluginCommand = (PluginCommand) command;
                            error.addToTab(COMMAND_INFO_TAB, "Owning Plugin", pluginCommand.getPlugin().getName());
                        }
                    }

                    if (event.getException() instanceof ServerEventException) {
                        ServerEventException eventException = (ServerEventException) event.getException();
                        error.addToTab(EVENT_INFO_TAB, "Event Name", eventException.getEvent().getEventName());
                        error.addToTab(EVENT_INFO_TAB, "Is Async", !Bukkit.getServer().isPrimaryThread());
                        error.addToTab(EVENT_INFO_TAB, "Owning Plugin", JavaPlugin.getProvidingPlugin(eventException.getListener().getClass()).getName());
                        Map<String, Object> eventData = new HashMap<>();
                        Class eventClass = eventException.getEvent().getClass();
                        do {
                            if (eventClass != Event.class) { // Info already provided ^
                                for (Field field : eventClass.getDeclaredFields()) {
                                    if (field.getType() == HandlerList.class) {
                                        continue; // Unneeded Data
                                    }
                                    field.setAccessible(true);
                                    try {
                                        Object value = field.get(eventException.getEvent());
                                        if (value instanceof EntityDamageEvent.DamageModifier) {
                                            value = value.getClass().getCanonicalName() + "." + ((EntityDamageEvent.DamageModifier) value).name();
                                        }
                                        if (value instanceof Enum) {
                                            value = value.getClass().getCanonicalName() + "." + ((Enum) value).name();
                                        }
                                        eventData.put(field.getName(), value);
                                    } catch (IllegalAccessException ignored) {
                                    } catch (Throwable internalE) {
                                        eventData.put(field.getName(), "Error getting field data: " + internalE.getClass().getCanonicalName() + (internalE.getMessage() != null && internalE.getMessage().trim().length() > 0 ? ": " + internalE.getMessage() : ""));
                                    }
                                }
                            }
                            eventClass = eventClass.getSuperclass();
                        } while (eventClass != null);
                        error.addToTab(EVENT_INFO_TAB, "Event Data", eventData);
                    }

                    if (event.getException() instanceof ServerSchedulerException) {
                        ServerSchedulerException schedulerException = (ServerSchedulerException) event.getException();
                        error.addToTab(TASK_INFO_TAB, "Task ID", schedulerException.getTask().getTaskId());
                        error.addToTab(TASK_INFO_TAB, "Is Async", !Bukkit.getServer().isPrimaryThread());
                        error.addToTab(TASK_INFO_TAB, "Owning Plugin", schedulerException.getTask().getOwner().getName());
                    }
                });
            }
        }, plugin);
    }
}
