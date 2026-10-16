package org.domi.furnace.server.entity;

import org.domi.furnace.server.item.ComponentPatch;

/** 아이템 스택: 아이템 레지스트리 id, 개수, 기본값 대비 컴포넌트 패치(바닐라 ItemStack). 불변. */
public record ItemStack(int itemId, int count, ComponentPatch patch) {

    public static final ItemStack EMPTY = new ItemStack(0, 0, ComponentPatch.EMPTY);

    public ItemStack {
        if (patch == null) patch = ComponentPatch.EMPTY;
    }

    public ItemStack(int itemId, int count) { this(itemId, count, ComponentPatch.EMPTY); }

    public boolean isEmpty() {
        return itemId == 0 || count <= 0;
    }

    public ItemStack withCount(int n) { return n <= 0 ? EMPTY : new ItemStack(itemId, n, patch); }

    /** 바닐라 isSameItemSameComponents. */
    public boolean sameItemSameComponents(ItemStack o) { return itemId == o.itemId && patch.equals(o.patch); }
}
