// 바닐라 LootItemFunction — JSON 목록은 sequence. sequence 외에는 조건(conditions)을 먼저 보고, 맞으면 적용한다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface LootFunction extends BiFunction<LootStack, LootContext, LootStack> {

    Logger LOG = LoggerFactory.getLogger(LootFunction.class);

    /** 바닐라 LootItemFunctions.compose — 목록 순서대로. */
    static LootFunction compose(List<LootFunction> fns) {
        if (fns.isEmpty()) return (s, c) -> s;
        if (fns.size() == 1) return fns.get(0);
        return (s, c) -> {
            for (LootFunction f : fns) s = f.apply(s, c);
            return s;
        };
    }

    static List<LootFunction> list(JsonObject o, String key, LootRegistries regs) {
        return LootJson.list(o, key, e -> parse(e, regs));
    }

    /** 즉시 효과(바닐라 InstantenousMobEffect) — 수상한 스튜 지속 시간을 20배 하지 않는다. */
    Set<String> INSTANT_EFFECTS = Set.of("minecraft:instant_health", "minecraft:instant_damage", "minecraft:saturation");

    static LootFunction parse(JsonElement e, LootRegistries regs) {
        if (e.isJsonArray()) {
            List<LootFunction> fns = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) fns.add(parse(x, regs));
            return compose(fns);
        }
        JsonObject o = e.getAsJsonObject();
        String type = LootJson.id(o, "function");
        if (type.equals("minecraft:sequence")) return compose(list(o, "functions", regs));
        List<LootCondition> conditions = LootCondition.list(o, "conditions", regs);
        LootFunction run = body(type, o, regs);
        if (conditions.isEmpty()) return run;
        return (s, c) -> LootCondition.allOf(conditions, c) ? run.apply(s, c) : s;
    }

    @SuppressWarnings("unchecked")
    private static LootFunction body(String type, JsonObject o, LootRegistries regs) {
        return switch (type) {
            case "minecraft:set_count" -> {
                NumberProvider count = NumberProvider.parse(LootJson.req(o, "count"));
                boolean add = LootJson.bool(o, "add", false);
                yield (s, c) -> { s.setCount((add ? s.count() : 0) + count.getInt(c)); return s; };
            }
            case "minecraft:explosion_decay" -> (s, c) -> {
                Float radius = c.param(LootParams.EXPLOSION_RADIUS);
                if (radius != null) {
                    float f = 1.0f / radius;
                    int n = s.count(), kept = 0;
                    for (int i = 0; i < n; i++) if (c.random().nextFloat() <= f) kept++;
                    s.setCount(kept);
                }
                return s;
            };
            case "minecraft:enchanted_count_increase" -> {
                String ench = LootCondition.enchantment(o, regs);
                NumberProvider count = NumberProvider.parse(LootJson.req(o, "count"));
                int limit = LootJson.integer(o, "limit", 0);
                yield (s, c) -> {
                    LootEntity attacker = c.param(LootParams.ATTACKING_ENTITY);
                    Enchantment en = c.registries().enchantment(ench);
                    if (attacker != null && en != null) {
                        int level = Enchantments.entityLevel(en, attacker);
                        if (level == 0) return s;
                        float f = (float) level * count.getFloat(c);
                        s.grow(Math.round(f));
                        if (limit > 0) s.limitSize(limit);
                    }
                    return s;
                };
            }
            case "minecraft:limit_count" -> {
                IntRange limit = IntRange.parse(LootJson.req(o, "limit"));
                yield (s, c) -> { s.setCount(limit.clamp(c, s.count())); return s; };
            }
            case "minecraft:apply_bonus" -> applyBonus(o, regs);
            case "minecraft:enchant_randomly" -> {
                HolderSet options = o.has("options") ? HolderSet.parse("minecraft:enchantment", o.get("options"), regs) : null;
                boolean onlyCompatible = LootJson.bool(o, "only_compatible", true);
                yield (s, c) -> {
                    boolean book = s.is("minecraft:book");
                    boolean filter = !book && onlyCompatible;
                    List<Enchantment> list = new ArrayList<>();
                    for (Enchantment en : Enchantments.resolve(options, c.registries())) {
                        if (!filter || en.isSupportedItem(s, c.registries())) list.add(en);
                    }
                    if (list.isEmpty()) {
                        LOG.warn("Couldn't find a compatible enchantment for {}", s);
                        return s;
                    }
                    Enchantment pick = list.get(c.random().nextInt(list.size()));
                    int level = Mth.nextInt(c.random(), 1, pick.maxLevel());
                    if (book) s = new LootStack("minecraft:enchanted_book", 1);
                    Enchantments.enchant(s, pick.id(), level);
                    return s;
                };
            }
            case "minecraft:enchant_with_levels" -> {
                NumberProvider levels = NumberProvider.parse(LootJson.req(o, "levels"));
                HolderSet options = o.has("options") ? HolderSet.parse("minecraft:enchantment", o.get("options"), regs) : null;
                yield (s, c) -> {
                    int lv = levels.getInt(c);
                    return Enchantments.enchantItem(c.random(), s, lv, Enchantments.resolve(options, c.registries()), c.registries());
                };
            }
            case "minecraft:set_damage" -> {
                NumberProvider damage = NumberProvider.parse(LootJson.req(o, "damage"));
                boolean add = LootJson.bool(o, "add", false);
                yield (s, c) -> {
                    if (s.isDamageable()) {
                        int max = s.maxDamage();
                        float existing = add ? 1.0f - (float) s.damage() / (float) max : 0.0f;
                        float remaining = 1.0f - Mth.clamp(damage.getFloat(c) + existing, 0.0f, 1.0f);
                        s.setDamage(Mth.floor(remaining * (float) max));
                    } else {
                        LOG.warn("Couldn't set damage of loot item {}", s);
                    }
                    return s;
                };
            }
            case "minecraft:furnace_smelt" -> (s, c) -> {
                if (s.isEmpty()) return s;
                String out = c.world().smeltingResult(s.item());
                if (out != null) return new LootStack(out, s.count());
                LOG.warn("Couldn't smelt {} because there is no smelting recipe", s);
                return s;
            };
            case "minecraft:set_enchantments" -> {
                Map<String, NumberProvider> map = new LinkedHashMap<>();
                if (o.has("enchantments")) {
                    for (var x : o.getAsJsonObject("enchantments").entrySet()) {
                        String id = LootJson.id(x.getKey());
                        if (!regs.exists("minecraft:enchantment", id)) throw new LootParseException("Unknown registry key in minecraft:enchantment: " + id);
                        map.put(id, NumberProvider.parse(x.getValue()));
                    }
                }
                boolean add = LootJson.bool(o, "add", false);
                yield (s, c) -> {
                    if (s.is("minecraft:book")) {
                        Object ench = s.get(Enchantments.ENCHANTMENTS);
                        s = s.transmute("minecraft:enchanted_book");
                        s.remove(Enchantments.ENCHANTMENTS);
                        s.set(Enchantments.STORED, ench);
                    }
                    String comp = s.is("minecraft:enchanted_book") ? Enchantments.STORED : Enchantments.ENCHANTMENTS;
                    Map<String, Integer> levels = new LinkedHashMap<>(Enchantments.levels(s, comp));
                    for (var x : map.entrySet()) {
                        int lvl = add ? Mth.clamp(levels.getOrDefault(x.getKey(), 0) + x.getValue().getInt(c), 0, 255)
                                : Mth.clamp(x.getValue().getInt(c), 0, 255);
                        if (lvl <= 0) levels.remove(x.getKey());
                        else levels.put(x.getKey(), lvl);
                    }
                    Enchantments.setLevels(s, comp, levels);
                    return s;
                };
            }
            case "minecraft:set_components" -> {
                JsonObject comps = LootJson.req(o, "components").getAsJsonObject();
                yield (s, c) -> {
                    for (var x : comps.entrySet()) {
                        String key = x.getKey();
                        if (key.startsWith("!")) s.remove(LootJson.id(key.substring(1)));
                        else s.set(LootJson.id(key), Components.fromJson(x.getValue()));
                    }
                    return s;
                };
            }
            case "minecraft:set_potion" -> {
                String potion = LootJson.id(o, "id");
                yield (s, c) -> {
                    Map<String, Object> pc = s.get("minecraft:potion_contents") instanceof Map<?, ?> m
                            ? LootStack.deepCopy((Map<String, Object>) m) : new LinkedHashMap<>();
                    pc.put("potion", potion);
                    s.set("minecraft:potion_contents", pc);
                    return s;
                };
            }
            case "minecraft:set_name" -> {
                String target = o.has("target") ? o.get("target").getAsString() : "custom_name";
                String comp = target.equals("item_name") ? "minecraft:item_name" : "minecraft:custom_name";
                String name = o.has("name") ? o.get("name").toString() : null;
                yield (s, c) -> { if (name != null) s.set(comp, name); return s; };
            }
            case "minecraft:set_stew_effect" -> {
                List<Object[]> effects = new ArrayList<>();
                for (JsonElement x : o.has("effects") ? o.getAsJsonArray("effects") : new com.google.gson.JsonArray()) {
                    JsonObject ef = x.getAsJsonObject();
                    effects.add(new Object[]{LootJson.id(ef, "type"), NumberProvider.parse(LootJson.req(ef, "duration"))});
                }
                yield (s, c) -> {
                    if (s.is("minecraft:suspicious_stew") && !effects.isEmpty()) {
                        Object[] ef = effects.get(c.random().nextInt(effects.size()));
                        int duration = ((NumberProvider) ef[1]).getInt(c);
                        if (!INSTANT_EFFECTS.contains((String) ef[0])) duration *= 20;
                        List<Object> list = s.get("minecraft:suspicious_stew_effects") instanceof List<?> l
                                ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id", ef[0]);
                        if (duration != 160) entry.put("duration", duration);   // 바닐라 코덱 기본값 160 은 생략된다
                        list.add(entry);
                        s.set("minecraft:suspicious_stew_effects", list);
                    }
                    return s;
                };
            }
            case "minecraft:set_ominous_bottle_amplifier" -> {
                NumberProvider amp = NumberProvider.parse(LootJson.req(o, "amplifier"));
                yield (s, c) -> { s.set("minecraft:ominous_bottle_amplifier", Mth.clamp(amp.getInt(c), 0, 4)); return s; };
            }
            case "minecraft:set_instrument" -> {
                String tag = LootJson.req(o, "options").getAsString();
                if (!tag.startsWith("#")) throw new LootParseException("Not a tag id: " + tag);
                String t = LootJson.id(tag.substring(1));
                yield (s, c) -> {
                    List<String> list = c.registries().tag("minecraft:instrument", t);
                    if (!list.isEmpty()) s.set("minecraft:instrument", list.get(c.random().nextInt(list.size())));
                    return s;
                };
            }
            case "minecraft:copy_state" -> {
                String block = LootJson.id(o, "block");
                List<String> props = LootJson.list(o, "properties", JsonElement::getAsString);
                yield (s, c) -> {
                    LootBlockState st = c.param(LootParams.BLOCK_STATE);
                    if (st != null) {
                        Map<String, Object> bs = s.get("minecraft:block_state") instanceof Map<?, ?> m
                                ? LootStack.deepCopy((Map<String, Object>) m) : new LinkedHashMap<>();
                        for (String p : props) {
                            String v = st.properties().get(p);
                            if (v != null) bs.put(p, v);
                        }
                        s.set("minecraft:block_state", bs);
                    }
                    return s;
                };
            }
            case "minecraft:copy_components" -> {
                String source = LootJson.req(o, "source").getAsString();
                if (!source.equals("block_entity")) throw new LootParseException("모르는 복사 원본: " + source);
                List<String> include = o.has("include") ? LootJson.list(o, "include", x -> LootJson.id(x.getAsString())) : null;
                List<String> exclude = o.has("exclude") ? LootJson.list(o, "exclude", x -> LootJson.id(x.getAsString())) : null;
                yield (s, c) -> {
                    Map<String, Object> be = c.param(LootParams.BLOCK_ENTITY);
                    if (be != null) {
                        for (var x : be.entrySet()) {
                            if (exclude != null && exclude.contains(x.getKey())) continue;
                            if (include != null && !include.contains(x.getKey())) continue;
                            s.set(x.getKey(), LootStack.deepCopy(x.getValue()));
                        }
                    }
                    return s;
                };
            }
            case "minecraft:copy_name" -> {
                String source = LootJson.req(o, "source").getAsString();
                yield (s, c) -> {
                    if (source.equals("block_entity") && c.param(LootParams.BLOCK_ENTITY) instanceof Map<?, ?> be
                            && be.get("minecraft:custom_name") != null) {
                        s.set("minecraft:custom_name", be.get("minecraft:custom_name"));
                    }
                    return s;
                };
            }
            case "minecraft:exploration_map" -> {
                String destination = o.has("destination") ? LootJson.id(o.get("destination").getAsString().replace("#", "")) : "minecraft:on_treasure_maps";
                String decoration = o.has("decoration") ? LootJson.id(o, "decoration") : "minecraft:mansion";
                int zoom = LootJson.integer(o, "zoom", 2), radius = LootJson.integer(o, "search_radius", 50);
                boolean skip = LootJson.bool(o, "skip_existing_chunks", true);
                yield (s, c) -> {
                    if (!s.is("minecraft:map")) return s;
                    double[] origin = c.param(LootParams.ORIGIN);
                    if (origin != null) {
                        int[] pos = c.world().findNearestMapStructure(destination, origin, radius, skip);
                        if (pos != null) {
                            LootStack map = c.world().createExplorationMap(pos[0], pos[1], zoom, decoration);
                            if (map != null) return map;
                        }
                    }
                    return s;
                };
            }
            case "minecraft:filtered" -> {
                ItemPredicate filter = ItemPredicate.parse(LootJson.req(o, "item_filter"), regs);
                LootFunction modifier = parse(LootJson.req(o, "modifier"), regs);
                yield (s, c) -> filter.test(s, c.registries()) ? modifier.apply(s, c) : s;
            }
            case "minecraft:reference" -> {
                String name = LootJson.id(o, "name");
                yield (s, c) -> c.data().applyModifier(name, s, c);
            }
            case "minecraft:set_item" -> {
                String item = LootJson.id(o, "item");
                if (!regs.exists("minecraft:item", item)) throw new LootParseException("Unknown item " + item);
                yield (s, c) -> s.transmute(item);
            }
            case "minecraft:toggle_tooltips" -> {
                Map<String, Boolean> toggles = new LinkedHashMap<>();
                for (var x : LootJson.req(o, "toggles").getAsJsonObject().entrySet()) toggles.put(LootJson.id(x.getKey()), x.getValue().getAsBoolean());
                yield (s, c) -> {
                    for (var t : toggles.entrySet()) {
                        if (s.get(t.getKey()) instanceof Map<?, ?> m) {
                            Map<String, Object> v = LootStack.deepCopy((Map<String, Object>) m);
                            if (t.getValue()) v.remove("show_in_tooltip"); else v.put("show_in_tooltip", false);
                            s.set(t.getKey(), v);
                        }
                    }
                    return s;
                };
            }
            case "minecraft:set_custom_model_data" -> {
                NumberProvider value = NumberProvider.parse(LootJson.req(o, "value"));
                yield (s, c) -> { s.set("minecraft:custom_model_data", value.getInt(c)); return s; };
            }
            default -> throw new LootParseException("아직 지원하지 않는 전리품 함수: " + type);
        };
    }

    private static LootFunction applyBonus(JsonObject o, LootRegistries regs) {
        String ench = LootCondition.enchantment(o, regs);
        String formula = LootJson.id(o, "formula");
        JsonObject p = o.has("parameters") ? o.getAsJsonObject("parameters") : new JsonObject();
        interface Formula { int calc(org.domi.furnace.server.world.gen.noise.NoiseRandom r, int count, int level); }
        Formula f = switch (formula) {
            case "minecraft:binomial_with_bonus_count" -> {
                int extra = LootJson.req(p, "extra").getAsInt();
                float probability = LootJson.req(p, "probability").getAsFloat();
                yield (r, count, level) -> {
                    for (int i = 0; i < level + extra; i++) if (r.nextFloat() < probability) count++;
                    return count;
                };
            }
            case "minecraft:uniform_bonus_count" -> {
                int mult = LootJson.req(p, "bonusMultiplier").getAsInt();
                yield (r, count, level) -> count + r.nextInt(mult * level + 1);
            }
            case "minecraft:ore_drops" -> (r, count, level) -> {
                if (level > 0) {
                    int bonus = r.nextInt(level + 2) - 1;
                    if (bonus < 0) bonus = 0;
                    return count * (bonus + 1);
                }
                return count;
            };
            default -> throw new LootParseException("모르는 보너스 공식: " + formula);
        };
        return (s, c) -> {
            LootStack tool = c.param(LootParams.TOOL);
            if (tool != null) {
                int level = Enchantments.itemLevel(ench, tool);
                s.setCount(f.calc(c.random(), s.count(), level));
            }
            return s;
        };
    }
}
