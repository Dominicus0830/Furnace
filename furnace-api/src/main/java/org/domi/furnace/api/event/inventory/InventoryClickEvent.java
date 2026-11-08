// 플레이어가 창 안을 클릭했을 때(서버가 클릭을 재현하기 전) — 메뉴를 처리하는 스레드에서 불린다. 취소하면 서버가 클릭을 적용하지 않고 창을 다시 보낸다. 제작 결과 칸을 꺼내는 클릭은 하위 CraftItemEvent 로 온다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;

public class InventoryClickEvent extends InventoryEvent implements Cancellable {

    private final int slot;
    private final int button;
    private final String clickType;
    private final ItemStack cursor;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** slot: 창 칸 번호(-999 는 창 밖), button: 클라이언트 버튼, clickType: 클릭 종류 이름(pickup, quick_move, swap, clone, throw, quick_craft, pickup_all). */
    public InventoryClickEvent(Player player, InventorySnapshot inventory, int slot, int button, String clickType, ItemStack cursor) {
        this(player, inventory, slot, button, clickType, cursor, null);
    }

    /** menu: 플러그인 메뉴 창이면 그 메뉴. */
    public InventoryClickEvent(Player player, InventorySnapshot inventory, int slot, int button, String clickType, ItemStack cursor,
                               org.domi.furnace.api.menu.Menu menu) {
        super(player, inventory, menu);
        this.slot = slot;
        this.button = button;
        this.clickType = clickType;
        this.cursor = cursor;
    }

    public int slot() { return slot; }

    public int button() { return button; }

    public String clickType() { return clickType; }

    /** 커서에 든 아이템. */
    public ItemStack cursor() { return cursor; }

    /** 클릭한 칸의 아이템(창 밖이면 EMPTY). */
    public ItemStack current() { return inventory().get(slot); }
}
