package me.theminecoder.bug;

import com.bugsnag.BeforeNotify;
import com.bugsnag.Client;
import com.bugsnag.Error;
import com.bugsnag.MetaData;
import me.theminecoder.bug.proxy.LoggedCommandMap;
import me.theminecoder.bug.proxy.LoggedPluginManager;
import me.theminecoder.bug.proxy.LoggedScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author Jackson
 * @version 1.0
 */
public class BukkitBugSnag extends JavaPlugin {

    private static final String BUKKIT_INFO_TAB = "Bukkit Info";
    private static final String WORLD_INFO_TAB = "World Info";
    private static final String EVENT_INFO_TAB = "Event Info";
    private static final String TASK_INFO_TAB = "Task Info";
    private static final String COMMAND_INFO_TAB = "Command Info";

    private static Client bugsnagClient;

    private boolean isSpigot;

    public static Client getBugsnagClient() {
        return bugsnagClient;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.isSpigot = isSpigotServer();

        if (this.getConfig().getString("api-key").equalsIgnoreCase("your-api-key-goes-here")) {
            this.getLogger().severe("Please set the api key in the config then reboot the server!");
            return;
        }

        bugsnagClient = new Client(this.getConfig().getString("api-key"));
        bugsnagClient.setReleaseStage(this.getConfig().getString("release-stage"));
        if (this.getConfig().getBoolean("enterprise.enabled")) {
            bugsnagClient.setEndpoint(this.getConfig().getString("enterprise.endpoint-url"));
        }
        bugsnagClient.setSendThreads(this.getConfig().getBoolean("send-info.threads"));

        if (this.getConfig().getBoolean("send-info.bukkit-info")) {
            bugsnagClient.addBeforeNotify(new BeforeNotify() {
                public boolean run(Error error) {
                    error.addToTab(BUKKIT_INFO_TAB, "Online Players", Bukkit.getOnlinePlayers().size());
                    List<String> pluginNames = new ArrayList<String>();
                    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                        pluginNames.add(plugin.getName() + " v" + plugin.getDescription().getVersion());
                    }
                    error.addToTab(BUKKIT_INFO_TAB, "Loaded Plugins", pluginNames);

                    return true;
                }
            });
            bugsnagClient.addToTab(BUKKIT_INFO_TAB, "Version", Bukkit.getVersion());
            bugsnagClient.addToTab(BUKKIT_INFO_TAB, "Is Spigot", this.isSpigot);
            if (this.isSpigot) {
                bugsnagClient.addToTab(BUKKIT_INFO_TAB, "Spigot: Bungeecord Enabled", Bukkit.spigot().getConfig().getBoolean("settings.bungeecord"));
                bugsnagClient.addToTab(BUKKIT_INFO_TAB, "Spigot: Late Bind Enabled", Bukkit.spigot().getConfig().getBoolean("settings.late-bind"));
                bugsnagClient.addToTab(BUKKIT_INFO_TAB, "Spigot: Netty Threads", Bukkit.spigot().getConfig().getInt("settings.netty-threads"));
            }
        }

        if (this.getConfig().getBoolean("send-info.world-info")) {
            bugsnagClient.addBeforeNotify(new BeforeNotify() {
                public boolean run(Error error) {
                    error.addToTab(WORLD_INFO_TAB, "Worlds Loaded", Bukkit.getWorlds().size());
                    Map<String, Integer> entitiesLoaded = new HashMap<String, Integer>();
                    for (World world : Bukkit.getWorlds()) {
                        entitiesLoaded.put(world.getName(), world.getEntities().size());
                    }
                    error.addToTab(WORLD_INFO_TAB, "Loaded Entities", entitiesLoaded);
                    Map<String, Integer> chunksLoaded = new HashMap<String, Integer>();
                    for (World world : Bukkit.getWorlds()) {
                        chunksLoaded.put(world.getName(), world.getLoadedChunks().length);
                    }
                    error.addToTab(WORLD_INFO_TAB, "Loaded Chunks", chunksLoaded);
                    return true;
                }
            });
        }

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
                    bugsnagClient.notify(e.getCause(), "error", metaData);
                }
            });
        } catch (Throwable e) {
            this.getLogger().severe("Could not register proxy commandMap");
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
            pluginManagerField.set(Bukkit.getServer(), new LoggedPluginManager(this) {
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
                    bugsnagClient.notify(e.getCause(), "error", metaData);
                }
            });
        } catch (Throwable e) {
            this.getLogger().severe("Could not register proxy plugin manager");
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


    public static boolean isSpigotServer() {
        try {
            Class.forName("org.spigotmc.WatchdogThread");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
