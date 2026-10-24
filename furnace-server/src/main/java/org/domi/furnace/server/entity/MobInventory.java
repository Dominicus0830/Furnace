// 바닐라 SimpleContainer(몹용): 칸 배열 하나. 같은 아이템 칸에 먼저 합치고 남으면 빈 칸에 넣으며, NBT 목록으로 저장한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class MobInventory {
    private final ItemStack[] items;

    public MobInventory(int size) {
        items = new ItemStack[size];
        Arrays.fill(items, ItemStack.EMPTY);
    }

    public int size() { return items.length; }

    public ItemStack get(int i) { return i >= 0 && i < items.length ? items[i] : ItemStack.EMPTY; }

    public void set(int i, ItemStack s) { items[i] = s == null ? ItemStack.EMPTY : s; }

    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    /** removeItem(slot, count): 칸에서 count 개까지 꺼낸다. */
    public ItemStack removeItem(int i, int count) {
        ItemStack cur = get(i);
        if (cur.isEmpty() || count <= 0) return ItemStack.EMPTY;
        int n = Math.min(count, cur.count());
        items[i] = cur.withCount(cur.count() - n);
        return cur.withCount(n);
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            if (!items[i].isEmpty()) list.add(items[i]);
            items[i] = ItemStack.EMPTY;
        }
        return list;
    }

    public boolean canAddItem(ItemStack stack) {
        for (ItemStack s : items) {
            if (s.isEmpty() || s.sameItemSameComponents(stack) && s.count() < Math.min(99, ItemStackCodecs.maxStackSize(s))) return true;
        }
        return false;
    }

    /** addItem: 같은 아이템 칸에 먼저 합치고 남은 것은 빈 칸에 통째로 넣는다. 못 넣은 나머지를 돌려준다. */
    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack rem = stack;
        for (int i = 0; i < items.length && !rem.isEmpty(); i++) {
            ItemStack s = items[i];
            if (!s.isEmpty() && s.sameItemSameComponents(rem)) {
                int max = Math.min(99, ItemStackCodecs.maxStackSize(s));
                int j = Math.min(rem.count(), max - s.count());
                if (j > 0) {
                    items[i] = s.withCount(s.count() + j);
                    rem = rem.withCount(rem.count() - j);
                }
            }
        }
        if (rem.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < items.length && !rem.isEmpty(); i++) {
            if (items[i].isEmpty()) {
                items[i] = rem;
                rem = ItemStack.EMPTY;
            }
        }
        return rem;
    }

    public NbtList createTag() {
        NbtList list = new NbtList((byte) 10);
        for (int i = 0; i < items.length; i++) {
            if (items[i].isEmpty()) continue;
            list.add(ItemStackNbt.write(items[i]));
        }
        return list;
    }

    public void fromTag(NbtList list) {
        Arrays.fill(items, ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof NbtCompound c) addItem(ItemStackNbt.read(c));
        }
    }
}
