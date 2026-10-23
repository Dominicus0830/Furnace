// 접속 완료(월드에 들어옴) — 접속 메시지를 바꾸거나 null 로 없앨 수 있다. 그 플레이어의 연결 스레드에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.text.Text;

public final class PlayerJoinEvent extends PlayerEvent {

    private Text joinMessage;

    public PlayerJoinEvent(Player player, Text joinMessage) {
        super(player);
        this.joinMessage = joinMessage;
    }

    /** 기본: 노란 "multiplayer.player.joined". null 이면 알리지 않는다. */
    public Text joinMessage() { return joinMessage; }

    public void joinMessage(Text message) { this.joinMessage = message; }
}
