// 로그인하려는 연결의 핸드셰이크를 받은 직후(버전·포워딩 검사를 통과한 뒤, Login Start 전) — 연결의 네트워크 스레드에서 불린다.
// 가상 호스트(hostname)별로 거르거나, 버전·주소를 보고 미리 끊을 때 쓴다. 블로킹 작업은 하지 말 것(그 스레드의 다른 연결이 멈춘다).
// 서버 목록 조회(상태) 연결에는 불리지 않는다 — 그쪽은 ServerListPingEvent.
package org.domi.furnace.api.event.player;

import java.util.Objects;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.net.PlayerConnection;
import org.domi.furnace.api.text.Text;

public final class PlayerHandshakeEvent extends Event implements Cancellable {

    private final PlayerConnection connection;
    private boolean cancelled;
    private Text kickMessage = Text.text("Disconnected");

    public PlayerHandshakeEvent(PlayerConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public PlayerConnection connection() { return connection; }

    /** 취소하면 kickMessage 로 끊는다(로그인 단계 끊기 패킷). */
    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public Text kickMessage() { return kickMessage; }

    public void kickMessage(Text message) { this.kickMessage = Objects.requireNonNull(message, "message"); }

    /** 취소 + 사유. */
    public void disallow(Text message) {
        kickMessage(message);
        cancelled = true;
    }
}
