// 창(인벤토리 메뉴)을 보는 플레이어가 주체인 이벤트의 바탕. inventory() 는 이벤트 시점 스냅샷이다(창의 모든 칸 — 위쪽 칸 뒤에 플레이어 인벤토리).
// menu() 는 그 창이 플러그인 메뉴(Player#openMenu)면 그 메뉴, 아니면 null 이다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.player.PlayerEvent;
import org.domi.furnace.api.inventory.InventorySnapshot;

public abstract class InventoryEvent extends PlayerEvent {

    private final InventorySnapshot inventory;
    private final org.domi.furnace.api.menu.Menu menu;

    protected InventoryEvent(Player player, InventorySnapshot inventory) { this(player, inventory, null); }

    protected InventoryEvent(Player player, InventorySnapshot inventory, org.domi.furnace.api.menu.Menu menu) {
        super(player);
        this.inventory = inventory;
        this.menu = menu;
    }

    public InventorySnapshot inventory() { return inventory; }

    /** 이 창이 플러그인 메뉴면 그 메뉴(열리는 중이면 열리려는 메뉴), 아니면 null. */
    public org.domi.furnace.api.menu.Menu menu() { return menu; }
}
