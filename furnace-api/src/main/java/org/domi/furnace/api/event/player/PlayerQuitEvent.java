// 접속 종료 — 퇴장 메시지를 바꾸거나 null 로 없앨 수 있다. 그 플레이어의 연결 스레드에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.net.QuitReason;
import org.domi.furnace.api.text.Text;

public final class PlayerQuitEvent extends PlayerEvent {

    private Text quitMessage;
    private final QuitReason reason;

    public PlayerQuitEvent(Player player, Text quitMessage) {
        this(player, quitMessage, QuitReason.DISCONNECTED);
    }

    public PlayerQuitEvent(Player player, Text quitMessage, QuitReason reason) {
        super(player);
        this.quitMessage = quitMessage;
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    /** 끊긴 이유(스스로 나감·킥·시간 초과·오류·서버 종료). */
    public QuitReason reason() { return reason; }

    /** 기본: 노란 "multiplayer.player.left". */
    public Text quitMessage() { return quitMessage; }

    public void quitMessage(Text message) { this.quitMessage = message; }
}
