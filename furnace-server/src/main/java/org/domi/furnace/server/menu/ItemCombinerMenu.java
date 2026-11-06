// 바닐라 ItemCombinerMenu — 입력 칸들 + 결과 칸 한 개 + 플레이어 인벤토리(모루·대장장이 작업대의 바탕).
// 칸 번호: 0..n-1 입력, n 결과, n+1..n+27 주 인벤토리, 그 뒤 핫바 9칸.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;

public abstract class ItemCombinerMenu extends AbstractContainerMenu {

    protected final BlockAccess access;
    protected final MenuPlayer player;
    protected final Container inputSlots;
    protected final ResultContainer resultSlots = new ResultContainer(new SimpleContainer(1));
    protected ResourceId recipeUsed;
    private final int[] blockPos;
    private final int inputCount;
    private final List<Integer> inputSlotIndexes = new ArrayList<>();

    protected ItemCombinerMenu(String menuType, int containerId, Container playerInventory, MenuPlayer player, int[] blockPos, BlockAccess access,
            int inputCount) {
        super(menuType, containerId);
        this.player = player;
        this.blockPos = blockPos;
        this.access = access;
        this.inputCount = inputCount;
        this.inputSlots = new SimpleContainer(inputCount) {
            @Override public void setChanged() {
                super.setChanged();
                slotsChanged(this);
            }
        };
        for (int i = 0; i < inputCount; i++) {
            final int idx = i;
            inputSlotIndexes.add(i);
            addSlot(new Slot(inputSlots, i) {
                @Override public boolean mayPlace(ItemStack stack) { return mayPlaceInput(idx, stack); }
            });
        }
        addSlot(new Slot(resultSlots, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public boolean mayPickup(MenuPlayer p) { return ItemCombinerMenu.this.mayPickup(p, hasItem()); }

            @Override public void onTake(MenuPlayer p, ItemStack stack) { ItemCombinerMenu.this.onTake(p, stack); }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    protected abstract boolean mayPlaceInput(int index, ItemStack stack);

    protected abstract boolean mayPickup(MenuPlayer player, boolean hasStack);

    protected abstract void onTake(MenuPlayer player, ItemStack stack);

    protected abstract boolean isValidBlock();

    /** 입력이 바뀌었을 때 결과를 다시 구한다(바닐라 createResult). */
    public abstract void createResult();

    /** shift-클릭으로 입력 칸에 넣어도 되는 아이템인가. */
    protected boolean canMoveIntoInputSlots(ItemStack stack) { return true; }

    /** shift-클릭이 입력 칸으로 옮길 때 시작할 칸. */
    protected int getSlotToQuickMoveTo(ItemStack stack) {
        return inputSlots.isEmpty() ? 0 : inputSlotIndexes.get(0);
    }

    public final int resultSlot() { return inputCount; }

    private int invStart() { return inputCount + 1; }

    private int useRowStart() { return invStart() + 27; }

    private int useRowEnd() { return useRowStart() + 9; }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == inputSlots) createResult();
    }

    @Override
    public boolean stillValid(MenuPlayer p) {
        return isValidBlock() && p.distanceToSqr(blockPos[0], blockPos[1], blockPos[2]) < 8.5 * 8.5;   // canInteractWithBlock(pos, 4.0): 거리 4.5 + 4.0
    }

    @Override
    public void removed(MenuPlayer p) {
        super.removed(p);
        clearContainer(p, inputSlots);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot); }

    @Override
    public ItemStack quickMoveStack(MenuPlayer p, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == resultSlot()) {
            moved = moveItemStackTo(stack, invStart(), useRowEnd(), true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (inputSlotIndexes.contains(index)) {
            moved = moveItemStackTo(stack, invStart(), useRowEnd(), false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (canMoveIntoInputSlots(stack) && index >= invStart() && index < useRowEnd()) {
            int target = getSlotToQuickMoveTo(result);
            moved = moveItemStackTo(stack, target, resultSlot(), false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (index >= invStart() && index < useRowStart()) {
            moved = moveItemStackTo(stack, useRowStart(), useRowEnd(), false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else {
            moved = moveItemStackTo(stack, invStart(), useRowStart(), false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return result;
    }
}
