// 바닐라 전리품 엔트리 — 단일(item·tag·loot_table·dynamic·empty)과 복합(alternatives·sequence·group).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;

public interface LootEntry {

    /** 바닐라 LootPoolEntry — 풀이 가중치로 고르는 후보. */
    interface Candidate {
        int weight(float luck);

        void create(Consumer<LootStack> out, LootContext ctx);
    }

    /** 바닐라 ComposableEntryContainer.expand. */
    boolean expand(LootContext ctx, Consumer<Candidate> out);

    static Consumer<LootStack> decorate(LootFunction fn, Consumer<LootStack> out, LootContext ctx) {
        return s -> out.accept(fn.apply(s, ctx));
    }

    static LootEntry parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        String type = LootJson.id(o, "type");
        List<LootCondition> conditions = LootCondition.list(o, "conditions", regs);
        return switch (type) {
            case "minecraft:alternatives", "minecraft:sequence", "minecraft:group" -> {
                List<LootEntry> children = LootJson.list(o, "children", x -> parse(x, regs));
                yield new Composite(type, conditions, children);
            }
            default -> singleton(type, o, conditions, regs);
        };
    }

    private static Singleton singleton(String type, JsonObject o, List<LootCondition> conditions, LootRegistries regs) {
        int weight = LootJson.integer(o, "weight", 1), quality = LootJson.integer(o, "quality", 0);
        LootFunction fns = LootFunction.compose(LootFunction.list(o, "functions", regs));
        return switch (type) {
            case "minecraft:item" -> {
                String item = LootJson.id(o, "name");
                if (!regs.exists("minecraft:item", item)) throw new LootParseException("Unknown item " + item);
                yield new Singleton(weight, quality, conditions, fns, (out, ctx) -> out.accept(new LootStack(item, 1)), null);
            }
            case "minecraft:empty" -> new Singleton(weight, quality, conditions, fns, (out, ctx) -> {}, null);
            case "minecraft:dynamic" -> {
                String name = LootJson.id(o, "name");
                yield new Singleton(weight, quality, conditions, fns, (out, ctx) -> ctx.params().addDynamicDrops(name, out), null);
            }
            case "minecraft:loot_table" -> {
                JsonElement v = LootJson.req(o, "value");
                if (v.isJsonPrimitive()) {
                    String id = LootJson.id(v.getAsString());
                    yield new Singleton(weight, quality, conditions, fns, (out, ctx) -> ctx.data().table(id).getRandomItemsRaw(ctx, out), null);
                }
                LootTable inline = LootTable.parse(v.getAsJsonObject(), regs);
                yield new Singleton(weight, quality, conditions, fns, (out, ctx) -> inline.getRandomItemsRaw(ctx, out), null);
            }
            case "minecraft:tag" -> {
                String tag = LootJson.id(o, "name");
                boolean expand = LootJson.req(o, "expand").getAsBoolean();
                Creator all = (out, ctx) -> {
                    for (String item : ctx.registries().tag("minecraft:item", tag)) out.accept(new LootStack(item, 1));
                };
                yield new Singleton(weight, quality, conditions, fns, all, expand ? tag : null);
            }
            default -> throw new LootParseException("모르는 엔트리: " + type);
        };
    }

    interface Creator { void create(Consumer<LootStack> out, LootContext ctx); }

    /** 바닐라 LootPoolSingletonContainer(+ TagEntry expand). */
    record Singleton(int weight, int quality, List<LootCondition> conditions, LootFunction functions, Creator creator,
                     String expandTag) implements LootEntry, Candidate {

        public int weight(float luck) { return Math.max(Mth.floor((float) weight + (float) quality * luck), 0); }

        public void create(Consumer<LootStack> out, LootContext ctx) { creator.create(decorate(functions, out, ctx), ctx); }

        public boolean expand(LootContext ctx, Consumer<Candidate> out) {
            if (!LootCondition.allOf(conditions, ctx)) return false;
            if (expandTag == null) {
                out.accept(this);
                return true;
            }
            // 태그 펼치기: 아이템마다 후보 하나 — 바닐라처럼 이 엔트리의 functions 는 적용하지 않는다
            for (String item : ctx.registries().tag("minecraft:item", expandTag)) {
                out.accept(new Candidate() {
                    public int weight(float luck) { return Singleton.this.weight(luck); }

                    public void create(Consumer<LootStack> o, LootContext c) { o.accept(new LootStack(item, 1)); }
                });
            }
            return true;
        }
    }

    record Composite(String type, List<LootCondition> conditions, List<LootEntry> children) implements LootEntry {
        public boolean expand(LootContext ctx, Consumer<Candidate> out) {
            if (!LootCondition.allOf(conditions, ctx)) return false;
            switch (type) {
                case "minecraft:alternatives" -> {
                    for (LootEntry c : children) if (c.expand(ctx, out)) return true;
                    return false;
                }
                case "minecraft:sequence" -> {
                    for (LootEntry c : children) if (!c.expand(ctx, out)) return false;
                    return true;
                }
                default -> {
                    for (LootEntry c : children) c.expand(ctx, out);
                    return true;
                }
            }
        }
    }
}
