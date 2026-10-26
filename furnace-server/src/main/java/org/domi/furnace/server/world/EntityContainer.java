// 엔티티 용기(상자 배·상자/깔때기 수레)를 메뉴용 Container 로 본 것(바닐라 ContainerEntity 의 Container 기본 구현).
// 쓰기는 그 엔티티를 가진 region 스레드에서만 — 창의 클릭은 엔티티 좌표(containerPos)의 region 에서 처리되고, stillValid 가 소유를 확인한다.
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ContainerEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.MenuPlayer;
import org.domi.furnace.server.menu.Stacks;

public final class EntityContainer implements Container {

    private final ContainerEntity entity;

    public EntityContainer(ContainerEntity entity) { this.entity = entity; }

    public ContainerEntity entity() { return entity; }

    private ItemStack[] items() { return entity.chestVehicleItems(); }

    @Override public int size() { return items().length; }

    @Override public ItemStack get(int i) {
        ItemStack[] it = items();
        return i >= 0 && i < it.length && it[i] != null ? it[i] : ItemStack.EMPTY;
    }

    /** setChestVehicleItem: 넣고 그 칸 최대 개수로 자른다(limitSize). */
    @Override public void set(int i, ItemStack s) {
        if (s == null) s = ItemStack.EMPTY;
        int max = Math.min(maxStackSize(), Stacks.maxStack(s));
        if (!s.isEmpty() && s.count() > max) s = s.withCount(max);
        items()[i] = s;
    }

    /** ContainerHelper.removeItem: 범위 안이고 비지 않았고 amount > 0 이면 떼어 낸다. */
    @Override public ItemStack remove(int i, int amount) {
        ItemStack cur = get(i);
        if (i < 0 || i >= size() || cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int n = Math.min(amount, cur.count());
        items()[i] = cur.withCount(cur.count() - n);
        return cur.withCount(n);
    }

    @Override public ItemStack removeNoUpdate(int i) {
        ItemStack cur = get(i);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        items()[i] = ItemStack.EMPTY;
        return cur;
    }

    @Override public void replaceInPlace(int i, ItemStack s) { items()[i] = s; }

    /** 엔티티 용기의 setChanged 는 아무것도 하지 않는다(엔티티는 늘 저장된다). */
    @Override public void setChanged() { }

    /** isChestVehicleStillValid: 지워지지 않았고 4칸 여유 안(그리고 이 region 이 엔티티를 가졌다). */
    @Override public boolean stillValid(MenuPlayer player) {
        var e = entity.asEntity();
        return MenuBlockContext.ownsEntity(e) && !e.isRemoved() && player.canInteractWithEntity(e.boundingBox(), 4.0);
    }

    @Override public int[] containerPos() { return MenuBlockContext.blockPos(entity.asEntity()); }
}
