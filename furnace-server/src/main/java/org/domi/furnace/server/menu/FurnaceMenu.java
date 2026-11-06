// 바닐라 AbstractFurnaceMenu(화로·용광로·훈연기) — 칸: 0 재료, 1 연료, 2 결과, 3..29 주 인벤토리, 30..38 핫바.
// 데이터 4개(태우는 시간·연료 총 틱·굽는 진행·굽는 시간)는 블록 엔티티의 값이다.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.FurnaceBlockEntity;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.FuelRegistry;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.BlockEntityContainer;

public class FurnaceMenu extends ContainerBackedMenu {

    public static final int INGREDIENT_SLOT = 0, FUEL_SLOT = 1, RESULT_SLOT = 2, SLOT_COUNT = 3, INV_SLOT_START = 3, INV_SLOT_END = 30,
            USE_ROW_SLOT_START = 30, USE_ROW_SLOT_END = 39;

    private final MenuPlayer owner;
    private final FurnaceBlockEntity be;

    /** @param menuType minecraft:furnace · minecraft:blast_furnace · minecraft:smoker */
    public FurnaceMenu(String menuType, int containerId, Container playerInventory, BlockEntityContainer container, int[] blockPos, MenuPlayer owner) {
        super(menuType, containerId, container, blockPos);
        checkContainerSize(container, 3);
        this.owner = owner;
        this.be = (FurnaceBlockEntity) container.blockEntity();
        addSlot(new Slot(container, 0));
        addSlot(new Slot(container, 1) {
            @Override public boolean mayPlace(ItemStack stack) { return FuelRegistry.isFuel(stack) || isBucket(stack); }

            @Override public int getMaxStackSize(ItemStack stack) { return isBucket(stack) ? 1 : super.getMaxStackSize(stack); }
        });
        addSlot(new Slot(container, 2) {
            private int removeCount;

            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public ItemStack remove(int amount) {
                if (hasItem()) removeCount += Math.min(amount, getItem().count());
                return super.remove(amount);
            }

            @Override public void onTake(MenuPlayer player, ItemStack stack) {
                checkTakeAchievements(stack);
                super.onTake(player, stack);
            }

            @Override protected void onQuickCraft(ItemStack stack, int count) {
                removeCount += count;
                checkTakeAchievements(stack);
            }

            @Override protected void checkTakeAchievements(ItemStack stack) {
                if (removeCount > 0) owner.onItemCrafted(stack, removeCount);
                // 바닐라 awardUsedRecipesAndPopExperience: 쓴 레시피를 알려 주고 쌓인 경험치를 준다 (TODO(엔티티): 구슬로 — 지금은 바로 더한다)
                List<ResourceId> used = new ArrayList<>();
                List<Integer> xp = be.takeExperience(owner.recipes(), used);
                if (!used.isEmpty()) {
                    List<ItemStack> grid = List.of(container.get(0), container.get(1), container.get(2));
                    for (ResourceId id : used) owner.triggerRecipeCrafted(id, grid);
                    owner.awardRecipes(used);
                }
                for (int amount : xp) if (amount > 0) owner.awardExperience(amount);
                removeCount = 0;
            }
        });
        addPlayerSlots(playerInventory);
        addDataSlot(DataSlot.of(() -> be.litTime, v -> be.litTime = v));
        addDataSlot(DataSlot.of(() -> be.litDuration, v -> be.litDuration = v));
        addDataSlot(DataSlot.of(() -> be.cookingProgress, v -> be.cookingProgress = v));
        addDataSlot(DataSlot.of(() -> be.cookingTotalTime, v -> be.cookingTotalTime = v));
    }

    private static boolean isBucket(ItemStack s) { return "minecraft:bucket".equals(ItemTools.itemName(s)); }

    private boolean canSmelt(ItemStack stack) {
        var rm = owner.recipes();
        return rm != null && rm.findCooking(be.recipeType(), stack) != null;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return super.canTakeItemForPickAll(stack, slot); }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == RESULT_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (index != FUEL_SLOT && index != INGREDIENT_SLOT) {
            if (canSmelt(stack)) {
                moved = moveItemStackTo(stack, INGREDIENT_SLOT, INGREDIENT_SLOT + 1, false);
            } else if (FuelRegistry.isFuel(stack)) {
                moved = moveItemStackTo(stack, FUEL_SLOT, RESULT_SLOT, false);
            } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
            } else {
                moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
            }
            if (!moved.changed()) return ItemStack.EMPTY;
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        slot.setChanged();
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }
}
