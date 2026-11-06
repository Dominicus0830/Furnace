// 바닐라 NumberProvider — 상수·균등·이항·점수·저장소·인챈트 레벨. getInt 기본은 Math.round(getFloat).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public interface NumberProvider {

    float getFloat(LootContext ctx);

    default int getInt(LootContext ctx) { return Math.round(getFloat(ctx)); }

    record Constant(float value) implements NumberProvider {
        public float getFloat(LootContext ctx) { return value; }
    }

    record Uniform(NumberProvider min, NumberProvider max) implements NumberProvider {
        public int getInt(LootContext ctx) {
            int lo = min.getInt(ctx), hi = max.getInt(ctx);
            return Mth.nextInt(ctx.random(), lo, hi);
        }

        public float getFloat(LootContext ctx) {
            float lo = min.getFloat(ctx), hi = max.getFloat(ctx);
            return Mth.nextFloat(ctx.random(), lo, hi);
        }
    }

    record Binomial(NumberProvider n, NumberProvider p) implements NumberProvider {
        public int getInt(LootContext ctx) {
            int count = n.getInt(ctx);
            float chance = p.getFloat(ctx);
            int successes = 0;
            for (int i = 0; i < count; i++) if (ctx.random().nextFloat() < chance) successes++;
            return successes;
        }

        public float getFloat(LootContext ctx) { return getInt(ctx); }
    }

    /** 점수판 값 — 점수판이 아직 없어 대상·목표가 없을 때의 바닐라 결과(0)를 낸다. */
    record Score(String target, String score, float scale) implements NumberProvider {
        public float getFloat(LootContext ctx) {
            Integer v = ctx.world().score(ctx, target, score);
            return v == null ? 0.0f : v * scale;
        }
    }

    /** 명령 저장소 값 — 경로를 못 찾으면 0. */
    record Storage(String storage, String path) implements NumberProvider {
        public float getFloat(LootContext ctx) {
            Number n = ctx.world().storageNumber(storage, path);
            return n == null ? 0.0f : n.floatValue();
        }

        public int getInt(LootContext ctx) {
            Number n = ctx.world().storageNumber(storage, path);
            return n == null ? 0 : n.intValue();
        }
    }

    record EnchantmentLevel(LevelBasedValue amount) implements NumberProvider {
        public float getFloat(LootContext ctx) {
            Integer level = ctx.param(LootParams.ENCHANTMENT_LEVEL);
            if (level == null) throw new IllegalStateException("enchantment_level 매개변수 없음");
            return amount.calculate(level);
        }
    }

    static NumberProvider parse(JsonElement e) {
        if (e.isJsonPrimitive()) return new Constant(e.getAsFloat());
        if (!e.isJsonObject()) throw new LootParseException("수 공급자가 아님: " + e);
        JsonObject o = e.getAsJsonObject();
        if (!o.has("type")) {
            if (o.has("min") && o.has("max")) return new Uniform(parse(o.get("min")), parse(o.get("max")));
            throw new LootParseException("수 공급자 type 없음: " + e);
        }
        return switch (LootJson.id(o, "type")) {
            case "minecraft:constant" -> new Constant(LootJson.req(o, "value").getAsFloat());
            case "minecraft:uniform" -> new Uniform(parse(LootJson.req(o, "min")), parse(LootJson.req(o, "max")));
            case "minecraft:binomial" -> new Binomial(parse(LootJson.req(o, "n")), parse(LootJson.req(o, "p")));
            case "minecraft:score" -> {
                JsonElement t = LootJson.req(o, "target");
                String target = t.isJsonPrimitive() ? t.getAsString()
                        : LootJson.id(t.getAsJsonObject(), "type").equals("minecraft:fixed") ? "fixed:" + t.getAsJsonObject().get("name").getAsString()
                        : t.getAsJsonObject().get("target").getAsString();
                yield new Score(target, LootJson.req(o, "score").getAsString(), o.has("scale") ? o.get("scale").getAsFloat() : 1.0f);
            }
            case "minecraft:storage" -> new Storage(LootJson.req(o, "storage").getAsString(), LootJson.req(o, "path").getAsString());
            case "minecraft:enchantment_level" -> new EnchantmentLevel(LevelBasedValue.parse(LootJson.req(o, "amount")));
            default -> throw new LootParseException("모르는 수 공급자: " + o.get("type"));
        };
    }
}
