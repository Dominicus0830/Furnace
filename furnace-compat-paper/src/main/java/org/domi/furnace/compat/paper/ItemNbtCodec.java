// 블록 엔티티 NBT 의 Items 목록({Slot, id, count, components}) ↔ Furnace API 아이템. API 아이템은 컴포넌트를 바닐라 NBT 값의 SNBT 로 들고 있어
// 서로 그대로 옮긴다("!minecraft:x":{} 는 지운 컴포넌트). 블록 상태의 사본 인벤토리(getSnapshotInventory)가 쓴다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.nbt.Snbt;

public final class ItemNbtCodec {

    private ItemNbtCodec() {}

    /** Items 목록 → 칸 size 개(없는 칸·이상한 항목은 EMPTY — 바닐라 loadAllItems 처럼 그 칸만 건너뛴다). */
    public static List<ItemStack> readItems(NbtCompound blockEntity, int size) {
        List<ItemStack> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(ItemStack.EMPTY);
        for (Object o : blockEntity.getList("Items").values()) {
            if (!(o instanceof NbtCompound e)) continue;
            int slot = e.getByte("Slot") & 0xFF;
            if (slot >= size) continue;
            try {
                ItemStack s = read(e);
                if (s != null) out.set(slot, s);
            } catch (RuntimeException bad) {
                // 이상한 항목은 그 칸만 건너뛴다
            }
        }
        return out;
    }

    /** 칸 → Items 목록(비지 않은 칸만, 칸 번호 순 — ContainerHelper.saveAllItems). */
    public static NbtList writeItems(List<ItemStack> slots) {
        NbtList list = new NbtList();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = slots.get(i);
            if (s == null || s.isEmpty()) continue;
            NbtCompound e = write(s);
            e.putByte("Slot", (byte) i);
            list.add(e);
        }
        return list;
    }

    /** 항목 하나(ItemStack.parseOptional) — 아이템이 없으면 null. */
    static ItemStack read(NbtCompound e) {
        String id = e.getString("id");
        int count = e.contains("count") ? e.getInt("count") : 1;
        if (id.isEmpty() || "minecraft:air".equals(id) || count <= 0) return null;
        Map<String, String> comps = new TreeMap<>();
        Set<String> removed = new TreeSet<>();
        NbtCompound c = e.getCompound("components");
        for (String k : c.keys()) {
            if (k.startsWith("!")) removed.add(k.substring(1));
            else comps.put(k, Snbt.write(c.get(k)));
        }
        return new ItemStack(id, count, comps, removed);
    }

    /** 항목 하나(ItemStack.save — 컴포넌트가 없으면 components 를 쓰지 않는다). */
    static NbtCompound write(ItemStack s) {
        NbtCompound e = new NbtCompound();
        e.putString("id", s.type());
        e.putInt("count", s.amount());
        if (!s.components().isEmpty() || !s.removedComponents().isEmpty()) {
            NbtCompound c = new NbtCompound();
            for (var en : s.components().entrySet()) c.put(en.getKey(), Snbt.parse(en.getValue()));
            for (String r : s.removedComponents()) c.putCompound("!" + r, new NbtCompound());
            e.putCompound("components", c);
        }
        return e;
    }
}
