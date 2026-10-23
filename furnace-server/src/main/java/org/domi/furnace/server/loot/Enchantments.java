// 바닐라 EnchantmentHelper 중 전리품이 쓰는 부분 — 레벨 읽기·인챈트 붙이기·인챈트 테이블식 선택(enchantItem).
package org.domi.furnace.server.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public final class Enchantments {
    private Enchantments() {}

    public static final String ENCHANTMENTS = "minecraft:enchantments";
    public static final String STORED = "minecraft:stored_enchantments";

    /** 컴포넌트의 인챈트 → 레벨(없으면 빈 맵). */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> levels(LootStack s, String component) {
        if (s.get(component) instanceof Map<?, ?> m && m.get("levels") instanceof Map<?, ?> l) {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (var e : ((Map<String, Object>) l).entrySet()) out.put(e.getKey(), ((Number) e.getValue()).intValue());
            return out;
        }
        return Map.of();
    }

    /** 바닐라 getItemEnchantmentLevel — enchantments 컴포넌트만 본다. */
    static int itemLevel(String enchantment, LootStack s) { return s == null ? 0 : levels(s, ENCHANTMENTS).getOrDefault(enchantment, 0); }

    /** 바닐라 getEnchantmentLevel(entity) — 인챈트의 칸에 든 장비 중 최대 레벨. */
    static int entityLevel(Enchantment e, LootEntity entity) {
        int lvl = 0;
        for (String slot : new String[]{"mainhand", "offhand", "feet", "legs", "chest", "head", "body"}) {
            if (!e.matchingSlot(slot)) continue;
            LootStack s = entity.equipment(slot);
            if (s != null && !s.isEmpty()) lvl = Math.max(lvl, itemLevel(e.id(), s));
        }
        return lvl;
    }

    private static String componentFor(LootStack s) { return s.is("minecraft:enchanted_book") ? STORED : ENCHANTMENTS; }

    /** 바닐라 ItemStack.enchant → updateEnchantments(upgrade: 더 높은 레벨만). */
    static void enchant(LootStack s, String enchantment, int level) {
        String comp = componentFor(s);
        Map<String, Integer> levels = new LinkedHashMap<>(levels(s, comp));
        if (level > 0) levels.merge(enchantment, Math.min(level, 255), Integer::max);
        setLevels(s, comp, levels);
    }

    static void setLevels(LootStack s, String comp, Map<String, Integer> levels) {
        Map<String, Object> value = new LinkedHashMap<>();
        Map<String, Object> l = new LinkedHashMap<>(levels);
        value.put("levels", l);
        if (s.get(comp) instanceof Map<?, ?> old && old.get("show_in_tooltip") instanceof Boolean b && !b) value.put("show_in_tooltip", false);
        s.set(comp, value);
    }

    record Instance(Enchantment enchantment, int level) {}

    /** 바닐라 enchantItem — 책이면 고른 뒤 마법 부여된 책으로 바꾼다. */
    static LootStack enchantItem(NoiseRandom r, LootStack stack, int levels, List<Enchantment> candidates, LootRegistries regs) {
        List<Instance> picked = select(r, stack, levels, candidates, regs);
        if (stack.is("minecraft:book")) stack = new LootStack("minecraft:enchanted_book", 1);
        for (Instance i : picked) enchant(stack, i.enchantment.id(), i.level);
        return stack;
    }

    /** 바닐라 selectEnchantment — 난수 호출 순서까지 같다. */
    static List<Instance> select(NoiseRandom r, LootStack stack, int cost, List<Enchantment> candidates, LootRegistries regs) {
        List<Instance> result = new ArrayList<>();
        int value = ItemData.vanilla().enchantmentValue(stack.item());
        if (value <= 0) return result;
        cost += 1 + r.nextInt(value / 4 + 1) + r.nextInt(value / 4 + 1);
        float f = (r.nextFloat() + r.nextFloat() - 1.0f) * 0.15f;
        cost = Mth.clamp(Math.round((float) cost + (float) cost * f), 1, Integer.MAX_VALUE);
        List<Instance> available = available(cost, stack, candidates, regs);
        if (!available.isEmpty()) {
            Instance first = weighted(r, available);
            if (first != null) result.add(first);
            while (r.nextInt(50) <= cost) {
                if (!result.isEmpty()) {
                    Instance last = result.get(result.size() - 1);
                    available.removeIf(i -> !Enchantment.areCompatible(last.enchantment, i.enchantment, regs));
                }
                if (available.isEmpty()) break;
                Instance next = weighted(r, available);
                if (next != null) result.add(next);
                cost /= 2;
            }
        }
        return result;
    }

    private static List<Instance> available(int cost, LootStack stack, List<Enchantment> candidates, LootRegistries regs) {
        List<Instance> list = new ArrayList<>();
        boolean book = stack.is("minecraft:book");
        for (Enchantment e : candidates) {
            if (!book && !e.isPrimaryItem(stack, regs)) continue;
            for (int lvl = e.maxLevel(); lvl >= 1; lvl--) {
                if (cost >= e.minCost(lvl) && cost <= e.maxCost(lvl)) {
                    list.add(new Instance(e, lvl));
                    break;
                }
            }
        }
        return list;
    }

    /** 바닐라 WeightedRandom.getRandomItem. */
    private static Instance weighted(NoiseRandom r, List<Instance> items) {
        long total = 0;
        for (Instance i : items) total += i.enchantment.weight();
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        if (total == 0) return null;
        int n = r.nextInt((int) total);
        for (Instance i : items) {
            n -= i.enchantment.weight();
            if (n < 0) return i;
        }
        return null;
    }

    static List<Enchantment> resolve(HolderSet set, LootRegistries regs) {
        if (set == null) return new ArrayList<>(regs.enchantments().values());
        List<Enchantment> out = new ArrayList<>();
        for (String id : set.resolve(regs)) {
            Enchantment e = regs.enchantment(id);
            if (e != null) out.add(e);
        }
        return Collections.unmodifiableList(out);
    }
}
