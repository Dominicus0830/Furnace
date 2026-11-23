package org.bukkit.craftbukkit.inventory;

import org.domi.furnace.compat.paper.EventInventories;

/** 모루(Paper 와 같은 이름 — T-264): 0 첫째, 1 둘째, 2 결과. 비용·이름은 PrepareAnvilEvent 안에서만 안다(state). */
@SuppressWarnings("removal")   // 옛 이름(AnvilInventory 비용 메서드)도 Paper 처럼 받는다
public class CraftInventoryAnvil extends CraftResultInventory implements org.bukkit.inventory.AnvilInventory {
    private final EventInventories.AnvilState state;

    /** state: 모루 결과를 정하는 순간의 값(그 밖의 이벤트면 null). */
    public CraftInventoryAnvil(org.domi.furnace.api.inventory.Inventory handle, EventInventories.AnvilState state) {
        super(handle, org.bukkit.event.inventory.InventoryType.ANVIL, () -> null);
        this.state = state;
    }

    private EventInventories.AnvilState state(String what) {
        if (state == null) throw EventInventories.anvilUnknown(what);
        return state;
    }

    @Override
    public String getRenameText() { return state("AnvilInventory#getRenameText").renameText; }

    @Override
    public int getRepairCostAmount() { throw EventInventories.anvilUnknown("AnvilInventory#getRepairCostAmount"); }

    @Override
    public void setRepairCostAmount(int amount) { throw EventInventories.anvilFixed("AnvilInventory#setRepairCostAmount"); }

    @Override
    public int getRepairCost() { return state("AnvilInventory#getRepairCost").repairCost; }

    @Override
    public void setRepairCost(int levels) { state("AnvilInventory#setRepairCost").repairCost = levels; }

    @Override
    public int getMaximumRepairCost() { return state == null ? EventInventories.AnvilState.MAX_REPAIR_COST : state.maximumRepairCost; }

    @Override
    public void setMaximumRepairCost(int levels) {
        if (levels < 0) throw new IllegalArgumentException("Maximum repair cost must be positive (or 0)");
        if (state != null) state.maximumRepairCost = levels;
        else if (levels != EventInventories.AnvilState.MAX_REPAIR_COST) throw EventInventories.anvilUnknown("AnvilInventory#setMaximumRepairCost");
    }

    /** 다리가 이벤트 뒤에 바뀐 비용을 읽는다(모르면 null). */
    public EventInventories.AnvilState furnaceState() { return state; }
}
