// 아이템의 도구 판정(바닐라 Tool 컴포넌트·ItemStack.getDestroySpeed/isCorrectToolForDrops)과 인챈트 레벨 읽기(EnchantmentHelper 일부).
// 컴포넌트 값은 기본값(아이템 보고서) 위에 스택 패치를 얹은 것. 파싱한 Tool 은 컴포넌트 값마다 캐시한다.
package org.domi.furnace.server.item;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.loot.ItemData;

public final class ItemTools {
    private ItemTools() {}

    public static final String TOOL = "minecraft:tool", ENCHANTMENTS = "minecraft:enchantments";

    /** 아이템 이름(minecraft:…). 빈 스택이면 minecraft:air. */
    public static String itemName(ItemStack s) {
        if (s == null || s.isEmpty()) return "minecraft:air";
        String n = ItemContext.name("minecraft:item", s.itemId());
        return n == null ? "minecraft:air" : n;
    }

    /** 기본값 + 패치를 본 컴포넌트 값(없거나 지워졌으면 null). */
    public static Object component(ItemStack s, String name) {
        if (s == null || s.isEmpty()) return null;
        Map<String, Object> patch = s.patch().entries();
        if (patch.containsKey(name)) return patch.get(name);
        return ItemData.vanilla().defaultComponent(itemName(s), name);
    }

    /** 컴포넌트 하나를 (정규화해서) 설정한 새 스택 — 값이 아이템 기본값과 같으면 패치에서 빠진다. value null = 지움. */
    public static ItemStack with(ItemStack s, String component, Object rawValue) {
        java.util.LinkedHashMap<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put(component, rawValue);
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(s.patch().entries());
        merged.putAll(ComponentPatch.of(raw).entries());
        return new ItemStack(s.itemId(), s.count(), ComponentPatch.ofNormalized(merged).normalizedFor(itemName(s)));
    }

    // ------------------------------------------------------------------ Tool

    /** 바닐라 Tool(rules, default_mining_speed, damage_per_block). */
    public record Tool(Rule[] rules, float defaultMiningSpeed, int damagePerBlock) {

        /** Tool.getMiningSpeed(state): 속도가 있는 첫 규칙, 없으면 기본 속도. */
        public float miningSpeed(int state) {
            for (Rule r : rules) if (r.speed != null && r.matches(state)) return r.speed;
            return defaultMiningSpeed;
        }

        /** Tool.isCorrectForDrops(state): correct_for_drops 가 있는 첫 규칙. */
        public boolean isCorrectForDrops(int state) {
            for (Rule r : rules) if (r.correctForDrops != null && r.matches(state)) return r.correctForDrops;
            return false;
        }
    }

    /** Tool.Rule — 블록 목록(태그 하나 또는 블록 id 들)·속도·드롭 정답 여부. */
    public static final class Rule {
        final String tag;
        final boolean[] blocks;
        final Float speed;
        final Boolean correctForDrops;

        Rule(String tag, boolean[] blocks, Float speed, Boolean correctForDrops) {
            this.tag = tag;
            this.blocks = blocks;
            this.speed = speed;
            this.correctForDrops = correctForDrops;
        }

        boolean matches(int state) { return tag != null ? B.isTag(state, tag) : blocks[B.block(state)]; }
    }

    private static final Map<Object, Tool> TOOL_CACHE = new ConcurrentHashMap<>();
    private static final Tool NONE = new Tool(new Rule[0], 1.0F, 0);

    /** 그 스택의 Tool(없으면 null). */
    public static Tool tool(ItemStack s) {
        Object v = component(s, TOOL);
        if (!(v instanceof Map<?, ?> m)) return null;
        Tool t = TOOL_CACHE.computeIfAbsent(m, ItemTools::parseTool);
        return t == NONE ? null : t;
    }

    private static Tool parseTool(Object o) {
        Map<?, ?> m = (Map<?, ?>) o;
        List<?> rules = m.get("rules") instanceof List<?> l ? l : List.of();
        Rule[] out = new Rule[rules.size()];
        for (int i = 0; i < out.length; i++) {
            Map<?, ?> r = (Map<?, ?>) rules.get(i);
            Object blocks = r.get("blocks");
            String tag = null;
            boolean[] set = null;
            if (blocks instanceof String s && s.startsWith("#")) {
                tag = s.substring(1);
            } else {
                set = new boolean[B.BLOCKS.blockCount()];
                List<?> names = blocks instanceof List<?> bl ? bl : List.of(blocks);
                for (Object n : names) {
                    int b = B.id(String.valueOf(n));
                    if (b >= 0) set[b] = true;
                }
            }
            Float speed = r.get("speed") instanceof Number n ? n.floatValue() : null;
            Boolean correct = r.get("correct_for_drops") instanceof Boolean c ? c : null;
            out[i] = new Rule(tag, set, speed, correct);
        }
        float def = m.get("default_mining_speed") instanceof Number n ? n.floatValue() : 1.0F;
        int dmg = m.get("damage_per_block") instanceof Number n ? n.intValue() : 1;
        return new Tool(out, def, dmg);
    }

