package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerKickEvent extends PlayerEvent implements Cancellable {

    private String kickReason;
    private String leaveMessage;
    private boolean cancelled;
    private final Cause cause;

    public PlayerKickEvent(Player playerKicked, String kickReason, String leaveMessage) {
        this(playerKicked, kickReason, leaveMessage, Cause.UNKNOWN);
    }

    /** Furnace 가 원인을 싣는 생성자(Paper 는 같은 모양을 Component 사문으로 가진다). */
    public PlayerKickEvent(Player playerKicked, String kickReason, String leaveMessage, Cause cause) {
        super(playerKicked);
        this.cause = cause;
        this.kickReason = kickReason;
        this.leaveMessage = leaveMessage;
    }

    /** 킥 원인. */
    public Cause getCause() { return cause; }

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

    public enum Cause {
        PLUGIN,
        WHITELIST,
        BANNED,
        IP_BANNED,
        KICK_COMMAND,
        FLYING_PLAYER,
        FLYING_VEHICLE,
        TIMEOUT,
        IDLING,
        INVALID_VEHICLE_MOVEMENT,
        INVALID_PLAYER_MOVEMENT,
        INVALID_ENTITY_ATTACKED,
        INVALID_PAYLOAD,
        INVALID_COOKIE,
        SPAM,
        ILLEGAL_ACTION,
        ILLEGAL_CHARACTERS,
        OUT_OF_ORDER_CHAT,
        UNSIGNED_CHAT,
        CHAT_VALIDATION_FAILED,
        EXPIRED_PROFILE_PUBLIC_KEY,
        INVALID_PUBLIC_KEY_SIGNATURE,
        TOO_MANY_PENDING_CHATS,
        SELF_INTERACTION,
        DUPLICATE_LOGIN,
        RESOURCE_PACK_REJECTION,
        RESTART_COMMAND,
        UNKNOWN;
    }
}
