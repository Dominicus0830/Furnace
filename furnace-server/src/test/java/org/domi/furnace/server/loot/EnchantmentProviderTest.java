// 마법 부여 제공자(enchantment_provider): 바닐라 데이터 읽기, 고정 시드 결정성, 비호환 조합 없음, 경계(특수 배율 0·과대), 난수 호출 순서,
// 바닐라 분포 성질(다이아 검: 레벨 3 마법은 나오고 날카로움 IV 는 비용 상한 밖이라 안 나온다).
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.Test;

class EnchantmentProviderTest {

    static final String MOB = "minecraft:mob_spawn_equipment";
    static final LootStack SWORD = new LootStack("minecraft:diamond_sword", 1);

    /** 호출 순서를 기록하는 난수(bound 가 있는 nextInt 는 "i<bound>", 그 밖은 이름). */
    static final class Recording implements RandomSource {
        final LegacyRandomSource r;
        final List<String> calls = new ArrayList<>();
        Recording(long seed) { r = new LegacyRandomSource(seed); }
        public int nextInt() { calls.add("i"); return r.nextInt(); }
        public int nextInt(int bound) { calls.add("i" + bound); return r.nextInt(bound); }
        public long nextLong() { calls.add("l"); return r.nextLong(); }
        public float nextFloat() { calls.add("f"); return r.nextFloat(); }
        public double nextDouble() { calls.add("d"); return r.nextDouble(); }
        public boolean nextBoolean() { calls.add("b"); return r.nextBoolean(); }
        public double nextGaussian() { calls.add("g"); return r.nextGaussian(); }
    }

