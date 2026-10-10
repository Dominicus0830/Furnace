// 인증이 끝난 뒤 접속을 받기 전 — 블로킹 작업(DB 조회 등)을 해도 되는 비동기 스레드에서 불린다. 거부하면 사유와 함께 끊는다.
package org.domi.furnace.api.event.player;

import java.net.InetAddress;
import java.util.UUID;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.text.Text;

public final class AsyncPlayerPreLoginEvent extends Event {

    public enum Result { ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }

    private final String name;
    private final UUID uniqueId;
    private final InetAddress address;
    private Result result = Result.ALLOWED;
    private Text kickMessage = Text.empty();

    public AsyncPlayerPreLoginEvent(String name, UUID uniqueId, InetAddress address) {
        this.name = name;
        this.uniqueId = uniqueId;
        this.address = address;
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
