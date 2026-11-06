// 바닐라 ResultSlot — 제작 결과 칸. 꺼내면 격자 재료를 하나씩 줄이고(양동이 등 남는 아이템은 그 자리에), 만든 개수만큼 통계·발전 과제를 올린다.
package org.domi.furnace.server.menu;

import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.recipe.CraftingInput;

public class ResultSlot extends Slot {

    private final CraftingGrid craftSlots;
    private final MenuPlayer owner;
    private int removeCount;

    public ResultSlot(MenuPlayer owner, CraftingGrid craftSlots, ResultContainer result, int slot) {
        super(result, slot);
        this.owner = owner;
        this.craftSlots = craftSlots;
    }

    @Override public boolean mayPlace(ItemStack stack) { return false; }

    @Override public ItemStack remove(int amount) {
        if (hasItem()) removeCount += Math.min(amount, getItem().count());
        // 바닐라는 onTake 의 onCraftedBy 가 꺼낸 스택을 그 자리에서 고친다(지도 잠금·확대의 새 번호). 불변 스택이라 꺼내는 곳에서 바꾼다.
        return org.domi.furnace.server.map.MapItems.postProcess(super.remove(amount));
    }

    @Override protected void onQuickCraft(ItemStack stack, int count) {
        removeCount += count;
        checkTakeAchievements(stack);
    }

    @Override protected void onSwapCraft(int count) { removeCount += count; }

    @Override protected void checkTakeAchievements(ItemStack stack) {
        if (removeCount > 0 && owner != null) owner.onItemCrafted(stack, removeCount);
        if (container instanceof ResultContainer rc && owner != null) rc.awardUsedRecipes(owner, craftSlots.items());
        removeCount = 0;
    }

    @Override
    public void onTake(MenuPlayer player, ItemStack stack) {
        checkTakeAchievements(stack);
        CraftingInput.Positioned positioned = craftSlots.positioned();
        CraftingInput input = positioned.input();
        int left = positioned.left(), top = positioned.top();
        var recipes = player.recipes();
        List<ItemStack> remaining = recipes != null ? recipes.remainingItems(input) : input.items();
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int idx = x + left + (y + top) * craftSlots.width();
                ItemStack item = craftSlots.get(idx);
                ItemStack rem = remaining.get(x + y * input.width());
                if (!item.isEmpty()) {
                    craftSlots.remove(idx, 1);
                    item = craftSlots.get(idx);
                }
                if (!rem.isEmpty()) {
                    if (item.isEmpty()) {
                        craftSlots.set(idx, rem);
                    } else if (Stacks.isSameItemSameComponents(item, rem)) {
                        craftSlots.set(idx, rem.withCount(rem.count() + item.count()));
                    } else {
                        ItemStack left2 = player.addToInventory(rem);
                        if (!left2.isEmpty()) player.drop(left2, false);
                    }
                }
            }
        }
    }
}
