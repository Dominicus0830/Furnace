package org.bukkit.event.player;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 플레이어가 아이템을 버린다. getItemDrop() 은 곧 월드에 들어갈 아이템 엔티티(CraftBukkit 처럼 아직 월드 밖)이고, 취소하면 인벤토리로 돌아간다. */
public class PlayerDropItemEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final Item drop;

    public PlayerDropItemEvent(Player player, Item drop) {
        super(player);
        this.drop = drop;
    }

    public Item getItemDrop() { return drop; }
}
