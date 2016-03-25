package me.theminecoder.bug.serverhandler;

import me.theminecoder.bug.BukkitBugSnag;

/**
 * @author Jackson
 * @version 1.0
 */
public interface ServerHandler {
    public boolean canUse(BukkitBugSnag plugin);
    public void init(BukkitBugSnag plugin);
}
