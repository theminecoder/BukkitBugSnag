package me.theminecoder.bug;

import com.bugsnag.BeforeNotify;
import com.bugsnag.Client;
import com.bugsnag.Error;
import com.bugsnag.MetaData;
import me.theminecoder.bug.proxy.LoggedCommandMap;
import me.theminecoder.bug.proxy.LoggedPluginManager;
import me.theminecoder.bug.proxy.LoggedScheduler;
import me.theminecoder.bug.serverhandler.BukkitHandler;
import me.theminecoder.bug.serverhandler.PaperSpigotHandler;
import me.theminecoder.bug.serverhandler.ServerHandler;
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

    public static final String BUKKIT_INFO_TAB = "Bukkit Info";
    public static final String WORLD_INFO_TAB = "World Info";
    public static final String EVENT_INFO_TAB = "Event Info";
    public static final String TASK_INFO_TAB = "Task Info";
    public static final String COMMAND_INFO_TAB = "Command Info";

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

        for(ServerHandler handler : new ServerHandler[]{
                new PaperSpigotHandler(),
                new BukkitHandler()
        }) {
            if(handler.canUse(this)) {
                try {
                    handler.init(this);
                    this.getLogger().info(handler.getClass().getSimpleName()+" registered!");
                    break;
                } catch (Throwable e) {
                    bugsnagClient.notify(new RuntimeException("Could not init server handler", e));
                }
            }
        }
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
