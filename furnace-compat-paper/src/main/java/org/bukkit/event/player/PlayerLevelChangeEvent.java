package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerLevelChangeEvent extends PlayerEvent {

    private final int oldLevel;
    private final int newLevel;

    public PlayerLevelChangeEvent(Player player, int oldLevel, int newLevel) {
        super(player);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public int getOldLevel() { return oldLevel; }

    public int getNewLevel() { return newLevel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
