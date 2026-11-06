// 서버 목록(멀티플레이 화면)이 상태를 물을 때 — 네트워크 스레드에서 불린다(월드 상태를 직접 보지 말 것). 응답의 MOTD·인원을 바꿀 수 있다.
package org.domi.furnace.api.event.server;

import java.net.InetAddress;
import java.util.Objects;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.text.Text;

public final class ServerListPingEvent extends Event {

    private final InetAddress address;
    private Text motd;
    private int numPlayers;
    private int maxPlayers;
    private boolean hidePlayers;

    public ServerListPingEvent(InetAddress address, Text motd, int numPlayers, int maxPlayers, boolean hidePlayers) {
        this.address = address;
        this.motd = motd;
        this.numPlayers = numPlayers;
        this.maxPlayers = maxPlayers;
        this.hidePlayers = hidePlayers;
    }

    /** 묻는 쪽 주소(모르면 null). */
    public InetAddress address() { return address; }

    public Text motd() { return motd; }

    public void setMotd(Text motd) { this.motd = Objects.requireNonNull(motd, "motd"); }

    /** 보일 접속자 수. */
    public int numPlayers() { return numPlayers; }

    public void setNumPlayers(int numPlayers) { this.numPlayers = numPlayers; }

    public int maxPlayers() { return maxPlayers; }

    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    /** true 면 접속자 이름 목록(sample)을 보내지 않는다. */
    public boolean hidePlayers() { return hidePlayers; }

    public void setHidePlayers(boolean hidePlayers) { this.hidePlayers = hidePlayers; }
}
