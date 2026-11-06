// 플레이어가 다른 차원(월드)으로 옮겨 간 뒤 — 도착한 region 스레드에서 불린다(포털·명령·다른 차원 리스폰 모두).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;

public final class PlayerChangedWorldEvent extends PlayerEvent {

    private final String from;

    public PlayerChangedWorldEvent(Player player, String from) {
        super(player);
        this.from = from;
    }

    /** 떠나온 월드 이름(minecraft:the_nether 등). 지금 월드는 player().location(). */
    public String from() { return from; }
}
