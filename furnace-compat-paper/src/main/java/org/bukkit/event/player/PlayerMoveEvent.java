package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private boolean cancelled;
    private Location from;
    private Location to;

    public PlayerMoveEvent(Player player, Location from, Location to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location getFrom() { return from; }

    public Location getTo() { return to; }

    /** 도착 칸 바꾸기는 Furnace 이동 이벤트가 아직 받지 않는다 — 취소(되돌리기)만 된다. */
    public void setTo(Location to) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("PlayerMoveEvent#setTo", "이동 이벤트는 취소만 받는다(도착 칸을 바꾸려면 취소 후 텔레포트)");
    }

    public void setFrom(Location from) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("PlayerMoveEvent#setFrom", "이동 이벤트의 출발 칸은 바꿀 수 없다");
    }

    public boolean hasChangedPosition() {
        return from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    public boolean hasExplicitlyChangedPosition() { return hasChangedPosition(); }

    public boolean hasChangedBlock() {
        return from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
    }

    public boolean hasChangedOrientation() { return from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch(); }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
