// 창이 닫히기 직전(커서·제작 격자를 돌려주기 전) — 닫기를 처리하는 스레드에서 불린다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.inventory.InventorySnapshot;

public final class InventoryCloseEvent extends InventoryEvent {

    public InventoryCloseEvent(Player player, InventorySnapshot inventory) { super(player, inventory); }
}
