package me.theminecoder.bug.serverhandler;

import com.bugsnag.MetaData;
import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import me.theminecoder.bug.BukkitBugSnag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * @author Jackson
 * @version 1.0
 */
public class PaperSpigotHandler implements ServerHandler {
    @Override
    public boolean canUse(BukkitBugSnag plugin) {
        boolean canUse = false;
        try{
            Class.forName("com.destroystokyo.paper.event.server.ServerExceptionEvent");
            canUse=true;
        } catch (Throwable ignored){
        }
        return canUse;
    }

    @Override
    public void init(BukkitBugSnag plugin) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerException(ServerExceptionEvent event) {
                MetaData metaData = new MetaData();
                //TODO Expand out and get metadata
                BukkitBugSnag.getBugsnagClient().notify(event.getException(), metaData);
            }
        }, plugin);
    }
}
