// 바닐라 ResultContainer — 제작 결과 한 칸과 "마지막으로 쓴 레시피". 어떤 amount 로 빼도 통째로 나온다(takeItem).
package org.domi.furnace.server.menu;

import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.recipe.RecipeManager.CraftingEntry;

public class ResultContainer implements Container {

    private final Container storage;
    private CraftingEntry recipeUsed;

    public ResultContainer(Container storage) { this.storage = storage; }

    @Override public int size() { return 1; }

    @Override public ItemStack get(int index) { return storage.get(0); }

    @Override public void set(int index, ItemStack stack) { storage.replaceInPlace(0, stack == null ? ItemStack.EMPTY : stack); }

    @Override public ItemStack remove(int index, int amount) { return removeNoUpdate(0); }

    @Override public ItemStack removeNoUpdate(int index) {
        ItemStack cur = storage.get(0);
        if (!cur.isEmpty()) storage.replaceInPlace(0, ItemStack.EMPTY);
        return cur;
    }

    @Override public void setChanged() { }

    @Override public boolean stillValid(MenuPlayer player) { return true; }

    public void clearContent() { storage.replaceInPlace(0, ItemStack.EMPTY); }

    public CraftingEntry recipeUsed() { return recipeUsed; }

    public void setRecipeUsed(CraftingEntry e) { recipeUsed = e; }

    /**
     * 바닐라 RecipeCraftingHolder.setRecipeUsed(level, player, recipe): 특수가 아닌 레시피는 doLimitedCrafting 이면 아는 것만 쓸 수 있다.
     * @return 이 레시피로 결과를 내도 되는가
     */
    public boolean setRecipeUsed(MenuPlayer player, CraftingEntry e) {
        if (!e.isSpecial() && player.doLimitedCrafting() && !player.hasRecipe(e.id())) return false;
        recipeUsed = e;
        return true;
    }

    /** 바닐라 RecipeCraftingHolder.awardUsedRecipes: recipe_crafted 를 일으키고, 특수가 아니면 레시피를 배운다. */
    public void awardUsedRecipes(MenuPlayer player, List<ItemStack> grid) {
        if (recipeUsed != null) {
            player.triggerRecipeCrafted(recipeUsed.id(), grid);
            if (!recipeUsed.isSpecial()) {
                player.awardRecipes(List.of(recipeUsed.id()));
                recipeUsed = null;
            }
        }
    }
}
