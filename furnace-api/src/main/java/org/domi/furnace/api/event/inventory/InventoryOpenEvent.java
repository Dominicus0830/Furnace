// 창이 열리기 직전 — 연결 이벤트 루프 스레드에서 불린다. 취소하면 열지 않는다. 블록 메뉴·제작대와 플러그인 메뉴(Player#openMenu)에서 발화한다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.inventory.InventorySnapshot;

public final class InventoryOpenEvent extends InventoryEvent implements Cancellable {

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public InventoryOpenEvent(Player player, InventorySnapshot inventory) { super(player, inventory); }

    /** menu: 열리려는 플러그인 메뉴. */
    public InventoryOpenEvent(Player player, InventorySnapshot inventory, org.domi.furnace.api.menu.Menu menu) { super(player, inventory, menu); }
}
