// 상자 블록엔티티를 메뉴용 Container 로 본 것. 읽기는 어느 스레드에서든(배열 칸의 참조를 읽을 뿐), 쓰기는 그 상자를 가진 region 스레드에서만 —
// 메뉴 클릭을 그 region 에서 처리하며 MenuBlockContext.runIn 으로 그 region 을 알려 준다(바닐라의 단일 스레드 가정을 region 모델로 옮긴 것).
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.MenuPlayer;

public final class ChestContainer implements Container {

    private final ChestBlockEntity chest;

    public ChestContainer(ChestBlockEntity chest) { this.chest = chest; }

    public ChestBlockEntity chest() { return chest; }

    private static Region writer() {
        Region r = MenuBlockContext.current();
        if (r == null) throw new IllegalStateException("상자 쓰기는 소유 region 스레드의 MenuBlockContext.runIn 안에서만 가능");
        return r;
    }

    @Override public int size() { return ChestBlockEntity.SLOTS; }

    @Override public ItemStack get(int index) {
        if (index < 0 || index >= chest.items.length) return ItemStack.EMPTY;
        ItemStack s = chest.items[index];
        return s == null ? ItemStack.EMPTY : s;
    }

    /** setItem: 칸을 바꾸고 변경을 알린다(저장 대상 표시·비교기). */
    @Override public void set(int index, ItemStack stack) {
        writer().setChestItem(chest.x, chest.y, chest.z, index, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override public ItemStack remove(int index, int amount) {
        ItemStack cur = get(index);
        if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int n = Math.min(amount, cur.count());
        chest.items[index] = cur.withCount(cur.count() - n);
        setChanged();
        return cur.withCount(n);
    }

    @Override public ItemStack removeNoUpdate(int index) {
        ItemStack cur = get(index);
        if (index >= 0 && index < chest.items.length) chest.items[index] = ItemStack.EMPTY;
        return cur;
    }

    /** 바닐라가 스택의 count 만 그 자리에서 바꾸는 곳 — setItem 없이 배열만 바꾼다(저장 표시는 뒤따르는 setChanged 가). */
    @Override public void replaceInPlace(int index, ItemStack stack) { chest.items[index] = stack; }

    @Override public void setChanged() { writer().markChestChanged(chest.x, chest.y, chest.z); }

    /** Container.stillValidBlockEntity: 그 블록엔티티가 살아 있고 상자 중심에서 8칸 이내. */
    @Override public boolean stillValid(MenuPlayer player) {
        return !chest.isRemoved() && player.distanceToSqr(chest.x + 0.5, chest.y + 0.5, chest.z + 0.5) <= 64.0;
    }
}
