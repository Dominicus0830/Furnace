package org.bukkit.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;

/**
 * 플레이어가 보고 있는 창 하나: 위쪽 인벤토리(상자·메뉴 등)와 아래쪽 플레이어 인벤토리. 칸 번호("raw slot")는 창 전체에서 센다.
 * Paper 1.21.1 처럼 인터페이스다.
 */
public interface InventoryView {

    /** 창 밖 칸 번호. */
    int OUTSIDE = -999;

    Inventory getTopInventory();

    Inventory getBottomInventory();

    HumanEntity getPlayer();

    InventoryType getType();

    /** raw 칸에 둔다. 창 밖이면 그 자리에 떨어뜨린다(Furnace: UnsupportedFeature). */
    void setItem(int slot, ItemStack item);

    ItemStack getItem(int slot);

    void setCursor(ItemStack item);

    ItemStack getCursor();

    /** 그 raw 칸이 든 인벤토리, 창 밖(-999)·-1 이면 null. */
    Inventory getInventory(int rawSlot);

    /** raw 칸 → 그 인벤토리 안의 칸 번호. */
    int convertSlot(int rawSlot);

    InventoryType.SlotType getSlotType(int slot);

    /** 이 창을 그 플레이어에게 연다. */
    void open();

    /** 이 창을 닫는다(플레이어가 이 창을 보고 있을 때). */
    void close();

    int countSlots();

    @Deprecated
    String getTitle();

    @Deprecated(since = "1.21.1")
    String getOriginalTitle();

    @Deprecated(since = "1.21.1")
    void setTitle(String title);
}