    /** ItemStack.getDestroySpeed(state): 도구가 있으면 규칙 속도, 없으면 1. */
    public static float destroySpeed(ItemStack s, int state) {
        Tool t = tool(s);
        return t == null ? 1.0F : t.miningSpeed(state);
    }

    /** ItemStack.isCorrectToolForDrops(state). */
    public static boolean isCorrectToolForDrops(ItemStack s, int state) {
        Tool t = tool(s);
        return t != null && t.isCorrectForDrops(state);
    }

    // ------------------------------------------------------------------ 인챈트

    /** EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) — enchantments 컴포넌트만. */
    public static int enchantmentLevel(ItemStack s, String enchantment) {
        if (component(s, ENCHANTMENTS) instanceof Map<?, ?> m && m.get("levels") instanceof Map<?, ?> l
                && l.get(enchantment) instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    /** EnchantmentHelper.hasTag(stack, #태그): 붙은 인챈트 중 그 인챈트 태그에 든 것이 있는가. */
    public static boolean hasEnchantmentTag(ItemStack s, String tag) {
        if (!(component(s, ENCHANTMENTS) instanceof Map<?, ?> m) || !(m.get("levels") instanceof Map<?, ?> l)) return false;
        for (var e : l.entrySet()) {
            if (!(e.getValue() instanceof Number n) || n.intValue() <= 0) continue;
            Integer id = ItemContext.id("minecraft:enchantment", String.valueOf(e.getKey()));
            if (id != null && ItemContext.isInTag("minecraft:enchantment", tag, id)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 내구도

    /** isDamageableItem: max_damage·damage 가 있고 unbreakable 이 없다. */
    public static boolean isDamageable(ItemStack s) {
        return component(s, "minecraft:max_damage") != null && component(s, "minecraft:damage") != null
                && component(s, "minecraft:unbreakable") == null;
    }

    public static int maxDamage(ItemStack s) { return component(s, "minecraft:max_damage") instanceof Number n ? n.intValue() : 0; }

    public static int damage(ItemStack s) {
        return component(s, "minecraft:damage") instanceof Number n ? Math.clamp(n.intValue(), 0, maxDamage(s)) : 0;
    }

    /** setDamage: 기본값(0)과 같으면 패치에서 뺀다. */
    public static ItemStack withDamage(ItemStack s, int damage) {
        int d = Math.clamp(damage, 0, maxDamage(s));
        java.util.LinkedHashMap<String, Object> patch = new java.util.LinkedHashMap<>(s.patch().entries());
        Object def = ItemData.vanilla().defaultComponent(itemName(s), "minecraft:damage");
        if (def instanceof Number n && n.intValue() == d) patch.remove("minecraft:damage");
        else patch.put("minecraft:damage", d);
        return new ItemStack(s.itemId(), s.count(), ComponentPatch.ofNormalized(patch));
    }

    /** attribute_modifiers 의 add_value 합 — type 은 "minecraft:generic.armor" 같은 전체 이름, slots 는 해당 칸 이름들(any 는 항상 포함). */
    public static float attributeBonus(ItemStack s, String type, String... slots) {
        if (s == null || s.isEmpty()) return 0.0F;
        if (!(component(s, "minecraft:attribute_modifiers") instanceof java.util.Map<?, ?> m) || !(m.get("modifiers") instanceof java.util.List<?> list)) return 0.0F;
        float sum = 0.0F;
        for (Object o : list) {
            if (!(o instanceof java.util.Map<?, ?> e)) continue;
            String t = String.valueOf(e.get("type"));
            String slot = e.get("slot") == null ? "any" : String.valueOf(e.get("slot"));
            String op = e.get("operation") == null ? "add_value" : String.valueOf(e.get("operation"));
            boolean slotOk = slot.equals("any");
            for (String w : slots) if (slot.equals(w)) slotOk = true;
            if (t.equals(type) && op.equals("add_value") && slotOk && e.get("amount") instanceof Number n) sum += n.floatValue();
        }
        return sum;
    }

    /** 손에 든 아이템의 attack_damage 속성(add_value, 주 손/손/any 칸)의 합. */
    public static float attackDamageBonus(ItemStack s) { return attributeBonus(s, "minecraft:generic.attack_damage", "mainhand", "hand"); }
}
