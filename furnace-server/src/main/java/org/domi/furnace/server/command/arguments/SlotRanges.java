// 바닐라 SlotRanges: 슬롯 이름 → 슬롯 번호 목록(item_slot·item_slots 인자). 이름 순서·번호는 바닐라 표와 같다.
// 장비 번호는 EquipmentSlot.getIndex: 손 98+(0 주손, 1 보조손), 방어구 100+(0 발, 1 다리, 2 가슴, 3 머리), 몸 105.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SlotRanges {
    private SlotRanges() {}

    /** 이름 하나와 그 슬롯들. */
    public record Range(String name, List<Integer> slots) {
        public int size() { return slots.size(); }
    }

    private static final Map<String, Range> BY_NAME = new LinkedHashMap<>();

    private static void add(String name, Integer... slots) { BY_NAME.putIfAbsent(name, new Range(name, List.of(slots))); }

    private static void addRange(String prefix, int start, int size) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            add(prefix + i, start + i);
            all.add(start + i);
        }
        BY_NAME.putIfAbsent(prefix + "*", new Range(prefix + "*", Collections.unmodifiableList(all)));
    }

    static {
        add("contents", 0);
        addRange("container.", 0, 54);
        addRange("hotbar.", 0, 9);
        addRange("inventory.", 9, 27);
        addRange("enderchest.", 200, 27);
        addRange("villager.", 300, 8);
        addRange("horse.", 500, 15);
        add("weapon", 98);
        add("weapon.mainhand", 98);
        add("weapon.offhand", 99);
        add("weapon.*", 98, 99);
        add("armor.head", 103);
        add("armor.chest", 102);
        add("armor.legs", 101);
        add("armor.feet", 100);
        add("armor.body", 105);
        add("armor.*", 103, 102, 101, 100, 105);
        add("horse.saddle", 400);
        add("horse.chest", 499);
        add("player.cursor", 499);   // 바닐라 표 그대로(horse.chest 와 같은 번호)
        addRange("player.crafting.", 500, 4);
    }

    /** 이름으로(없으면 null). */
    public static Range byName(String name) { return BY_NAME.get(name); }

    public static List<String> names() { return List.copyOf(BY_NAME.keySet()); }
}
