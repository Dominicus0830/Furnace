// 구성 단계의 마지막(레지스트리·태그·server.properties 리소스 팩이 끝나고 구성 끝 패킷을 보내기 직전) — 비동기 스레드(가상 스레드)에서 불린다.
// 모든 처리기가 돌아올 때까지 구성 단계가 멈춰 있으므로 플레이어 데이터를 미리 읽는 등의 블로킹 작업을 해도 된다(기다리는 동안 서버가
// keep-alive 를 보내 클라이언트 시간 초과를 막는다). 아직 Player 가 없다 — 연결 정보로만 판단한다.
// 거부하면 구성 단계 끊기 패킷으로 끊는다. 플레이 중 재구성 때는 불리지 않는다(처음 들어올 때 한 번).
package org.domi.furnace.api.event.player;

import java.util.Objects;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.net.PlayerConnection;
import org.domi.furnace.api.text.Text;

public final class AsyncPlayerConfigureEvent extends Event {

    private final PlayerConnection connection;
    private boolean allowed = true;
    private Text kickMessage = Text.empty();

    public AsyncPlayerConfigureEvent(PlayerConnection connection) {
        super(true);   // 가상 스레드에서 불린다
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public PlayerConnection connection() { return connection; }

    public boolean isAllowed() { return allowed; }

    public Text kickMessage() { return kickMessage; }

    public void allow() {
        allowed = true;
        kickMessage = Text.empty();
    }

    public void disallow(Text message) {
        allowed = false;
        kickMessage = Objects.requireNonNull(message, "message");
    }
}
