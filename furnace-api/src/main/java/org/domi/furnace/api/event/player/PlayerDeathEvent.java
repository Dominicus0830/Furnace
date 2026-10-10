// 플레이어 사망 — 사망 메시지(null 이면 알리지 않음)와 인벤토리·경험치 유지 여부를 바꿀 수 있다.
// 피해를 준 쪽의 스레드(소유 region·명령 스레드)에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.text.Text;

public final class PlayerDeathEvent extends PlayerEvent {

    private Text deathMessage;
    private boolean keepInventory;
    private boolean keepLevel;

    public PlayerDeathEvent(Player player, Text deathMessage, boolean keepInventory) {
        super(player);
        this.deathMessage = deathMessage;
        this.keepInventory = keepInventory;
        this.keepLevel = keepInventory;
    }

    public Text deathMessage() { return deathMessage; }

    public void deathMessage(Text message) { this.deathMessage = message; }

    public boolean keepInventory() { return keepInventory; }

    public void keepInventory(boolean v) { this.keepInventory = v; }

    /** 리스폰 때 경험치를 유지(기본은 keepInventory 게임룰과 같다). */
    public boolean keepLevel() { return keepLevel; }

    public void keepLevel(boolean v) { this.keepLevel = v; }
}
