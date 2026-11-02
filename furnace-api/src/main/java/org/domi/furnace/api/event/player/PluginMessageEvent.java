// 클라이언트가 플레이 단계에서 보낸 커스텀 페이로드(바닐라 minecraft:brand 는 서버가 따로 읽고 이 이벤트로는 오지 않는다).
// 그 플레이어의 연결(IO) 스레드에서 불린다 — 오래 걸리는 일은 넘기고, 게임 상태는 자기 스케줄러로 건드린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;

public final class PluginMessageEvent extends PlayerEvent {

    private final String channel;
    private final byte[] data;

    public PluginMessageEvent(Player player, String channel, byte[] data) {
        super(player);
        this.channel = channel;
        this.data = data;
    }

    /** 채널 이름(예: "myplugin:main"). */
    public String channel() { return channel; }

    /** 본문 사본(최대 32767바이트). */
    public byte[] data() { return data.clone(); }
}
