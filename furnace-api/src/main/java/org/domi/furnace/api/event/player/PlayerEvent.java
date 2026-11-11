// 플레이어가 주체인 이벤트의 바탕.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Event;

public abstract class PlayerEvent extends Event {

    private final Player player;

    protected PlayerEvent(Player player) { this.player = player; }

    /** @param async 서버의 region/전역 스레드가 아닌 곳(연결 스레드 등)에서 불리면 true */
    protected PlayerEvent(Player player, boolean async) {
        super(async);
        this.player = player;
    }

    public Player player() { return player; }
}
