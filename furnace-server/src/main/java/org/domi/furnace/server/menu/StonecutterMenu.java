// 바닐라 StonecutterMenu(석재 절단기, stonecutter) — 칸: 0 재료, 1 결과, 2..28 주 인벤토리, 29..37 핫바. 데이터 0 = 고른 레시피 번호.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.RecipeManager.StonecuttingEntry;

public class StonecutterMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0, RESULT_SLOT = 1, INV_SLOT_START = 2, INV_SLOT_END = 29, USE_ROW_SLOT_START = 29, USE_ROW_SLOT_END = 38;

    private final int[] blockPos;
    private final BlockAccess access;
    private final MenuPlayer owner;
    private final DataSlot selectedRecipeIndex = DataSlot.standalone();
    private List<StonecuttingEntry> recipes = new ArrayList<>();
    private ItemStack input = ItemStack.EMPTY;
    private long lastSoundTime;
    private ResourceId recipeUsed;
    private final Container inputContainer;
    private final ResultContainer resultContainer = new ResultContainer(new SimpleContainer(1));
    final Slot inputSlot;
    final Slot resultSlot;

    public StonecutterMenu(int containerId, Container playerInventory, MenuPlayer owner, int[] blockPos, BlockAccess access) {
        super("minecraft:stonecutter", containerId);
        this.owner = owner;
        this.blockPos = blockPos;
        this.access = access;
        this.inputContainer = new SimpleContainer(1) {
            @Override public void setChanged() {
                super.setChanged();
                slotsChanged(this);   // 바닐라는 여기서 broadcastChanges 를 하지 않는다(재정의한 slotsChanged 가 결과 목록만 다시 구한다)
            }
        };
        inputSlot = addSlot(new Slot(inputContainer, 0));
        resultSlot = addSlot(new Slot(resultContainer, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public void onTake(MenuPlayer player, ItemStack stack) {
                player.onItemCrafted(stack, stack.count());
                if (recipeUsed != null) {   // resultContainer.awardUsedRecipes(player, [재료]) — 특수 레시피가 아니므로 배운다
                    player.triggerRecipeCrafted(recipeUsed, List.of(inputSlot.getItem()));
                    player.awardRecipes(List.of(recipeUsed));
                    recipeUsed = null;
                }
                ItemStack removed = inputSlot.remove(1);
                if (!removed.isEmpty()) setupResultSlot();
                long now = access.gameTime();
                if (lastSoundTime != now) {
                    access.playSound("minecraft:ui.stonecutter.take_result");
                    lastSoundTime = now;
                }
                super.onTake(player, stack);
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
        addDataSlot(selectedRecipeIndex);   // 바닐라 DataSlot.standalone() 은 0 에서 시작한다
    }

    public List<StonecuttingEntry> recipes() { return recipes; }

    public int selectedRecipeIndex() { return selectedRecipeIndex.get(); }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) {
        return access.stillBlock() && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(MenuPlayer player, int id) {
        if (isValidRecipeIndex(id)) {
            selectedRecipeIndex.set(id);
            setupResultSlot();
        }
        return true;
    }

    private boolean isValidRecipeIndex(int i) { return i >= 0 && i < recipes.size(); }

    @Override
    public void slotsChanged(Container container) {
        ItemStack stack = inputSlot.getItem();
        if (stack.itemId() != input.itemId() || stack.isEmpty() != input.isEmpty()) {
            input = stack;
            setupRecipeList(stack);
        }
    }

    private void setupRecipeList(ItemStack stack) {
        recipes = new ArrayList<>();
        selectedRecipeIndex.set(-1);
        resultSlot.set(ItemStack.EMPTY);
        RecipeManager rm = owner.recipes();
        if (!stack.isEmpty() && rm != null) recipes = rm.findStonecutting(stack);
    }

    private void setupResultSlot() {
        RecipeManager rm = owner.recipes();
        if (!recipes.isEmpty() && isValidRecipeIndex(selectedRecipeIndex.get()) && rm != null) {
            StonecuttingEntry e = recipes.get(selectedRecipeIndex.get());
            recipeUsed = e.id();
            resultSlot.set(e.recipe().result().stack(rm.context()));
        } else {
            resultSlot.set(ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != resultContainer && super.canTakeItemForPickAll(stack, slot); }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == RESULT_SLOT) {
            // TODO(아이템 제작 훅): Item.onCraftedBy(stack, level, player)
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (index == INPUT_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (owner.recipes() != null && !owner.recipes().findStonecutting(stack).isEmpty()) {
            moved = moveItemStackTo(stack, INPUT_SLOT, RESULT_SLOT, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
            moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else {   // 핫바
            moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        broadcastChanges();
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        resultContainer.removeNoUpdate(0);   // resultContainer.removeItemNoUpdate(1) — 결과는 돌려주지 않고 버린다
        clearContainer(player, inputContainer);
    }
}
