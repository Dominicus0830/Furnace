package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerToggleSneakEvent extends PlayerEvent implements Cancellable {

    private final boolean isSneaking;
    private boolean cancel;

    public PlayerToggleSneakEvent(Player player, boolean isSneaking) {
        super(player);
        this.isSneaking = isSneaking;
    }

    public boolean isSneaking() { return isSneaking; }

    @Override
    public boolean isCancelled() { return cancel; }

    @Override
    public void setCancelled(boolean cancel) { this.cancel = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
