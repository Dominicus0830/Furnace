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
    private final org.domi.furnace.server.entity.Player opener;
    private final OpenableContainer.RegionRunner runner;

    public EntityContainer(ContainerEntity entity) { this(entity, null, null); }

    /** opener·runner 가 있으면 닫을 때 엔티티의 region 에서 stopOpen(container_close)을 부른다. */
    public EntityContainer(ContainerEntity entity, org.domi.furnace.server.entity.Player opener, OpenableContainer.RegionRunner runner) {
        this.entity = entity;
        this.opener = opener;
        this.runner = runner;
    }

    public ContainerEntity entity() { return entity; }

    /** 칸에 닿기 전에 열지 않은 전리품 표를 푼다(바닐라 ContainerEntity 의 getItem·setItem·removeItem 이 unpackChestVehicleLootTable(null)). */
    private ItemStack[] items() {
        entity.unpackLootTable();
        return entity.chestVehicleItems();
    }

    /** getContainerSize: 표를 풀지 않는다. */
    @Override public int size() { return entity.chestVehicleItems().length; }

    /** isChestVehicleEmpty: 표를 풀지 않고 지금 칸만 본다. */
    @Override public boolean isEmpty() {
        for (ItemStack s : entity.chestVehicleItems()) if (s != null && !s.isEmpty()) return false;
        return true;
    }

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

    /**
     * stopOpen: 엔티티 용기의 stopOpen(상자 배·상자 수레의 container_close)을 그 엔티티를 가진 region 스레드에서.
     * 지금 스레드가 그 엔티티를 가진 region 이면 바로, 아니면 엔티티 자리의 region 으로 보낸다(그 사이 지워졌거나 옮겨 갔으면 생략).
     */
    @Override public void stopOpen(MenuPlayer player) {
        if (opener == null) return;
        var e = entity.asEntity();
        Region cur = MenuBlockContext.current();
        if (cur != null && cur.level().entities().get(e.id()) == e) {
            if (!e.isRemoved()) entity.stopOpen(opener);
        } else if (runner != null) {
            runner.runAt(e.x(), e.z(), r -> MenuBlockContext.runIn(r, () -> {
                if (!e.isRemoved() && r.level().entities().get(e.id()) == e) entity.stopOpen(opener);
            }));
        } else if (cur == null && !e.isRemoved()) {
            entity.stopOpen(opener);   // region 이 없는 단독 레벨(시험)
        }
    }
}
