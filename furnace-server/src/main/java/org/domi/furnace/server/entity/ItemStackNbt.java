// 아이템 스택 ↔ NBT({id, count, components}) — 엔티티 저장(손·방어구·주민 거래 등)이 같이 쓴다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemNbt;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class ItemStackNbt {
    private ItemStackNbt() { }

    /** 빈 스택은 빈 태그. */
    public static NbtCompound write(ItemStack s) {
        NbtCompound it = new NbtCompound();
        if (s == null || s.isEmpty()) return it;
        it.putString("id", ItemTools.itemName(s));
        it.putInt("count", s.count());
        NbtCompound components = ItemNbt.write(s.patch());
        if (components != null) it.putCompound(ItemNbt.KEY, components);
        return it;
    }

    public static ItemStack read(NbtCompound it) {
        if (it == null || !(it.tags().get("id") instanceof String name)) return ItemStack.EMPTY;
        Integer itemId = ItemContext.id("minecraft:item", name);
        int count = it.tags().get("count") instanceof Number c ? c.intValue() : 1;
        if (itemId == null || itemId <= 0 || count <= 0) return ItemStack.EMPTY;
        var patch = ItemNbt.read(it, name, "furnace:components");
        return patch == null ? ItemStack.EMPTY : new ItemStack(itemId, count, patch);
    }
}
