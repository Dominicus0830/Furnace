package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerQuitEvent extends PlayerEvent {

    private String quitMessage;

    public PlayerQuitEvent(Player who, String quitMessage) {
        super(who);
        this.quitMessage = quitMessage;
    }

    public String getQuitMessage() { return quitMessage; }

    public void setQuitMessage(String quitMessage) { this.quitMessage = quitMessage; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
