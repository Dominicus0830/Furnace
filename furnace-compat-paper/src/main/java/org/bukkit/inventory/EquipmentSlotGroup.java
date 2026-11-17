package org.bukkit.inventory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** 속성 수정치가 걸리는 장비 칸 묶음(바닐라 EquipmentSlotGroup, 1.20.5+). */
public final class EquipmentSlotGroup implements Predicate<EquipmentSlot> {

    private static final Map<String, EquipmentSlotGroup> BY_NAME = new HashMap<>();

    public static final EquipmentSlotGroup ANY = get("any", s -> true, EquipmentSlot.HAND);
    public static final EquipmentSlotGroup MAINHAND = get("mainhand", EquipmentSlot.HAND);
    public static final EquipmentSlotGroup OFFHAND = get("offhand", EquipmentSlot.OFF_HAND);
    public static final EquipmentSlotGroup HAND = get("hand", s -> s == EquipmentSlot.HAND || s == EquipmentSlot.OFF_HAND, EquipmentSlot.HAND);
    public static final EquipmentSlotGroup FEET = get("feet", EquipmentSlot.FEET);
    public static final EquipmentSlotGroup LEGS = get("legs", EquipmentSlot.LEGS);
    public static final EquipmentSlotGroup CHEST = get("chest", EquipmentSlot.CHEST);
    public static final EquipmentSlotGroup HEAD = get("head", EquipmentSlot.HEAD);
    public static final EquipmentSlotGroup ARMOR = get("armor",
            s -> s == EquipmentSlot.FEET || s == EquipmentSlot.LEGS || s == EquipmentSlot.CHEST || s == EquipmentSlot.HEAD, EquipmentSlot.CHEST);

    private final String key;
    private final Predicate<EquipmentSlot> predicate;
    private final EquipmentSlot example;

    private EquipmentSlotGroup(String key, Predicate<EquipmentSlot> predicate, EquipmentSlot example) {
        this.key = key;
        this.predicate = predicate;
        this.example = example;
    }

    @Override
    public boolean test(EquipmentSlot slot) { return predicate.test(slot); }

    /** @deprecated 이 묶음의 대표 칸(ANY 는 HAND). */
    @Deprecated
    public EquipmentSlot getExample() { return example; }

    @Override
    public String toString() { return key; }

    /** 이름(mainhand·armor …, 대소문자 무시)으로 찾기. 없으면 null. */
    public static EquipmentSlotGroup getByName(String name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null");
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    private static EquipmentSlotGroup get(String key, EquipmentSlot slot) { return get(key, s -> s == slot, slot); }

    private static EquipmentSlotGroup get(String key, Predicate<EquipmentSlot> p, EquipmentSlot example) {
        EquipmentSlotGroup g = new EquipmentSlotGroup(key, p, example);
        BY_NAME.put(key, g);
        return g;
    }
}
