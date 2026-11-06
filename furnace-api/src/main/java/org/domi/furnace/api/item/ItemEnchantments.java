// minecraft:enchantments / stored_enchantments — 마법 부여 키(minecraft:sharpness) → 레벨(1~255).
// 레벨 0 이하는 넣지 않는다(바닐라 ItemEnchantments.Mutable.set 과 같다). 불변.
package org.domi.furnace.api.item;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record ItemEnchantments(Map<String, Integer> levels, boolean showInTooltip) {

    public static final ItemEnchantments EMPTY = new ItemEnchantments(Map.of(), true);

    public ItemEnchantments {
        TreeMap<String, Integer> m = new TreeMap<>();
        for (var e : levels.entrySet()) {
            String key = e.getKey().indexOf(':') < 0 ? "minecraft:" + e.getKey() : e.getKey();
            int lv = e.getValue();
            if (lv > 255) throw new IllegalArgumentException("마법 부여 레벨은 255 이하: " + key + "=" + lv);
            if (lv > 0) m.put(key, lv);
        }
        levels = m.isEmpty() ? Map.of() : Collections.unmodifiableMap(m);
    }

    public ItemEnchantments(Map<String, Integer> levels) { this(levels, true); }

    public int level(String enchantment) {
        return levels.getOrDefault(enchantment.indexOf(':') < 0 ? "minecraft:" + enchantment : enchantment, 0);
    }

    public ItemEnchantments with(String enchantment, int level) {
        TreeMap<String, Integer> m = new TreeMap<>(levels);
        m.put(enchantment.indexOf(':') < 0 ? "minecraft:" + enchantment : enchantment, level);
        return new ItemEnchantments(m, showInTooltip);
    }

    public ItemEnchantments without(String enchantment) { return with(enchantment, 0); }

    public ItemEnchantments withShowInTooltip(boolean show) { return new ItemEnchantments(levels, show); }

    public boolean isEmpty() { return levels.isEmpty(); }
}
