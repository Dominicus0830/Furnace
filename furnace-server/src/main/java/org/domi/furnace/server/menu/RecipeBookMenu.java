// 바닐라 RecipeBookMenu: 레시피 북이 격자를 채울 수 있는 메뉴(인벤토리 2×2 · 제작대 3×3).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.recipe.RecipeManager.CraftingEntry;
import org.domi.furnace.server.recipe.StackedContents;

public interface RecipeBookMenu {

    int resultSlotIndex();

    int gridWidth();

    int gridHeight();

    /** 격자 칸 수 + 결과 칸 1. */
    default int gridSize() { return gridWidth() * gridHeight() + 1; }

    Slot slot(int index);

    int containerId();

    void fillCraftSlotsStackedContents(StackedContents sc);

    /** 격자와 결과 칸을 비운다(알림 없이). */
    void clearCraftingContent();

    /** 지금 격자가 이 레시피에 맞는가. */
    boolean recipeMatches(CraftingEntry recipe);

    default boolean shouldMoveToInventory(int slot) { return slot != resultSlotIndex(); }

    default void beginPlacingRecipe() { }

    default void finishPlacingRecipe(CraftingEntry recipe) { }
}
