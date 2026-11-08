package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerKickEvent extends PlayerEvent implements Cancellable {

    private String kickReason;
    private String leaveMessage;
    private boolean cancelled;

    public PlayerKickEvent(Player playerKicked, String kickReason, String leaveMessage) {
        super(playerKicked);
        this.kickReason = kickReason;
        this.leaveMessage = leaveMessage;
    }

    public String getReason() { return kickReason; }

    public void setReason(String kickReason) { this.kickReason = kickReason; }

    public String getLeaveMessage() { return leaveMessage; }

    /** 나갔다는 방송 문구 — 바꾸면 이어서 오는 PlayerQuitEvent 의 처음 퇴장 메시지가 된다(null·빈 문자열이면 알림 없음). */
    public void setLeaveMessage(String leaveMessage) { this.leaveMessage = leaveMessage; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
