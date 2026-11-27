// 서버가 플레이어를 내보내기 직전(/kick, 화이트리스트와 밴, 유휴, 비행 검출, 플러그인 kick 등)에 내보내기를 요청한 스레드에서 불린다.
// 취소하면 내보내지 않는다. 같은 계정 중복 접속은 원인 DUPLICATE_LOGIN 으로 이 이벤트를 부른다(Paper 와 같다). 서버 종료는 부르지 않는다.
package org.domi.furnace.api.event.player;

import java.util.Objects;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.text.Text;

public final class PlayerKickEvent extends PlayerEvent implements Cancellable {

    private Text reason;
    private boolean cancelled;
    private Text leaveMessage;
    private boolean leaveMessageSet;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    private final Cause cause;

    public PlayerKickEvent(Player player, Text reason) {
        this(player, reason, Cause.UNKNOWN);
    }

    public PlayerKickEvent(Player player, Text reason, Cause cause) {
        super(player);
        this.reason = reason;
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /** 내보내는 원인. */
    public Cause cause() { return cause; }

    /** 내보내기 원인(Paper 의 PlayerKickEvent.Cause 와 같은 종류). */
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

    /** 클라이언트에 보일 사유. */
    public Text reason() { return reason; }

    public void setReason(Text reason) { this.reason = Objects.requireNonNull(reason, "reason"); }

    /**
     * 내보낸 뒤 모두에게 보일 퇴장 알림(기본은 노란 "multiplayer.player.left"). 바꾸면 그 값이 PlayerQuitEvent 의 처음 퇴장 메시지가 된다
     * (Paper, Spigot 의 PlayerKickEvent#leaveMessage). null 이면 알림이 없다.
     */
    public Text leaveMessage() {
        if (leaveMessageSet) return leaveMessage;
        return Text.translatable("multiplayer.player.left", player().displayName()).color(org.domi.furnace.api.text.NamedTextColor.YELLOW);
    }

    public void leaveMessage(Text message) {
        this.leaveMessage = message;
        this.leaveMessageSet = true;
    }

    /** 처리기가 퇴장 알림을 정했는가(정하지 않았으면 서버가 평소대로 만든다). */
    public boolean hasLeaveMessage() { return leaveMessageSet; }
}
