// 몹 생성 장비 마법(Mob.populateDefaultEquipmentEnchantments → mob_spawn_equipment 제공자): 데이터 팩 없을 때 무해(확률 굴림 난수만 소비),
// 데이터 팩이 있으면 넘긴 난수만 써서 결정적으로 걸고 결과는 enchantments 컴포넌트에 들어간다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.loot.LootData;
import org.domi.furnace.server.loot.VanillaChestLootTest;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.Test;

class MobSpawnEnchantTest {

    /** 어려움 + 오래된 월드·청크 → 실효 난이도 6.75, 특수 배율 1.0. */
    static final DifficultyInstance HARD_FULL = new DifficultyInstance(3, 100_000_000L, 4_000_000L, 1.0F);

    /** nextFloat 횟수를 세는 레벨 난수 대역. */
    static final class Counting implements RandomSource {
        final LegacyRandomSource r;
        int floats;
        Counting(long seed) { r = new LegacyRandomSource(seed); }
        public int nextInt() { return r.nextInt(); }
        public int nextInt(int bound) { return r.nextInt(bound); }
        public long nextLong() { return r.nextLong(); }
        public float nextFloat() { floats++; return r.nextFloat(); }
        public double nextDouble() { return r.nextDouble(); }
        public boolean nextBoolean() { return r.nextBoolean(); }
        public double nextGaussian() { return r.nextGaussian(); }
    }

    /** 데이터 팩(전리품 데이터)을 붙인 시험 레벨. */
    static final class DataLevel extends BlockPlacementTest.TestLevel {
        final LevelServices services;
        DataLevel(LootData data) {
            services = new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public LootData loot() { return data; }
            };
        }
        @Override protected LevelServices services() { return services; }
    }

    private static Zombie zombie(BlockPlacementTest.TestLevel level) throws Exception {
        assumeTrue(TestRecipes.manager() != null);
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.setItemSlot(EquipmentSlot.MAINHAND, TestRecipes.item("diamond_sword", 1));
        z.setItemSlot(EquipmentSlot.HEAD, TestRecipes.item("iron_helmet", 1));
        return z;
    }

    @Test
    void withoutDataPackOnlyRollsChanceAndLeavesItemsAlone() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        assertNull(level.lootData());
        var z = zombie(level);
        var r = new Counting(3);
        z.populateDefaultEquipmentEnchantments(r, HARD_FULL);
        assertEquals(2, r.floats, "손·머리 한 번씩(빈 칸은 굴리지 않는다)");
        assertTrue(EnchantTools.levels(z.getItemBySlot(EquipmentSlot.MAINHAND), EnchantTools.ENCHANTMENTS).isEmpty());
        assertTrue(EnchantTools.levels(z.getItemBySlot(EquipmentSlot.HEAD), EnchantTools.ENCHANTMENTS).isEmpty());
        assertEquals(TestRecipes.item("diamond_sword", 1), z.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    @Test
    void withDataPackEnchantsDeterministicallyFromGivenRandom() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var level = new DataLevel(data);
        int enchantedSwords = 0, enchantedHelmets = 0;
        for (long i = 0; i < 200; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;   // 연속 시드는 java.util.Random 의 첫 출력이 닮아 흩뿌린다
            var a = zombie(level);
            var b = zombie(level);
            a.populateDefaultEquipmentEnchantments(new LegacyRandomSource(seed), HARD_FULL);
            b.populateDefaultEquipmentEnchantments(new LegacyRandomSource(seed), HARD_FULL);
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD}) {
                assertEquals(a.getItemBySlot(slot), b.getItemBySlot(slot), "같은 시드 → 같은 장비(시드 " + seed + ")");
                Map<String, Integer> levels = EnchantTools.levels(a.getItemBySlot(slot), EnchantTools.ENCHANTMENTS);
                for (var e : levels.entrySet()) {
                    var en = data.registries().enchantment(e.getKey());
                    assertNotNull(en, e.getKey());
                    assertTrue(data.registries().tag("minecraft:enchantment", "minecraft:on_mob_spawn_equipment").contains(e.getKey()));
                    assertTrue(e.getValue() >= 1 && e.getValue() <= en.maxLevel());
                }
                if (!levels.isEmpty()) { if (slot == EquipmentSlot.MAINHAND) enchantedSwords++; else enchantedHelmets++; }
            }
            // 레벨 난수 한 줄기에서 손 → 머리 순서로 굴린다: 손이 걸리지 않은 경우에도 머리 굴림은 이어진다.
        }
        // 손 25 %·방어구 50 % 확률(특수 배율 1): 200 번이면 둘 다 넉넉히 나온다.
        assertTrue(enchantedSwords > 20 && enchantedSwords < 90, "검 " + enchantedSwords);
        assertTrue(enchantedHelmets > 60 && enchantedHelmets < 140, "투구 " + enchantedHelmets);
    }

    @Test
    void chanceRollUsesTheSameRandomStreamBeforeTheProvider() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var level = new DataLevel(data);
        // 첫 nextFloat 가 0.25 미만인 시드를 찾아, 그 뒤의 난수로 EnchantTools.enchantFromProvider 를 직접 돌린 결과와 같은지 본다.
        long seed = 0;
        while (new LegacyRandomSource(seed).nextFloat() >= 0.25F) seed++;
        var z = zombie(level);
        z.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        z.populateDefaultEquipmentEnchantments(new LegacyRandomSource(seed), HARD_FULL);
        var r = new LegacyRandomSource(seed);
        r.nextFloat();   // 확률 굴림
        ItemStack expected = EnchantTools.enchantFromProvider(TestRecipes.item("diamond_sword", 1), data, Mob.MOB_SPAWN_EQUIPMENT, 1.0F, r);
        assertFalse(EnchantTools.levels(expected, EnchantTools.ENCHANTMENTS).isEmpty());
        assertEquals(expected, z.getItemBySlot(EquipmentSlot.MAINHAND));
    }
}
