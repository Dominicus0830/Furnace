// 경험치 레벨이 바뀐 뒤 — 플레이어를 가진 region 스레드에서 그 틱 끝에 한 번 불린다(한 틱에 여러 번 바뀌면 처음과 마지막 레벨).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;

public final class PlayerLevelChangeEvent extends PlayerEvent {

    private final int oldLevel;
    private final int newLevel;

    public PlayerLevelChangeEvent(Player player, int oldLevel, int newLevel) {
        super(player);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public int oldLevel() { return oldLevel; }

    public int newLevel() { return newLevel; }
}
