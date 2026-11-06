// 바닐라 EnchantmentProvider(데이터 팩 enchantment_provider/*.json) — 몹 생성 장비·약탈자 석궁·엔더맨 전리품 마법.
// by_cost / by_cost_with_difficulty 는 EnchantmentHelper.selectEnchantment(마법 부여대와 같은 선택)로, single 은 레벨만 뽑는다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public sealed interface EnchantmentProvider {

    /**
     * EnchantmentProvider.enchant(stack, mutable, random, difficulty): 고른 마법을 levels 에 upgrade(더 높은 레벨만) 로 넣는다.
     * special 은 DifficultyInstance.getSpecialMultiplier().
     */
    void enchant(LootStack stack, Map<String, Integer> levels, NoiseRandom random, float special, LootRegistries regs);

    /** ItemEnchantments.Mutable.upgrade: 0 이하는 무시, 255 로 자르고 더 높은 레벨만. */
    static void upgrade(Map<String, Integer> levels, String enchantment, int level) {
        if (level > 0) levels.merge(enchantment, Math.min(level, 255), Integer::max);
    }

    /** by_cost: cost(IntProvider) 를 뽑고 selectEnchantment. */
    record ByCost(HolderSet enchantments, IntProvider cost) implements EnchantmentProvider {
        @Override
        public void enchant(LootStack stack, Map<String, Integer> levels, NoiseRandom random, float special, LootRegistries regs) {
            int c = cost.sample(random);
            for (Enchantments.Instance i : Enchantments.select(random, stack, c, Enchantments.resolve(enchantments, regs), regs)) {
                upgrade(levels, i.enchantment().id(), i.level());
            }
        }
    }

    /** by_cost_with_difficulty: cost = randomBetweenInclusive(min, min + (int)(special × span)) 뒤 selectEnchantment. */
    record ByCostWithDifficulty(HolderSet enchantments, int minCost, int maxCostSpan) implements EnchantmentProvider {
        @Override
        public void enchant(LootStack stack, Map<String, Integer> levels, NoiseRandom random, float special, LootRegistries regs) {
            int max = minCost + (int) (special * maxCostSpan);
            int c = random.nextInt(max - minCost + 1) + minCost;   // Mth.randomBetweenInclusive
            for (Enchantments.Instance i : Enchantments.select(random, stack, c, Enchantments.resolve(enchantments, regs), regs)) {
                upgrade(levels, i.enchantment().id(), i.level());
            }
        }
    }

    /** single: 한 마법을 level(IntProvider) 로, 정의의 1..max_level 로 잘라서. */
    record Single(String enchantment, IntProvider level) implements EnchantmentProvider {
        @Override
        public void enchant(LootStack stack, Map<String, Integer> levels, NoiseRandom random, float special, LootRegistries regs) {
            Enchantment e = regs.enchantment(enchantment);
            int l = level.sample(random);
            if (e != null) l = Mth.clamp(l, 1, e.maxLevel());
            upgrade(levels, enchantment, l);
        }
    }

    /** 바닐라 IntProvider 중 constant·uniform(마법 부여 제공자가 쓰는 것). */
    record IntProvider(int min, int max) {
        /** ConstantInt.sample = value, UniformInt.sample = Mth.randomBetweenInclusive(random, min, max). */
        int sample(NoiseRandom random) { return min == max ? min : random.nextInt(max - min + 1) + min; }

        static IntProvider parse(JsonElement e) {
            if (e.isJsonPrimitive()) return new IntProvider(e.getAsInt(), e.getAsInt());
            JsonObject o = e.getAsJsonObject();
            return switch (LootJson.id(o, "type")) {
                case "minecraft:constant" -> { int v = LootJson.req(o, "value").getAsInt(); yield new IntProvider(v, v); }
                case "minecraft:uniform" -> {
                    JsonObject v = o.has("value") ? o.get("value").getAsJsonObject() : o;
                    yield new IntProvider(LootJson.req(v, "min_inclusive").getAsInt(), LootJson.req(v, "max_inclusive").getAsInt());
                }
                default -> throw new LootParseException("모르는 정수 공급자: " + o.get("type"));
            };
        }
    }

    static EnchantmentProvider parse(JsonElement e, LootRegistries regs) {
        if (!e.isJsonObject()) throw new LootParseException("마법 부여 제공자가 아님: " + e);
        JsonObject o = e.getAsJsonObject();
        return switch (LootJson.id(o, "type")) {
            case "minecraft:by_cost" -> new ByCost(HolderSet.parse("minecraft:enchantment", LootJson.req(o, "enchantments"), regs),
                    IntProvider.parse(LootJson.req(o, "cost")));
            case "minecraft:by_cost_with_difficulty" -> {
                int min = LootJson.req(o, "min_cost").getAsInt(), span = LootJson.req(o, "max_cost_span").getAsInt();
                if (min < 1 || min > 10000 || span < 0 || span > 10000) throw new LootParseException("min_cost/max_cost_span 범위 밖");
                yield new ByCostWithDifficulty(HolderSet.parse("minecraft:enchantment", LootJson.req(o, "enchantments"), regs), min, span);
            }
            case "minecraft:single" -> {
                List<String> ids = HolderSet.parse("minecraft:enchantment", LootJson.req(o, "enchantment"), regs).direct();
                if (ids == null || ids.size() != 1) throw new LootParseException("single 의 enchantment 는 하나여야 함");
                yield new Single(ids.get(0), IntProvider.parse(LootJson.req(o, "level")));
            }
            default -> throw new LootParseException("모르는 마법 부여 제공자: " + o.get("type"));
        };
    }
}
