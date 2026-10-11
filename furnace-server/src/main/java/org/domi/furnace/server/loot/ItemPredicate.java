// 바닐라 ItemPredicate — items·count·components(정확히 같은 값)·predicates(인챈트·손상·물약 …).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ItemPredicate(HolderSet items, Bounds count, Map<String, Object> components, List<SubPredicate> predicates) {

    interface SubPredicate { boolean test(LootStack s, LootRegistries regs); }

    public static ItemPredicate parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        HolderSet items = o.has("items") ? HolderSet.parse("minecraft:item", o.get("items"), regs) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> comps = o.has("components") ? (Map<String, Object>) Components.fromJson(o.get("components")) : Map.of();
        List<SubPredicate> subs = new ArrayList<>();
        if (o.has("predicates")) {
            for (var p : o.getAsJsonObject("predicates").entrySet()) subs.add(sub(LootJson.id(p.getKey()), p.getValue(), regs));
        }
        return new ItemPredicate(items, Bounds.parse(o.get("count")), comps, List.copyOf(subs));
    }

    private static SubPredicate sub(String type, JsonElement v, LootRegistries regs) {
        return switch (type) {
            case "minecraft:enchantments" -> enchantments("minecraft:enchantments", v, regs);
            case "minecraft:stored_enchantments" -> enchantments("minecraft:stored_enchantments", v, regs);
            case "minecraft:damage" -> {
                JsonObject o = v.getAsJsonObject();
                Bounds durability = Bounds.parse(o.get("durability")), damage = Bounds.parse(o.get("damage"));
                yield (s, r) -> s.isDamageable() && durability.matches(s.maxDamage() - s.damage()) && damage.matches(s.damage());
            }
            case "minecraft:potion_contents" -> {
                HolderSet potions = HolderSet.parse("minecraft:potion", v, regs);
                yield (s, r) -> s.get("minecraft:potion_contents") instanceof Map<?, ?> m && m.get("potion") instanceof String p
                        && potions.contains(p, r);
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ItemPredicate.class).warn("아직 지원하지 않는 아이템 조건 {} — 항상 거짓", type);
                yield (s, r) -> false;
            }
        };
    }

    private record EnchantmentPredicate(HolderSet enchantments, Bounds levels) {}

    private static SubPredicate enchantments(String component, JsonElement v, LootRegistries regs) {
        List<EnchantmentPredicate> list = new ArrayList<>();
        for (JsonElement x : v.getAsJsonArray()) {
            JsonObject o = x.getAsJsonObject();
            list.add(new EnchantmentPredicate(o.has("enchantments") ? HolderSet.parse("minecraft:enchantment", o.get("enchantments"), regs) : null,
                    Bounds.parse(o.get("levels"))));
        }
        return (s, r) -> {
            Map<String, Integer> levels = Enchantments.levels(s, component);
            for (EnchantmentPredicate p : list) {
                boolean ok;
                if (p.enchantments != null) {
                    ok = false;
                    for (String id : p.enchantments.resolve(r)) {
                        int lvl = levels.getOrDefault(id, 0);
                        if (lvl != 0 && (p.levels.isAny() || p.levels.matches(lvl))) { ok = true; break; }
                    }
                } else if (!p.levels.isAny()) {
                    ok = false;
                    for (int lvl : levels.values()) if (p.levels.matches(lvl)) { ok = true; break; }
                } else {
                    ok = !levels.isEmpty();
                }
                if (!ok) return false;
            }
            return true;
        };
    }

    public boolean test(LootStack s, LootRegistries regs) {
        if (items != null && !items.contains(s.item(), regs)) return false;
        if (!count.matches(s.count())) return false;
        for (var c : components.entrySet()) if (!Components.same(s.get(LootJson.id(c.getKey())), c.getValue())) return false;
        for (SubPredicate p : predicates) if (!p.test(s, regs)) return false;
        return true;
    }
}
