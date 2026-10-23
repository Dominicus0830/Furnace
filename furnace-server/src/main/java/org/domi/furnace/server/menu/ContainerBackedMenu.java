// 위에 용기 칸들, 아래에 플레이어 주 인벤토리 27칸과 핫바 9칸이 오는 메뉴의 바탕(바닐라 ChestMenu·ShulkerBoxMenu·HopperMenu·DispenserMenu 공통):
// 용기는 블록 엔티티에 매이고(블록 좌표 → 그 region 에서 클릭 처리), 열고 닫을 때 startOpen/stopOpen 을 부른다.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;

public abstract class ContainerBackedMenu extends AbstractContainerMenu {

    protected final Container container;
    private final int[] blockPos;

    protected ContainerBackedMenu(String menuType, int containerId, Container container, int[] blockPos) {
        super(menuType, containerId);
        this.container = container;
        this.blockPos = blockPos;
    }

    /** 용기 칸 다음에 플레이어 주 인벤토리 3줄과 핫바를 붙인다. */
    protected final void addPlayerSlots(Container playerInventory) {
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    public Container container() { return container; }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) { return container.stillValid(player); }

    /** 바닐라 DispenserMenu 만 이동 뒤 onTake 를 부르고 개수가 그대로면 EMPTY 를 돌려준다. */
    protected boolean notifiesTake() { return false; }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack;
            int n = container.size();
            Moved moved = index < n ? moveItemStackTo(stack, n, slots.size(), true) : moveItemStackTo(stack, 0, n, false);
            if (!moved.changed()) return ItemStack.EMPTY;
            if (moved.remaining().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setCountInPlace(moved.remaining());
            if (notifiesTake()) {
                if (moved.remaining().count() == result.count()) return ItemStack.EMPTY;
                slot.onTake(player, moved.remaining());
            }
        }
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
