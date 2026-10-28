// 바닐라 CrafterMenu(crafter_3x3) — 칸: 0..8 제작기 3×3(CrafterSlot: 잠근 칸엔 못 놓음), 9..35 주 인벤토리, 36..44 핫바,
// 45 결과 미리보기(NonInteractiveResultSlot: 꺼내기·놓기 불가). 데이터 10개 = 칸 0..8 잠금(1 = 잠김), 9 = triggered.
// 메뉴 자신이 리스너라 어느 칸이 바뀌든 결과 미리보기를 다시 계산한다(refreshRecipeResult).
package org.domi.furnace.server.menu;

import java.util.Optional;
import org.domi.furnace.server.block.entity.CrafterBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.BlockEntityContainer;

public class CrafterMenu extends ContainerBackedMenu implements MenuListener {

    public static final int RESULT_SLOT = 45;

    private final MenuPlayer owner;
    private final CrafterBlockEntity be;
    private final SimpleContainer resultContainer = new SimpleContainer(1);

    public CrafterMenu(int containerId, Container playerInventory, BlockEntityContainer container, int[] blockPos, MenuPlayer owner) {
        super("minecraft:crafter_3x3", containerId, container, blockPos);
        this.owner = owner;
        this.be = (CrafterBlockEntity) container.blockEntity();
        checkContainerSize(container, 9);
        container.startOpen(owner);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) addSlot(new CrafterSlot(container, c + r * 3));
        addPlayerSlots(playerInventory);
        addSlot(new NonInteractiveResultSlot(resultContainer, 0));
        for (int i = 0; i < 10; i++) {
            final int k = i;
            addDataSlot(DataSlot.of(() -> be.dataValue(k), v -> be.setDataValue(k, v)));
        }
        refreshRecipeResult();
        addSlotListener(this);
    }

    /** 바닐라 isSlotDisabled: 데이터 칸 값이 1. */
    public boolean isSlotDisabled(int slot) { return slot > -1 && slot < 9 && be.dataValue(slot) == 1; }

    public boolean isPowered() { return be.dataValue(9) == 1; }

    public CrafterBlockEntity crafter() { return be; }

    /** 바닐라 refreshRecipeResult: 서버 플레이어일 때 지금 격자의 레시피 결과를 미리보기 칸에. */
    private void refreshRecipeResult() {
        var rm = owner == null ? null : owner.recipes();
        ItemStack out = ItemStack.EMPTY;
        if (rm != null) {
            var input = be.asCraftInput();
            var e = rm.findCrafting(input, null);
            if (e != null) out = e.recipe().assemble(input, rm.context());
        }
        resultContainer.set(0, out);
    }

    @Override public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) { refreshRecipeResult(); }

    @Override public void dataChanged(AbstractContainerMenu menu, int dataIndex, int value) { }

    /** 바닐라 quickMoveStack: 제작기 칸 → 플레이어 칸(9..45, 뒤에서부터), 그 밖 → 제작기 칸(0..9). */
    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack;
            Moved moved = index < 9 ? moveItemStackTo(stack, 9, 45, true) : moveItemStackTo(stack, 0, 9, false);
            if (!moved.changed()) return ItemStack.EMPTY;
            stack = moved.remaining();
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setCountInPlace(stack);
            if (stack.count() == result.count()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return result;
    }

    /** 바닐라 CrafterSlot: 잠긴 칸엔 놓을 수 없고, 바뀌면 메뉴에 slotsChanged. */
    private final class CrafterSlot extends Slot {
        CrafterSlot(Container c, int i) { super(c, i); }

        @Override public boolean mayPlace(ItemStack stack) { return !isSlotDisabled(containerIndex()) && super.mayPlace(stack); }

        @Override public void setChanged() {
            super.setChanged();
            slotsChanged(container);
        }
    }

    /** 바닐라 NonInteractiveResultSlot: 보이기만 하는 결과 칸. */
    static final class NonInteractiveResultSlot extends Slot {
        NonInteractiveResultSlot(Container c, int i) { super(c, i); }

        @Override public void onQuickCraft(ItemStack picked, ItemStack original) { }

        @Override public boolean mayPickup(MenuPlayer player) { return false; }

        @Override public Optional<ItemStack> tryRemove(int amount, int max, MenuPlayer player) { return Optional.empty(); }

        @Override public ItemStack safeTake(int amount, int max, MenuPlayer player) { return ItemStack.EMPTY; }

        @Override public ItemStack safeInsert(ItemStack stack) { return stack; }

        @Override public ItemStack safeInsert(ItemStack stack, int amount) { return safeInsert(stack); }

        @Override public boolean allowModification(MenuPlayer player) { return false; }

        @Override public boolean mayPlace(ItemStack stack) { return false; }

        @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }

        @Override public void onTake(MenuPlayer player, ItemStack stack) { }
    }
}
