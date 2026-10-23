// 바닐라 CraftingMenu.slotChangedCraftingGrid — 격자가 바뀌면 레시피를 찾아 결과 칸을 채우고, 그 칸을 클라이언트에 바로 보낸다.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.recipe.CraftingInput;
import org.domi.furnace.server.recipe.RecipeManager.CraftingEntry;

final class CraftingLogic {

    private CraftingLogic() { }

    /** @param last 직전 레시피(place_recipe 가 넘긴다 — 아니면 null) */
    static void slotChangedCraftingGrid(AbstractContainerMenu menu, MenuPlayer owner, CraftingGrid grid, ResultContainer result, CraftingEntry last) {
        if (owner == null) return;
        var recipes = owner.recipes();
        CraftingInput input = grid.asInput();
        ItemStack out = ItemStack.EMPTY;
        if (recipes != null) {
            CraftingEntry e = recipes.findCrafting(input, last);
            if (e != null && result.setRecipeUsed(owner, e)) {
                // 바닐라는 여기서 isItemEnabled(기능 플래그)도 본다 — 실험 기능 아이템은 아직 없다.
                out = e.recipe().assemble(input, recipes.context());
            }
        }
        result.set(0, out);
        menu.setRemoteSlot(0, out);
        menu.sendSlotChange(0, out);
    }
}
