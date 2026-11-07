package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerKickEvent extends PlayerEvent implements Cancellable {

    private String kickReason;
    private final String leaveMessage;
    private boolean cancelled;

    public PlayerKickEvent(Player playerKicked, String kickReason, String leaveMessage) {
        super(playerKicked);
        this.kickReason = kickReason;
        this.leaveMessage = leaveMessage;
    }

    public String getReason() { return kickReason; }

    public void setReason(String kickReason) { this.kickReason = kickReason; }

    public String getLeaveMessage() { return leaveMessage; }

    /** 나갔다는 방송 문구는 PlayerQuitEvent 로 바꾼다 — Furnace 의 킥 이벤트에는 이 칸이 없다. */
    public void setLeaveMessage(String leaveMessage) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("PlayerKickEvent#setLeaveMessage", "킥 이벤트에는 나갔다는 방송 문구 칸이 없다 — PlayerQuitEvent#setQuitMessage 를 쓴다");
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
