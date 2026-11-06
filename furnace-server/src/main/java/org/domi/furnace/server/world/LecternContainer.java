// 독서대의 책 한 칸을 메뉴용 Container 로 본 것(바닐라 LecternBlockEntity.bookAccess). 쓰기는 블록의 region 스레드(MenuBlockContext)에서만.
package org.domi.furnace.server.world;

import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.block.types.LecternBlock;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.MenuPlayer;

public final class LecternContainer implements Container {

    private final LecternBlockEntity be;

    public LecternContainer(LecternBlockEntity be) { this.be = be; }

    public LecternBlockEntity blockEntity() { return be; }

    @Override public int size() { return 1; }

    @Override public ItemStack get(int index) { return index == 0 ? be.book() : ItemStack.EMPTY; }

    @Override public void set(int index, ItemStack stack) { }   // 바닐라 setItem 은 아무것도 하지 않는다

    @Override public ItemStack remove(int index, int amount) {
        if (index != 0) return ItemStack.EMPTY;
        ItemStack book = be.book();
        removeBook();
        return book;
    }

    @Override public ItemStack removeNoUpdate(int index) {
        if (index != 0) return ItemStack.EMPTY;
        ItemStack book = be.book();
        removeBook();
        return book;
    }

    /** onBookItemRemoved: 쪽 0, 블록 상태 has_book=false(신호 끔). */
    private void removeBook() {
        Region r = MenuBlockContext.current();
        be.setBook(ItemStack.EMPTY, r == null ? null : r.level());
        if (r != null) LecternBlock.resetBookState(null, r.level(), be.pos(), r.level().getBlockState(be.pos()), false);
    }

    @Override public void setChanged() {
        Region r = MenuBlockContext.current();
        if (r != null) r.markBlockEntityChanged(be);
    }

    @Override public int maxStackSize() { return 1; }

    @Override public boolean stillValid(MenuPlayer player) {
        var p = be.pos();
        return !be.isRemoved() && player.distanceToSqr(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5) <= 64.0;
    }
}
