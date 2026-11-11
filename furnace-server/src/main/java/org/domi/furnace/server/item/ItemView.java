// 아이템 술어가 보는 아이템(바닐라 ItemStack 의 읽기 면): 아이템 이름·개수·컴포넌트(기본값 + 패치의 정규 값).
// 명령(엔티티 ItemStack)·전리품(LootStack)·컴포넌트 안에 든 아이템(용기·묶음의 정규 값)을 같은 술어로 보게 한다.
package org.domi.furnace.server.item;

import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;

public interface ItemView {

    /** 아이템 이름. 빈 스택이면 minecraft:air. */
    String item();

    /** 개수. 빈 스택이면 0(바닐라 getCount). */
    int count();

    /** 기본값 + 패치의 정규 값(없거나 지웠으면 null) — 바닐라 ItemStack.get. 빈 스택은 컴포넌트가 없다. */
    Object get(String component);

    static ItemView of(ItemStack s) {
        if (s == null || s.isEmpty()) return EMPTY;
        String name = ItemContext.name("minecraft:item", s.itemId());
        if (name == null) return EMPTY;
        Map<String, Object> patch = s.patch().entries();
        int count = s.count();
        return new ItemView() {
            public String item() { return name; }
            public int count() { return count; }
            public Object get(String component) {
                if (patch.containsKey(component)) return patch.get(component);
                return ItemPrototypes.defaultValue(name, component);
            }
        };
    }

    /** 컴포넌트 안의 정규 아이템 값({id, count?, components?}, ItemStackCodecs). */
    static ItemView ofCanonical(Object stack) {
        if (stack == null) return EMPTY;
        Map<String, Object> m = Val.map(stack);
        String name = (String) m.get("id");
        if (name == null || ItemStackCodecs.AIR.equals(name)) return EMPTY;
        int count = m.get("count") instanceof Number n ? n.intValue() : 1;
        if (count <= 0) return EMPTY;
        Map<String, Object> patch = ItemStackCodecs.patchOf(m).entries();
        return new ItemView() {
            public String item() { return name; }
            public int count() { return count; }
            public Object get(String component) {
                if (patch.containsKey(component)) return patch.get(component);
                return ItemPrototypes.defaultValue(name, component);
            }
        };
    }

    ItemView EMPTY = new ItemView() {
        public String item() { return ItemStackCodecs.AIR; }
        public int count() { return 0; }
        public Object get(String component) { return null; }
    };
}
