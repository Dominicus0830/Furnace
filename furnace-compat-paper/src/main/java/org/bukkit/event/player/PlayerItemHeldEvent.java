package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 단축 칸 바꾸기. 취소하면 이전 칸에 머문다. */
public class PlayerItemHeldEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final int previous;
    private final int current;

    public PlayerItemHeldEvent(Player player, int previous, int current) {
        super(player);
        this.previous = previous;
        this.current = current;
    }

    public int getPreviousSlot() { return previous; }

    public int getNewSlot() { return current; }
}
