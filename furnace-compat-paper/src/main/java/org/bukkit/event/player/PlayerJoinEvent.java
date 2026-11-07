package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerJoinEvent extends PlayerEvent {

    private String joinMessage;

    public PlayerJoinEvent(Player playerJoined, String joinMessage) {
        super(playerJoined);
        this.joinMessage = joinMessage;
    }

    /** 입장 메시지(레거시 § 서식 문자열). 없애려면 null. */
    public String getJoinMessage() { return joinMessage; }

    public void setJoinMessage(String joinMessage) { this.joinMessage = joinMessage; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
