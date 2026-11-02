// 서버 내부 값 ↔ 플러그인 API 값 변환. 숫자 id 는 이 경계 안에서만 쓰고 API 로는 이름만 내보낸다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;

public final class ApiConvert {

    private ApiConvert() {}

    // ------------------------------------------------------------------ 아이템

    public static org.domi.furnace.api.item.ItemStack item(ItemStack s, ItemRegistry items) {
        if (s == null || s.isEmpty()) return org.domi.furnace.api.item.ItemStack.EMPTY;
        Map<String, String> comps = new TreeMap<>();
        java.util.Set<String> removed = new java.util.TreeSet<>();
        for (var e : s.patch().entries().entrySet()) {
            if (e.getValue() == null) removed.add(e.getKey());
            else comps.put(e.getKey(), Snbt.write(e.getValue()));
        }
        String name = items == null ? "minecraft:air" : items.nameOf(s.itemId());
        return new org.domi.furnace.api.item.ItemStack(name, s.count(), comps, removed);
    }

    /** 알 수 없는 아이템·컴포넌트면 IllegalArgumentException. */
    public static ItemStack item(org.domi.furnace.api.item.ItemStack a, ItemRegistry items) {
        if (a.isEmpty()) return ItemStack.EMPTY;
        int id = items.idOf(a.type());
        if (id < 0) throw new IllegalArgumentException("알 수 없는 아이템: " + a.type());
        Map<String, Object> raw = new LinkedHashMap<>();
        for (var e : a.components().entrySet()) raw.put(e.getKey(), Snbt.parse(e.getValue()));
        for (String r : a.removedComponents()) raw.put(r, null);
        return new ItemStack(id, a.amount(), raw.isEmpty() ? ComponentPatch.EMPTY : ComponentPatch.of(raw));
    }

    // ------------------------------------------------------------------ 블록

    public static BlockData block(int stateId, BlockRegistry blocks) {
        String key = blocks.canonicalKeyOf(stateId);
        if (key == null) return BlockData.of(blocks.nameOf(stateId));
        int semi = key.indexOf(';');
        if (semi < 0) return BlockData.of(key);
        Map<String, String> props = new TreeMap<>();
        for (String kv : key.substring(semi + 1).split(",")) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return new BlockData(key.substring(0, semi), props);
    }

    /**
     * 이름·속성이 레지스트리에 없으면 IllegalArgumentException(근사 상태로 대체하지 않는다).
     * 적지 않은 속성은 기본 상태의 값을 쓴다(바닐라 setblock 과 같다).
     */
    public static int stateId(BlockData data, BlockRegistry blocks) {
        int def = blocks.defaultStateId(data.type());   // 모르는 블록이면 IllegalArgumentException
        if (data.properties().isEmpty()) return def;
        Map<String, String> merged = new TreeMap<>(block(def, blocks).properties());
        for (var e : data.properties().entrySet()) {
            if (!merged.containsKey(e.getKey())) throw new IllegalArgumentException("없는 속성: " + data);
            merged.put(e.getKey(), e.getValue());
        }
        int id = blocks.exactStateId(org.domi.furnace.server.world.BlockStateKey.of(data.type(), merged));
        if (id < 0) throw new IllegalArgumentException("알 수 없는 블록 상태: " + data);
        return id;
    }

    // ------------------------------------------------------------------ 창

    public static InventorySnapshot snapshot(AbstractContainerMenu menu, ItemRegistry items) {
        List<org.domi.furnace.api.item.ItemStack> out = new ArrayList<>(menu.slots.size());
        for (var slot : menu.slots) out.add(item(slot.getItem(), items));
        return new InventorySnapshot(menuType(menu), out);
    }

    /** 메뉴 종류 키 — 클래스 이름에서 만든다(InventoryMenu 는 minecraft:player). 바닐라 menu type 이름과 같지 않을 수 있다(가정). */
    static String menuType(AbstractContainerMenu menu) {
        String n = menu.getClass().getSimpleName();
        if (n.equals("InventoryMenu")) return "minecraft:player";
        if (n.endsWith("Menu")) n = n.substring(0, n.length() - 4);
        StringBuilder sb = new StringBuilder("minecraft:");
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
