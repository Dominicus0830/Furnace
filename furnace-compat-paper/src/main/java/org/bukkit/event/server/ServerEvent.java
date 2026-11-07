package org.bukkit.event.server;

import org.bukkit.event.Event;

public abstract class ServerEvent extends Event {

    public ServerEvent() {}

    public ServerEvent(boolean async) { super(async); }
}
