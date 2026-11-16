// LEGACY-ORACLE since 2b28b2ff: ApiConvert#item(서버 → API, 부를 때마다 SNBT 변환)·ApiConvert#snapshot(칸 전체 즉시 변환) — T-033 이전
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: snapshot 의 입력을 메뉴 대신 (종류 키, 칸 아이템 배열)로 받는다
// (옛 snapshot 은 menu.slots 마다 slot.getItem() 을 순서대로 읽어 같은 배열을 만들었다. 깔때기·양조대 스냅샷도 같은 꼴이었다). 변환 계산은 그 커밋 그대로다.
package org.domi.furnace.server.identity.legacy.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.identity.LegacyOracle;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.world.ItemRegistry;

@LegacyOracle(since = "2b28b2ff", copyOf = "ApiConvert#item(ItemStack, ItemRegistry), ApiConvert#snapshot(AbstractContainerMenu, ItemRegistry)")
public final class LegacyApiConvert {

    private LegacyApiConvert() {}

    public static org.domi.furnace.api.item.ItemStack item(ItemStack s, ItemRegistry items) {
        if (s == null || s.isEmpty()) return org.domi.furnace.api.item.ItemStack.EMPTY;
        Map<String, String> comps = new TreeMap<>();
        java.util.Set<String> removed = new java.util.TreeSet<>();
        for (var e : s.patch().entries().entrySet()) {
            if (e.getValue() == null) removed.add(e.getKey());
            else comps.put(e.getKey(), Snbt.write(toNbt(e.getKey(), e.getValue())));
        }
        String name = items == null ? "minecraft:air" : items.nameOf(s.itemId());
        return new org.domi.furnace.api.item.ItemStack(name, s.count(), comps, removed);
    }

    private static Object toNbt(String component, Object value) {
        var t = org.domi.furnace.server.item.DataComponents.byName(component);
        return t == null || t.isTransient() ? value : t.persistent().toNbt(value);
    }

    public static InventorySnapshot snapshot(String type, ItemStack[] slots, ItemRegistry items) {
        List<org.domi.furnace.api.item.ItemStack> out = new ArrayList<>(slots.length);
        for (ItemStack s : slots) out.add(item(s, items));
        return new InventorySnapshot(type, out);
    }
}
