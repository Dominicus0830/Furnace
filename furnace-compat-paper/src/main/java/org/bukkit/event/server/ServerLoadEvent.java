package org.bukkit.event.server;

import org.bukkit.event.HandlerList;

public class ServerLoadEvent extends ServerEvent {

    private final LoadType type;

    public ServerLoadEvent(LoadType type) { this.type = type; }

    public LoadType getType() { return type; }

    public enum LoadType {
        STARTUP, RELOAD
    }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
