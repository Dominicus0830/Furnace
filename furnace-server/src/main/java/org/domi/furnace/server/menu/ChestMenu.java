// 바닐라 ChestMenu(generic_9xN) — 위에 컨테이너 rows*9 칸, 아래에 플레이어 주 인벤토리 27칸과 핫바 9칸.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;

public class ChestMenu extends AbstractContainerMenu {

    private final Container container;
    private final int rows;
    private final int[] blockPos;

    /** @param menuType minecraft:menu 이름(예: minecraft:generic_9x3). @param blockPos 블록에 매인 컨테이너면 좌표, 아니면 null. */
    public ChestMenu(String menuType, int containerId, Container playerInventory, Container container, int rows, int[] blockPos, MenuPlayer opener) {
        super(menuType, containerId);
        checkContainerSize(container, rows * 9);
        this.container = container;
        this.rows = rows;
        this.blockPos = blockPos;
        container.startOpen(opener);
        for (int r = 0; r < rows; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(container, c + r * 9));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    public static ChestMenu threeRows(int containerId, Container playerInventory, Container container, int[] blockPos, MenuPlayer opener) {
        return new ChestMenu("minecraft:generic_9x3", containerId, playerInventory, container, 3, blockPos, opener);
    }

    public Container container() { return container; }

    public int rows() { return rows; }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) { return container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack;
            Moved moved = index < rows * 9 ? moveItemStackTo(stack, rows * 9, slots.size(), true) : moveItemStackTo(stack, 0, rows * 9, false);
            if (!moved.changed()) return ItemStack.EMPTY;
            if (moved.remaining().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setCountInPlace(moved.remaining());
        }
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
