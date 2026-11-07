package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerExpChangeEvent extends PlayerEvent {

    private int exp;

    public PlayerExpChangeEvent(Player player, int expAmount) {
        super(player);
        this.exp = expAmount;
    }

    public int getAmount() { return exp; }

    public void setAmount(int amount) { this.exp = amount; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
