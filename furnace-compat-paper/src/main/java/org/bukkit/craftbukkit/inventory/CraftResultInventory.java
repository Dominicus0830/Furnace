package org.bukkit.craftbukkit.inventory;

import org.bukkit.inventory.InventoryHolder;

/** 결과 칸이 있는 창의 인벤토리(Paper 와 같은 이름 — T-264): 모루·숫돌·석재 절단기 등의 상위. */
public class CraftResultInventory extends CraftInventory {
    public CraftResultInventory(org.domi.furnace.api.inventory.Inventory handle, org.bukkit.event.inventory.InventoryType type,
            java.util.function.Supplier<InventoryHolder> holder) {
        super(handle, type, holder);
    }
}
