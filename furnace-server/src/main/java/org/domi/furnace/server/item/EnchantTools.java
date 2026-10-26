// 바닐라 EnchantmentHelper 중 메뉴(숫돌·모루)가 쓰는 부분 — 인챈트 읽기·고치기(ItemEnchantments.Mutable), 저주 판정.
package org.domi.furnace.server.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.domi.furnace.server.entity.ItemStack;

public final class EnchantTools {
    private EnchantTools() { }

    public static final String ENCHANTMENTS = "minecraft:enchantments", STORED = "minecraft:stored_enchantments";

    private static boolean isBook(ItemStack s) { return "minecraft:enchanted_book".equals(ItemTools.itemName(s)); }

    /** 바닐라 getComponentType: 인챈트 책은 stored_enchantments, 그 밖은 enchantments. */
    public static String componentFor(ItemStack s) { return isBook(s) ? STORED : ENCHANTMENTS; }

    /** 컴포넌트의 인챈트 → 레벨(없으면 빈 맵). */
    public static Map<String, Integer> levels(ItemStack s, String component) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (ItemTools.component(s, component) instanceof Map<?, ?> m && m.get("levels") instanceof Map<?, ?> l) {
            for (var e : l.entrySet()) if (e.getValue() instanceof Number n) out.put(String.valueOf(e.getKey()), n.intValue());
        }
        return out;
    }

    /** 바닐라 getEnchantmentsForCrafting. */
    public static Map<String, Integer> forCrafting(ItemStack s) { return levels(s, componentFor(s)); }

    /** 바닐라 hasAnyEnchantments: enchantments 나 stored_enchantments 가 비어 있지 않다. */
    public static boolean hasAny(ItemStack s) { return !levels(s, ENCHANTMENTS).isEmpty() || !levels(s, STORED).isEmpty(); }

    public static boolean isCurse(String enchantment) {
        Integer id = ItemContext.id("minecraft:enchantment", enchantment);
        return id != null && ItemContext.isInTag("minecraft:enchantment", "minecraft:curse", id);
    }

    /**
     * 바닐라 EnchantmentHelper.enchantItemFromProvider(stack, registries, provider, difficulty, random):
     * 데이터 팩의 enchantment_provider 를 찾아 걸고, 없으면(데이터 팩 없음 포함) 그대로 돌려준다. 난수는 넘긴 random 만 쓴다.
     */
    public static ItemStack enchantFromProvider(ItemStack s, org.domi.furnace.server.loot.LootData data, String providerId, float special,
                                                org.domi.furnace.server.block.RandomSource r) {
        if (data == null) return s;
        org.domi.furnace.server.loot.EnchantmentProvider p = data.enchantmentProvider(providerId);
        if (p == null) return s;
        var lootStack = new org.domi.furnace.server.loot.LootStack(ItemTools.itemName(s), s.count());
        var random = new org.domi.furnace.server.loot.LegacyNoiseRandom(r);
        return update(s, levels -> p.enchant(lootStack, levels, random, special, data.registries()));
    }

    /** 바닐라 updateEnchantments(stack, consumer): 수정한 결과를 컴포넌트로 다시 쓴 새 스택(빈 결과도 명시적으로 쓴다). */
    public static ItemStack update(ItemStack s, Consumer<Map<String, Integer>> edit) {
        String comp = componentFor(s);
        Map<String, Integer> levels = levels(s, comp);
        edit.accept(levels);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("levels", new LinkedHashMap<String, Object>(levels));
        if (ItemTools.component(s, comp) instanceof Map<?, ?> old && old.get("show_in_tooltip") instanceof Boolean b && !b) value.put("show_in_tooltip", false);
        return ItemTools.with(s, comp, value);
    }
}
