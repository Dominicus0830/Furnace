// 바닐라 접속 검사(밴·화이트리스트·인원) 결과를 플러그인이 보고 바꿀 수 있다 — 연결 스레드에서 불린다.
package org.domi.furnace.api.event.player;

import java.net.InetAddress;
import java.util.UUID;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.text.Text;

public final class PlayerLoginEvent extends Event {

    public enum Result { ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }

    private final String name;
    private final UUID uniqueId;
    private final InetAddress address;
    private Result result;
    private Text kickMessage;

    public PlayerLoginEvent(String name, UUID uniqueId, InetAddress address, Result result, Text kickMessage) {
        this.name = name;
        this.uniqueId = uniqueId;
        this.address = address;
        this.result = result;
        this.kickMessage = kickMessage == null ? Text.empty() : kickMessage;
    }

    public String name() { return name; }

    public UUID uniqueId() { return uniqueId; }

    public InetAddress address() { return address; }

    public Result result() { return result; }

    public Text kickMessage() { return kickMessage; }

    public void allow() {
        result = Result.ALLOWED;
        kickMessage = Text.empty();
    }

    public void disallow(Result result, Text message) {
        this.result = result;
        this.kickMessage = message;
    }
}
