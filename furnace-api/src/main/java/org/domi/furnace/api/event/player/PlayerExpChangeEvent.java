// 경험치 구슬을 주워 경험치를 받기 직전(수선으로 쓰고 남은 양) — 구슬이 있는 region 스레드에서 불린다. 받을 양을 바꿀 수 있다(0 = 받지 않음).
// /xp 명령·발전 과제 보상은 해당하지 않는다(Bukkit 과 같다).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;

public final class PlayerExpChangeEvent extends PlayerEvent {

    private int amount;

    public PlayerExpChangeEvent(Player player, int amount) {
        super(player);
        this.amount = amount;
    }

    public int amount() { return amount; }

    public void setAmount(int amount) { this.amount = amount; }
}
