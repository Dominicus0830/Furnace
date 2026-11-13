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
            else comps.put(e.getKey(), Snbt.write(toNbt(e.getKey(), e.getValue())));
        }
        String name = items == null ? "minecraft:air" : items.nameOf(s.itemId());
        return new org.domi.furnace.api.item.ItemStack(name, s.count(), comps, removed);
    }

    /**
     * 패치의 정규 값은 NBT 가 아니라 코덱의 값(마법 부여는 Map 등)일 수 있다 — 저장할 때처럼 그 컴포넌트의 코덱으로 NBT 로 바꾼다
     * (예전엔 그대로 SNBT 로 쓰다 "NBT 값이 아님" 으로 실패해, 마법 부여 아이템을 /give 하면 드롭 이벤트 구독 플러그인이 있을 때 명령이 오류를 냈다).
     * 저장되지 않는(transient) 컴포넌트는 값이 이미 NBT 꼴이다.
     */
    private static Object toNbt(String component, Object value) {
        var t = org.domi.furnace.server.item.DataComponents.byName(component);
        return t == null || t.isTransient() ? value : t.persistent().toNbt(value);
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

    /**
     * 상태 번호 → 불변 API BlockData. 같은 번호는 같은 객체를 돌려준다(레지스트리가 상태 번호별로 한 번만 만든다 — 속성 문자열 쪼개기·TreeMap 을 호출마다
     * 하지 않는다). BlockData 는 불변 record 라 공유해도 관측 결과가 같다. 레지스트리 밖 번호는 캐시하지 않고 그때그때 만든다.
     */
    public static BlockData block(int stateId, BlockRegistry blocks) {
        BlockData hit = blocks.cachedApiBlock(stateId);
        if (hit != null) return hit;
        BlockData made = buildBlock(stateId, blocks);
        if (stateId >= 0 && stateId < blocks.totalStateCount()) {
            made = shareStrings(made);
            blocks.cacheApiBlock(stateId, made);
        }
        return made;
    }

    /** 오래 남는 캐시 값의 종류·속성 이름·값 문자열을 JVM 에서 하나로 합친다(상태마다 substring 으로 새로 만든 문자열이 상태당 수백 바이트였다). 값은 같다. */
    private static BlockData shareStrings(BlockData d) {
        String type = d.type().intern();
        if (d.properties().isEmpty()) return new BlockData(type, Map.of());
        Map<String, String> props = new TreeMap<>();
        for (var e : d.properties().entrySet()) props.put(e.getKey().intern(), e.getValue().intern());
        return new BlockData(type, props);
    }

    /** 캐시를 거치지 않고 만든다(시험이 캐시 결과와 전수 비교하는 기준). */
    static BlockData buildBlock(int stateId, BlockRegistry blocks) {
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
