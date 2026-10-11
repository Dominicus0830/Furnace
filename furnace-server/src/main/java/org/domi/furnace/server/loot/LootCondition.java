// 바닐라 LootItemCondition — JSON 목록은 all_of. 평가 순서·난수 호출이 바닐라와 같아야 한다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Predicate;

public interface LootCondition extends Predicate<LootContext> {

    static boolean allOf(List<LootCondition> conditions, LootContext ctx) {
        for (LootCondition c : conditions) if (!c.test(ctx)) return false;
        return true;
    }

    static List<LootCondition> list(JsonObject o, String key, LootRegistries regs) {
        return LootJson.list(o, key, e -> parse(e, regs));
    }

    static LootCondition parse(JsonElement e, LootRegistries regs) {
        if (e.isJsonArray()) {
            List<LootCondition> terms = LootJson.list(wrap(e), "terms", x -> parse(x, regs));
            return ctx -> allOf(terms, ctx);
        }
        JsonObject o = e.getAsJsonObject();
        String type = LootJson.id(o, "condition");
        return switch (type) {
            case "minecraft:inverted" -> { LootCondition t = parse(LootJson.req(o, "term"), regs); yield ctx -> !t.test(ctx); }
            case "minecraft:any_of" -> {
                List<LootCondition> terms = list(o, "terms", regs);
                yield ctx -> { for (LootCondition c : terms) if (c.test(ctx)) return true; return false; };
            }
            case "minecraft:all_of" -> { List<LootCondition> terms = list(o, "terms", regs); yield ctx -> allOf(terms, ctx); }
            case "minecraft:random_chance" -> {
                NumberProvider chance = NumberProvider.parse(LootJson.req(o, "chance"));
                yield ctx -> { float c = chance.getFloat(ctx); return ctx.random().nextFloat() < c; };
            }
            case "minecraft:random_chance_with_enchanted_bonus" -> {
                float unenchanted = LootJson.req(o, "unenchanted_chance").getAsFloat();
                LevelBasedValue enchanted = LevelBasedValue.parse(LootJson.req(o, "enchanted_chance"));
                String ench = enchantment(o, regs);
                yield ctx -> {
                    LootEntity attacker = ctx.param(LootParams.ATTACKING_ENTITY);
                    Enchantment en = ctx.registries().enchantment(ench);
                    int level = attacker != null && en != null ? Enchantments.entityLevel(en, attacker) : 0;
                    float chance = level > 0 ? enchanted.calculate(level) : unenchanted;
                    return ctx.random().nextFloat() < chance;
                };
            }
            case "minecraft:entity_properties" -> {
                String target = LootJson.req(o, "entity").getAsString();
                EntityPredicate p = o.has("predicate") ? EntityPredicate.parse(o.get("predicate"), regs) : null;
                yield ctx -> {
                    LootEntity ent = ctx.param(entityTarget(target));
                    if (p == null) return true;
                    return p.matches(ctx, ctx.param(LootParams.ORIGIN), ent);
                };
            }
            case "minecraft:killed_by_player" -> ctx -> ctx.hasParam(LootParams.LAST_DAMAGE_PLAYER);
            case "minecraft:entity_scores" -> {
                String target = LootJson.req(o, "entity").getAsString();
                List<java.util.Map.Entry<String, IntRange>> scores = new java.util.ArrayList<>();
                for (var s : LootJson.req(o, "scores").getAsJsonObject().entrySet()) scores.add(java.util.Map.entry(s.getKey(), IntRange.parse(s.getValue())));
                yield ctx -> {
                    if (ctx.param(entityTarget(target)) == null) return false;
                    for (var s : scores) {
                        Integer v = ctx.world().score(ctx, target, s.getKey());
                        if (v == null || !s.getValue().matches(ctx, v)) return false;
                    }
                    return true;
                };
            }
            case "minecraft:block_state_property" -> {
                String block = LootJson.id(o, "block");
                if (!regs.exists("minecraft:block", block)) throw new LootParseException("Unknown block " + block);
                java.util.Map<String, JsonElement> props = new java.util.LinkedHashMap<>();
                if (o.has("properties")) for (var p : o.getAsJsonObject("properties").entrySet()) props.put(p.getKey(), p.getValue());
                yield ctx -> {
                    LootBlockState s = ctx.param(LootParams.BLOCK_STATE);
                    return s != null && s.block().equals(block) && (props.isEmpty() || StateProperties.matches(props, s));
                };
            }
            case "minecraft:match_tool" -> {
                ItemPredicate p = o.has("predicate") ? ItemPredicate.parse(o.get("predicate"), regs) : null;
                yield ctx -> {
                    LootStack tool = ctx.param(LootParams.TOOL);
                    return tool != null && (p == null || p.test(tool, ctx.registries()));
                };
            }
            case "minecraft:table_bonus" -> {
                String ench = enchantment(o, regs);
                List<Float> chances = LootJson.list(o, "chances", JsonElement::getAsFloat);
                if (chances.isEmpty()) throw new LootParseException("Value must be non-empty: []");
                yield ctx -> {
                    LootStack tool = ctx.param(LootParams.TOOL);
                    int level = tool != null ? Enchantments.itemLevel(ench, tool) : 0;
                    float chance = chances.get(Math.min(level, chances.size() - 1));
                    return ctx.random().nextFloat() < chance;
                };
            }
            case "minecraft:survives_explosion" -> ctx -> {
                Float r = ctx.param(LootParams.EXPLOSION_RADIUS);
                if (r == null) return true;
                float f = 1.0f / r;
                return ctx.random().nextFloat() <= f;
            };
            case "minecraft:damage_source_properties" -> {
                DamageSourcePredicate p = o.has("predicate") ? DamageSourcePredicate.parse(o.get("predicate"), regs) : null;
                yield ctx -> {
                    LootDamageSource src = ctx.param(LootParams.DAMAGE_SOURCE);
                    double[] origin = ctx.param(LootParams.ORIGIN);
                    return origin != null && src != null && (p == null || p.matches(ctx, origin, src));
                };
            }
            case "minecraft:location_check" -> {
                LocationPredicate p = o.has("predicate") ? LocationPredicate.parse(o.get("predicate"), regs) : null;
                int dx = LootJson.integer(o, "offsetX", 0), dy = LootJson.integer(o, "offsetY", 0), dz = LootJson.integer(o, "offsetZ", 0);
                yield ctx -> {
                    double[] origin = ctx.param(LootParams.ORIGIN);
                    return origin != null && (p == null || p.matches(ctx, new double[]{origin[0] + dx, origin[1] + dy, origin[2] + dz}));
                };
            }
            case "minecraft:weather_check" -> {
                Boolean raining = o.has("raining") ? o.get("raining").getAsBoolean() : null;
                Boolean thundering = o.has("thundering") ? o.get("thundering").getAsBoolean() : null;
                yield ctx -> (raining == null || raining == ctx.world().isRaining())
                        && (thundering == null || thundering == ctx.world().isThundering());
            }
            case "minecraft:time_check" -> {
                Long period = o.has("period") ? o.get("period").getAsLong() : null;
                IntRange value = IntRange.parse(LootJson.req(o, "value"));
                yield ctx -> {
                    long t = ctx.world().dayTime();
                    if (period != null) t %= period;
                    return value.matches(ctx, (int) t);
                };
            }
            case "minecraft:value_check" -> {
                NumberProvider value = NumberProvider.parse(LootJson.req(o, "value"));
                IntRange range = IntRange.parse(LootJson.req(o, "range"));
                yield ctx -> range.matches(ctx, value.getInt(ctx));
            }
            case "minecraft:reference" -> {
                String name = LootJson.id(o, "name");
                yield ctx -> ctx.data().testPredicate(name, ctx);
            }
            case "minecraft:enchantment_active_check" -> {
                boolean active = LootJson.req(o, "active").getAsBoolean();
                yield ctx -> {
                    Boolean a = ctx.param(LootParams.ENCHANTMENT_ACTIVE);
                    if (a == null) throw new java.util.NoSuchElementException("enchantment_active");
                    return a == active;
                };
            }
            default -> throw new LootParseException("모르는 조건: " + type);
        };
    }

    private static JsonObject wrap(JsonElement array) {
        JsonObject o = new JsonObject();
        o.add("terms", array);
        return o;
    }

    static String enchantment(JsonObject o, LootRegistries regs) {
        String id = LootJson.id(o, "enchantment");
        if (!regs.exists("minecraft:enchantment", id)) throw new LootParseException("Unknown registry key in minecraft:enchantment: " + id);
        return id;
    }

    /** 바닐라 LootContext.EntityTarget. */
    static LootParams.Key<LootEntity> entityTarget(String name) {
        return switch (name) {
            case "this" -> LootParams.THIS_ENTITY;
            case "attacker" -> LootParams.ATTACKING_ENTITY;
            case "direct_attacker" -> LootParams.DIRECT_ATTACKING_ENTITY;
            case "attacking_player" -> LootParams.LAST_DAMAGE_PLAYER;
            default -> throw new LootParseException("모르는 엔티티 대상: " + name);
        };
    }
}