    static Map<String, Integer> enchant(LootData data, LootStack stack, long seed, float special) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        data.enchantmentProvider(MOB).enchant(stack, levels, new LegacyNoiseRandom(new LegacyRandomSource(seed)), special, data.registries());
        return levels;
    }

    @Test
    void vanillaProvidersParse() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var mob = assertInstanceOf(EnchantmentProvider.ByCostWithDifficulty.class, data.enchantmentProvider(MOB));
        assertEquals(5, mob.minCost());
        assertEquals(17, mob.maxCostSpan());
        assertEquals("minecraft:on_mob_spawn_equipment", mob.enchantments().tag());
        var crossbow = assertInstanceOf(EnchantmentProvider.Single.class, data.enchantmentProvider("minecraft:pillager_spawn_crossbow"));
        assertEquals("minecraft:piercing", crossbow.enchantment());
        assertEquals(1, crossbow.level().sample(null));
        assertNotNull(data.enchantmentProvider("minecraft:raid/vindicator_post_wave_5"));
        assertNull(data.enchantmentProvider("minecraft:no_such_provider"));
    }

    @Test
    void sameSeedSameResult() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        for (long seed = 1; seed <= 200; seed++) {
            assertEquals(enchant(data, SWORD, seed, 1.0F), enchant(data, SWORD, seed, 1.0F));
        }
    }

    @Test
    void neverIncompatibleAndAlwaysPrimaryWithinLevelRange() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        LootRegistries regs = data.registries();
        List<String> allowed = regs.tag("minecraft:enchantment", "minecraft:on_mob_spawn_equipment");
        assertFalse(allowed.isEmpty());
        int nonEmpty = 0;
        for (long seed = 0; seed < 3000; seed++) {
            Map<String, Integer> got = enchant(data, SWORD, seed, 1.0F);
            if (!got.isEmpty()) nonEmpty++;
            List<String> ids = new ArrayList<>(got.keySet());
            for (int i = 0; i < ids.size(); i++) {
                Enchantment a = regs.enchantment(ids.get(i));
                assertNotNull(a);
                assertTrue(allowed.contains(a.id()), a.id() + " 는 태그 밖");
                assertTrue(a.isPrimaryItem(SWORD, regs), a.id() + " 는 다이아 검의 주 대상이 아님");
                int lvl = got.get(a.id());
                assertTrue(lvl >= 1 && lvl <= a.maxLevel(), a.id() + " 레벨 " + lvl);
                for (int j = i + 1; j < ids.size(); j++) {
                    assertTrue(Enchantment.areCompatible(a, regs.enchantment(ids.get(j)), regs), a.id() + " + " + ids.get(j) + " 비호환");
                }
            }
        }
        assertEquals(3000, nonEmpty, "비용 5 이상이면 다이아 검에는 항상 하나는 걸린다");
    }

    @Test
    void randomCallOrderMatchesVanilla() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        // 특수 배율 0: cost = randomBetweenInclusive(5, 5) → nextInt(1); 다이아 검 enchantmentValue 10 → nextInt(3) 두 번, nextFloat 두 번,
        // 가중치 선택 nextInt(합), 그 뒤 nextInt(50) 루프(비용 ≤ 8 이라 50 굴림이 비용을 넘기면 끝).
        Recording r = new Recording(7);
        Map<String, Integer> levels = new LinkedHashMap<>();
        data.enchantmentProvider(MOB).enchant(SWORD, levels, new LegacyNoiseRandom(r), 0.0F, data.registries());
        assertEquals(List.of("i1", "i3", "i3", "f", "f"), r.calls.subList(0, 5), r.calls.toString());
        assertTrue(r.calls.get(5).startsWith("i") && !r.calls.get(5).equals("i50"), "여섯째는 가중치 합 굴림: " + r.calls);
        assertEquals("i50", r.calls.get(6), "일곱째는 추가 마법 굴림 nextInt(50): " + r.calls);
        assertFalse(levels.isEmpty());

        // 특수 배율 1: cost = randomBetweenInclusive(5, 22) → nextInt(18) 이 첫 굴림.
        Recording r1 = new Recording(7);
        data.enchantmentProvider(MOB).enchant(SWORD, new LinkedHashMap<>(), new LegacyNoiseRandom(r1), 1.0F, data.registries());
        assertEquals("i18", r1.calls.get(0));
    }

    @Test
    void boundariesZeroAndHugeSpecialMultiplier() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        LootRegistries regs = data.registries();
        // 특수 배율 0 → 비용 5..8: 레벨 1 마법만(다이아 검의 어떤 마법도 레벨 2 최소 비용이 8 보다 크다).
        for (long seed = 0; seed < 500; seed++) {
            for (var e : enchant(data, SWORD, seed, 0.0F).entrySet()) assertEquals(1, e.getValue(), e.getKey());
        }
        // 과대 배율(바닐라에선 1 이 최대): 크래시 없이 정의 범위 안의 레벨만.
        var huge = new EnchantmentProvider.ByCostWithDifficulty(new HolderSet("minecraft:enchantment", "minecraft:on_mob_spawn_equipment", null), 5, 17);
        for (long seed = 0; seed < 200; seed++) {
            Map<String, Integer> levels = new LinkedHashMap<>();
            huge.enchant(SWORD, levels, new LegacyNoiseRandom(new LegacyRandomSource(seed)), 1000.0F, regs);
            for (var e : levels.entrySet()) {
                Enchantment en = regs.enchantment(e.getKey());
                assertTrue(e.getValue() >= 1 && e.getValue() <= en.maxLevel(), e.toString());
            }
        }
        // 마법을 걸 수 없는 아이템(enchantmentValue 0)은 난수를 비용 굴림 하나만 쓰고 끝난다.
        Recording r = new Recording(1);
        Map<String, Integer> none = new LinkedHashMap<>();
        data.enchantmentProvider(MOB).enchant(new LootStack("minecraft:stone", 1), none, new LegacyNoiseRandom(r), 1.0F, regs);
        assertTrue(none.isEmpty());
        assertEquals(List.of("i18"), r.calls);
    }

    @Test
    void diamondSwordDistributionMatchesVanillaBounds() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        int level3 = 0, multi = 0, sharpness = 0;
        for (long seed = 0; seed < 5000; seed++) {
            Map<String, Integer> got = enchant(data, SWORD, seed, 1.0F);
            if (got.size() >= 2) multi++;
            for (var e : got.entrySet()) {
                if (e.getValue() >= 3) level3++;
                if (e.getKey().equals("minecraft:sharpness")) {
                    sharpness++;
                    // 최대 비용 22 → 1+2+2 → 27 → ×1.15 → 31 < 날카로움 IV 최소 비용 34
                    assertTrue(e.getValue() <= 3, "날카로움 " + e.getValue());
                }
            }
        }
        assertTrue(level3 > 0, "레벨 3 마법(날카로움 III·내구성 III 등)이 나와야 함");
        assertTrue(multi > 0, "두 개 이상 걸리는 경우가 있어야 함");
        assertTrue(sharpness > 1000, "날카로움은 가중치 10 으로 가장 흔하다: " + sharpness);
    }

    @Test
    void singleProviderClampsToDefinition() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var p = new EnchantmentProvider.Single("minecraft:sharpness", new EnchantmentProvider.IntProvider(9, 9));
        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("minecraft:sharpness", 2);
        NoiseRandom r = new LegacyNoiseRandom(new LegacyRandomSource(0));
        p.enchant(SWORD, levels, r, 1.0F, data.registries());
        assertEquals(5, levels.get("minecraft:sharpness"), "max_level 5 로 자른다");
        new EnchantmentProvider.Single("minecraft:sharpness", new EnchantmentProvider.IntProvider(1, 1)).enchant(SWORD, levels, r, 1.0F, data.registries());
        assertEquals(5, levels.get("minecraft:sharpness"), "upgrade: 낮은 레벨로 내리지 않는다");
    }
}
