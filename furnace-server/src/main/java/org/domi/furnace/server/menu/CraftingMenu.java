// 바닐라 CraftingMenu(제작대, crafting) — 칸: 0 결과, 1..9 격자 3×3, 10..36 주 인벤토리, 37..45 핫바.
package org.domi.furnace.server.menu;

import java.util.function.BooleanSupplier;
import org.domi.furnace.server.entity.ItemStack;

public class CraftingMenu extends AbstractContainerMenu implements RecipeBookMenu {

    public static final int RESULT_SLOT = 0, CRAFT_SLOT_START = 1, CRAFT_SLOT_END = 10, INV_SLOT_START = 10, INV_SLOT_END = 37,
            USE_ROW_SLOT_START = 37, USE_ROW_SLOT_END = 46;

    private final CraftingGrid craftSlots;
    private final ResultContainer resultSlots;
    private final MenuPlayer owner;
    private final int[] blockPos;
    private final BooleanSupplier blockIsCraftingTable;
    private boolean placingRecipe;

    /**
     * @param blockPos 제작대 좌표(그 블록을 가진 region 스레드에서 클릭·유효성을 처리한다)
     * @param blockIsCraftingTable 그 좌표가 아직 제작대인가 — 블록 region 스레드에서만 호출된다
     */
    public CraftingMenu(int containerId, Container playerInventory, MenuPlayer owner, int[] blockPos, BooleanSupplier blockIsCraftingTable) {
        super("minecraft:crafting", containerId);
        this.owner = owner;
        this.blockPos = blockPos;
        this.blockIsCraftingTable = blockIsCraftingTable;
        this.craftSlots = new CraftingGrid(new SimpleContainer(9), 3, 3, this);
        this.resultSlots = new ResultContainer(new SimpleContainer(1));
        addSlot(new ResultSlot(owner, craftSlots, resultSlots, 0));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) addSlot(new Slot(craftSlots, c + r * 3));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    public CraftingGrid craftSlots() { return craftSlots; }

    public ResultContainer resultSlots() { return resultSlots; }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override
    public void slotsChanged(Container container) {
        if (!placingRecipe) CraftingLogic.slotChangedCraftingGrid(this, owner, craftSlots, resultSlots, null);
    }

    @Override
    public boolean stillValid(MenuPlayer player) {
        return blockIsCraftingTable.getAsBoolean()
                && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        clearContainer(player, craftSlots);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != resultSlots; }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == RESULT_SLOT) {
            stack = org.domi.furnace.server.map.MapItems.postProcess(stack);   // Item.onCraftedBy — 지도 잠금·확대의 새 번호
            result = stack;
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (index >= INV_SLOT_START && index < USE_ROW_SLOT_END) {
            moved = moveItemStackTo(stack, CRAFT_SLOT_START, CRAFT_SLOT_END, false);
            if (!moved.changed()) {
                moved = index < USE_ROW_SLOT_START
                        ? moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)
                        : moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_START, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            }
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == RESULT_SLOT) player.drop(stack, false);
        return result;
    }

    // --- RecipeBookMenu ---
    @Override public int resultSlotIndex() { return RESULT_SLOT; }
    @Override public int gridWidth() { return 3; }
    @Override public int gridHeight() { return 3; }
    @Override public Slot slot(int index) { return slots.get(index); }
    @Override public int containerId() { return containerId; }

    @Override public void fillCraftSlotsStackedContents(org.domi.furnace.server.recipe.StackedContents sc) {
        for (ItemStack s : craftSlots.items()) sc.accountSimpleStack(s);
    }

    @Override public void clearCraftingContent() {
        for (int i = 0; i < craftSlots.size(); i++) craftSlots.removeNoUpdate(i);
        resultSlots.clearContent();
    }

    @Override public boolean recipeMatches(org.domi.furnace.server.recipe.RecipeManager.CraftingEntry recipe) {
        var rm = owner.recipes();
        return rm != null && recipe.recipe().matches(craftSlots.asInput(), rm.context());
    }

    @Override public void beginPlacingRecipe() { placingRecipe = true; }

    @Override public void finishPlacingRecipe(org.domi.furnace.server.recipe.RecipeManager.CraftingEntry recipe) {
        placingRecipe = false;
        CraftingLogic.slotChangedCraftingGrid(this, owner, craftSlots, resultSlots, recipe);
    }
}
