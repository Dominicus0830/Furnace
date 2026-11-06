// 바닐라 LecternMenu(독서대, lectern) — 칸 1개(책, 꺼내기 불가 화면용), 데이터 0 = 펼친 쪽. 버튼: 1 이전, 2 다음, 3 책 꺼내기, 100+n 쪽 n.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.LecternContainer;
import org.domi.furnace.server.world.MenuBlockContext;

public class LecternMenu extends AbstractContainerMenu {

    private final int[] blockPos;
    private final LecternContainer lectern;
    private final LecternBlockEntity be;

    public LecternMenu(int containerId, LecternContainer lectern, int[] blockPos) {
        super("minecraft:lectern", containerId);
        checkContainerSize(lectern, 1);
        this.lectern = lectern;
        this.be = lectern.blockEntity();
        this.blockPos = blockPos;
        addSlot(new Slot(lectern, 0) {
            @Override public void setChanged() {
                super.setChanged();
                slotsChanged(container);
            }
        });
        addDataSlot(DataSlot.of(be::page, v -> {
            var r = MenuBlockContext.current();
            be.setPage(v, r == null ? null : r.level());
        }));
    }

    @Override public int[] containerBlockPos() { return blockPos; }

    public ItemStack book() { return lectern.get(0); }

    @Override
    public boolean clickMenuButton(MenuPlayer player, int id) {
        if (id >= 100) {
            setData(0, id - 100);
            return true;
        }
        switch (id) {
            case 1 -> { setData(0, be.page() - 1); return true; }
            case 2 -> { setData(0, be.page() + 1); return true; }
            case 3 -> {
                if (!player.mayBuild()) return false;
                ItemStack book = lectern.removeNoUpdate(0);
                lectern.setChanged();
                if (!player.addToInventory(book).isEmpty()) player.drop(book, false);   // Inventory.add 실패하면 던진다
                return true;
            }
            default -> { return false; }
        }
    }

    @Override public ItemStack quickMoveStack(MenuPlayer player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(MenuPlayer player) { return lectern.stillValid(player); }
}
