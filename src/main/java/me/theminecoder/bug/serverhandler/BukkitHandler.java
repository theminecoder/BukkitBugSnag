package me.theminecoder.bug.serverhandler;

import com.bugsnag.MetaData;
import me.theminecoder.bug.BukkitBugSnag;
import me.theminecoder.bug.proxy.LoggedCommandMap;
import me.theminecoder.bug.proxy.LoggedPluginManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static me.theminecoder.bug.BukkitBugSnag.COMMAND_INFO_TAB;
import static me.theminecoder.bug.BukkitBugSnag.EVENT_INFO_TAB;

/**
 * @author Jackson
 * @version 1.0
 */
public class BukkitHandler implements ServerHandler {
    @Override
    public boolean canUse(BukkitBugSnag plugin) {
        return true;
    }

    @Override
    public void init(BukkitBugSnag plugin) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            if (Modifier.isFinal(commandMapField.getModifiers())) {
                Field modifierField = Field.class.getDeclaredField("modifiers");
                modifierField.setAccessible(true);
                modifierField.set(commandMapField, commandMapField.getModifiers() & ~Modifier.FINAL);
            }
            commandMapField.set(Bukkit.getServer(), new LoggedCommandMap((SimpleCommandMap) commandMapField.get(Bukkit.getServer())) {
                @SuppressWarnings("unused")
                private Map<String, Command> knownCommands; //Hack for original knownCommands reflection.

                @Override
                protected void setKnownCommands(Map<String, Command> knownCommands) {
                    this.knownCommands = knownCommands;
                }

                @Override
                protected void customHandler(Command command, String commandLine, Throwable e) {
                    MetaData metaData = new MetaData();
                    metaData.addToTab(COMMAND_INFO_TAB, "Command", command.getName());
                    metaData.addToTab(COMMAND_INFO_TAB, "Full Command", commandLine);
                    if (command instanceof PluginCommand) {
                        PluginCommand pluginCommand = (PluginCommand) command;
                        metaData.addToTab(COMMAND_INFO_TAB, "Owning Plugin", pluginCommand.getPlugin().getName());
                    }
                    BukkitBugSnag.getBugsnagClient().notify(e.getCause(), "error", metaData);
                }
            });
        } catch (Throwable e) {
            plugin.getLogger().severe("Could not register proxy commandMap");
            e.printStackTrace();
        }

        try {
            Field pluginManagerField = Bukkit.getServer().getClass().getDeclaredField("pluginManager");
            pluginManagerField.setAccessible(true);
            if (Modifier.isFinal(pluginManagerField.getModifiers())) {
                Field modifierField = Field.class.getDeclaredField("modifiers");
                modifierField.setAccessible(true);
                modifierField.set(pluginManagerField, pluginManagerField.getModifiers() & ~Modifier.FINAL);
            }
            pluginManagerField.set(Bukkit.getServer(), new LoggedPluginManager(plugin) {
                private Object timings;
                private final Map<String, Permission> permissions = new HashMap();
                private final Map<Boolean, Set<Permission>> defaultPerms = new LinkedHashMap();
                private final Map<String, Map<Permissible, Boolean>> permSubs = new HashMap();
                private final Map<Boolean, Map<Permissible, Boolean>> defSubs = new HashMap();

                @Override
                protected void customHandler(Event event, Throwable e) {
                    MetaData metaData = new MetaData();
                    metaData.addToTab(EVENT_INFO_TAB, "Event Name", event.getEventName());
                    metaData.addToTab(EVENT_INFO_TAB, "Is Async", event.isAsynchronous());
                    Map<String, Object> eventData = new HashMap<String, Object>();
                    Class eventClass = event.getClass();
                    do {
                        if (eventClass != Event.class) { // Info already provided ^
                            for (Field field : eventClass.getDeclaredFields()) {
                                if (field.getType() == HandlerList.class) {
                                    continue; // Unneeded Data
                                }
                                field.setAccessible(true);
                                try {
                                    Object value = field.get(event);
                                    if (value instanceof EntityDamageEvent.DamageModifier) {
                                        value = value.getClass().getCanonicalName() + "." + ((EntityDamageEvent.DamageModifier) value).name();
                                    }
                                    if (value instanceof Enum) {
                                        value = value.getClass().getCanonicalName() + "." + ((Enum) value).name();
                                    }
                                    eventData.put(field.getName(), value);
                                } catch (IllegalAccessException ignored) {
                                } catch (Throwable internalE) {
                                    eventData.put(field.getName(), "Error getting field data: "+internalE.getClass().getCanonicalName() + (internalE.getMessage() != null && internalE.getMessage().trim().length() > 0 ? ": " + internalE.getMessage() : ""));
                                }
                            }
                        }
                        eventClass = eventClass.getSuperclass();
                    } while (eventClass != null);
                    metaData.addToTab(EVENT_INFO_TAB, "Event Data", eventData);
                    BukkitBugSnag.getBugsnagClient().notify(e.getCause(), "error", metaData);
                }
            });
        } catch (Throwable e) {
            plugin.getLogger().severe("Could not register proxy plugin manager");
            e.printStackTrace();
        }

        //TODO Somehow make a dynamic scheduler class to use as the proxy to the real scheduler.
//        try {
//            Field schedulerField = Bukkit.getServer().getClass().getDeclaredField("scheduler");
//            schedulerField.setAccessible(true);
//            if (Modifier.isFinal(schedulerField.getModifiers())) {
//                Field modifierField = Field.class.getDeclaredField("modifiers");
//                modifierField.setAccessible(true);
//                modifierField.set(schedulerField, schedulerField.getModifiers() & ~Modifier.FINAL);
//            }
//            schedulerField.set(Bukkit.getServer(), new LoggedScheduler(this) {
//                @Override
//                protected void customHandler(int taskID, Throwable e) {
//                    MetaData metaData = new MetaData();
//                    metaData.addToTab(TASK_INFO_TAB, "Task ID", taskID);
//                    bugsnagClient.notify(e.getCause(), "error", metaData);
//                }
//            });
//        } catch (Throwable e) {
//            this.getLogger().severe("Could not register proxy scheduler");
//            e.printStackTrace();
//        }

    }
}
