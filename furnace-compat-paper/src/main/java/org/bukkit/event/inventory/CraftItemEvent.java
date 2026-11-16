package org.bukkit.event.inventory;

import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Recipe;

/**
 * 제작 결과 칸을 꺼내는 클릭. 자기 처리기 목록이 없어 {@link InventoryClickEvent} 처리기도 이 이벤트를 받는다(Bukkit 과 같다).
 * Furnace: 결과 칸이 비어 있으면 이 이벤트가 아니라 보통 클릭이다.
 */
public class CraftItemEvent extends InventoryClickEvent {

    private final Recipe recipe;

    public CraftItemEvent(Recipe recipe, InventoryView view, SlotType type, int slot, ClickType click, InventoryAction action) {
        super(view, type, slot, click, action);
        this.recipe = recipe;
    }

    public CraftItemEvent(Recipe recipe, InventoryView view, SlotType type, int slot, ClickType click, InventoryAction action, int key) {
        super(view, type, slot, click, action, key);
        this.recipe = recipe;
    }

    @Override
    public CraftingInventory getInventory() { return (CraftingInventory) super.getInventory(); }

    public Recipe getRecipe() { return recipe; }
}
