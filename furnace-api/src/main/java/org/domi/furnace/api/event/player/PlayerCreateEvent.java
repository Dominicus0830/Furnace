// 플레이어 객체가 막 만들어졌을 때(구성 끝 → 플레이 진입, 저장 데이터를 읽은 직후) — 연결 스레드에서 불린다.
// 클라이언트에게 플레이 패킷(login·권한 레벨·명령 트리)을 하나도 보내기 전이고 아직 월드에 들어가지 않았다.
// 그래서 여기서 붙인 권한(Player.addAttachment 등)은 첫 명령 트리부터 반영된다 — LuckPerms 류가 "Player 가 생기는 순간"
// 권한을 넣는 자리다. 위치·월드·인벤토리를 바꾸지 말 것(첫 위치는 PlayerSpawnLocationEvent). 블로킹 작업 금지
// (미리 읽을 데이터는 AsyncPlayerPreLoginEvent·AsyncPlayerConfigureEvent 에서).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.net.PlayerConnection;

public final class PlayerCreateEvent extends PlayerEvent {

    private final PlayerConnection connection;

    public PlayerCreateEvent(Player player, PlayerConnection connection) {
        super(player);
        this.connection = connection;
    }

    public PlayerConnection connection() { return connection; }
}
