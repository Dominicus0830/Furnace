package org.bukkit.event.inventory;

import org.bukkit.event.HandlerList;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Recipe;

/** 제작 격자가 바뀌어 결과 칸을 새로 정했을 때. 결과는 {@code getInventory().setResult(…)} 로 바꾼다. */
public class PrepareItemCraftEvent extends InventoryEvent {

    private static final HandlerList handlers = new HandlerList();

    private final boolean repair;
    private final CraftingInventory matrix;

    public PrepareItemCraftEvent(CraftingInventory what, InventoryView view, boolean isRepair) {
        super(view);
        this.matrix = what;
        this.repair = isRepair;
    }

    /** 맞는 레시피(없으면 null). */
    public Recipe getRecipe() { return matrix.getRecipe(); }

    @Override
    public CraftingInventory getInventory() { return matrix; }

    /** 같은 도구 두 개를 합쳐 고치는 레시피인가. */
    public boolean isRepair() { return repair; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
