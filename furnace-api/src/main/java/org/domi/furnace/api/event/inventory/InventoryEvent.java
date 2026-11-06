// 창(인벤토리 메뉴)을 보는 플레이어가 주체인 이벤트의 바탕. inventory() 는 이벤트 시점 스냅샷이다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.player.PlayerEvent;
import org.domi.furnace.api.inventory.InventorySnapshot;

public abstract class InventoryEvent extends PlayerEvent {

    private final InventorySnapshot inventory;

    protected InventoryEvent(Player player, InventorySnapshot inventory) {
        super(player);
        this.inventory = inventory;
    }

    public InventorySnapshot inventory() { return inventory; }
}
