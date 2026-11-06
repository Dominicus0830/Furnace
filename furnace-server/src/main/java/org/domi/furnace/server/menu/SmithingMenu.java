// 바닐라 SmithingMenu(대장장이 작업대, smithing) — 칸: 0 거푸집, 1 밑감, 2 재료, 3 결과, 4..30 주 인벤토리, 31..39 핫바.
package org.domi.furnace.server.menu;

import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.RecipeManager.SmithingEntry;

public class SmithingMenu extends ItemCombinerMenu {

    public static final int TEMPLATE_SLOT = 0, BASE_SLOT = 1, ADDITIONAL_SLOT = 2, RESULT_SLOT = 3;

    private SmithingEntry selectedRecipe;

    public SmithingMenu(int containerId, Container playerInventory, MenuPlayer player, int[] blockPos, BlockAccess access) {
        super("minecraft:smithing", containerId, playerInventory, player, blockPos, access, 3);
    }

    @Override protected boolean mayPlaceInput(int index, ItemStack stack) {
        RecipeManager rm = player.recipes();
        return rm != null && rm.smithingAccepts(index, stack);
    }

    @Override protected boolean isValidBlock() { return access.stillBlock(); }

    @Override protected boolean mayPickup(MenuPlayer p, boolean hasStack) {
        RecipeManager rm = player.recipes();
        return selectedRecipe != null && rm != null
                && selectedRecipe.recipe().matches(inputSlots.get(0), inputSlots.get(1), inputSlots.get(2), rm.context());
    }

    @Override protected void onTake(MenuPlayer p, ItemStack stack) {
        p.onItemCrafted(stack, stack.count());
        if (recipeUsed != null) {
            p.triggerRecipeCrafted(recipeUsed, List.of(inputSlots.get(0), inputSlots.get(1), inputSlots.get(2)));
            p.awardRecipes(List.of(recipeUsed));
            recipeUsed = null;
        }
        for (int i = 0; i < 3; i++) shrinkStackInSlot(i);
        access.levelEvent(1044);
    }

    private void shrinkStackInSlot(int i) {
        ItemStack s = inputSlots.get(i);
        if (!s.isEmpty()) inputSlots.set(i, s.withCount(s.count() - 1));
    }

    @Override
    public void createResult() {
        RecipeManager rm = player.recipes();
        if (rm == null) { resultSlots.set(0, ItemStack.EMPTY); return; }
        ItemStack t = inputSlots.get(0), b = inputSlots.get(1), a = inputSlots.get(2);
        List<SmithingEntry> found = rm.findSmithing(t, b, a);
        if (found.isEmpty()) {
            resultSlots.set(0, ItemStack.EMPTY);
        } else {
            SmithingEntry e = found.get(0);
            ItemStack out = e.recipe().assemble(t, b, a, rm.context(), rm.trims());
            selectedRecipe = e;
            recipeUsed = e.id();
            resultSlots.set(0, out);
        }
    }

    @Override protected boolean canMoveIntoInputSlots(ItemStack stack) { return firstFreeAccepting(stack) >= 0; }

    @Override protected int getSlotToQuickMoveTo(ItemStack stack) { return Math.max(firstFreeAccepting(stack), 0); }

    /** 바닐라 findSlotToQuickMoveTo: 그 아이템을 받는 첫 칸(거푸집 → 밑감 → 재료 중) 가운데 비어 있는 것. */
    private int firstFreeAccepting(ItemStack stack) {
        RecipeManager rm = player.recipes();
        if (rm == null) return -1;
        for (var e : rm.smithingRecipes()) {
            for (int i = 0; i < 3; i++) {
                boolean ok = i == 0 ? e.recipe().isTemplate(stack, rm.context()) : i == 1 ? e.recipe().isBase(stack, rm.context())
                        : e.recipe().isAddition(stack, rm.context());
                if (ok && !getSlot(i).hasItem()) return i;
            }
        }
        return -1;
    }
}
