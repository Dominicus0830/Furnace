// 플레이어에게 명령 트리를 보내기 직전(접속, 권한 변경, 명령 등록 변경, Player.updateCommands)에 불린다. 바닐라 sendCommands 에 해당한다(Paper PlayerCommandSendEvent).
// commands() 는 그 플레이어가 쓸 수 있는 최상위 명령 이름이다. 별칭과 "플러그인:이름" 을 포함하며, send-namespaced=false 면 이름공간 이름은 뺀다.
// 여기서 뺀 이름은 클라이언트 자동완성(명령 트리)에서 사라진다. 서버에서 직접 쳐서 실행하는 것은 그대로다.
// 이름을 더해도 아무 일도 없다(트리에 없는 명령을 만들지 않는다).
// 불리는 스레드: 접속 때는 그 플레이어의 연결 스레드이고, 그 밖에는 명령 등록이나 권한을 바꾼 스레드(보통 전역 스레드)다. 월드·엔티티 상태는 스케줄러로 넘긴다.
package org.domi.furnace.api.event.player;

import java.util.Collection;
import java.util.LinkedHashSet;
import org.domi.furnace.api.entity.Player;

public final class PlayerCommandSendEvent extends PlayerEvent {

    private final Collection<String> commands;

    public PlayerCommandSendEvent(Player player, Collection<String> commands) {
        super(player);
        this.commands = new LinkedHashSet<>(commands);
    }

    /** 보낼 최상위 명령 이름(바꿀 수 있는 집합, 트리 순서). */
    public Collection<String> commands() { return commands; }
}
