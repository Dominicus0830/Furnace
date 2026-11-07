// 약탈자·습격 바닐라 대조(M4) 후속: 불길한 깃발 구성 요소, addWaveMob 의 같은 UUID 교체, 습격 보너스 석궁 마법, 영웅 처치자 판정.
// 기대값은 바닐라 1.21.1 바이트코드 대조 결과(Raid.getLeaderBannerInstance·Raid.addWaveMob·Pillager.applyRaidBuffs·Raider.die).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.LootData;
import org.domi.furnace.server.loot.VanillaChestLootTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.raid.Raid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RaidParityTest {

    @BeforeAll
    static void install() throws Exception { TestRecipes.manager(); }

    /** 데이터 팩(전리품 데이터)을 붙일 수 있는 습격 시험 레벨. */
    static final class DataRaidLevel extends PatrolSpawnerTest.PatrolLevel {
        private final LootData data;

        DataRaidLevel(LootData data) {
            super(11L);
            this.data = data;
        }

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(super.services()) {
                @Override public LootData loot() { return data; }
            };
        }

        @Override public boolean isVillage(Pos pos) { return true; }
    }

    private static Raid raid(DataRaidLevel level, int omenLevel) {
        NbtCompound t = new NbtCompound();
        t.putInt("Id", 3);
        t.putBoolean("Started", true);
        t.putBoolean("Active", true);
        t.putInt("GroupsSpawned", 1);
        t.putInt("NumGroups", 7);
        t.putInt("BadOmenLevel", omenLevel);
        t.putString("Status", "ongoing");
        t.putInt("CX", 8);
        t.putInt("CY", -60);
        t.putInt("CZ", 8);
        return new Raid(level, t);
    }

    private static Pillager pillager(DataRaidLevel level, Raid raid) {
        var p = new Pillager(level, level.nextEntityId());
        p.moveTo(8.5, -60, 8.5, 0, 0);
        p.setCurrentRaid(raid);
        p.setCanJoinRaid(true);
        level.addFreshEntity(p);
        return p;
    }

    @Test
    void leaderBannerHasOnlyPatternsTooltipFlagAndGoldName() {
        var banner = Raider.leaderBanner();
        assertEquals("minecraft:white_banner", ItemTools.itemName(banner));
        assertEquals("common", ItemTools.component(banner, "minecraft:rarity"), "바닐라 깃발에는 희귀도 구성 요소가 없다(기본 common)");
        String name = String.valueOf(ItemTools.component(banner, "minecraft:item_name"));
        assertTrue(name.contains("block.minecraft.ominous_banner"), name);
        assertTrue(name.contains("gold"), "이름은 금색 스타일(바닐라 withStyle(GOLD)): " + name);
        assertNotNull(ItemTools.component(banner, "minecraft:banner_patterns"));
        assertNotNull(ItemTools.component(banner, "minecraft:hide_additional_tooltip"));
    }

    @Test
    void addWaveMobReplacesSameUuidInstanceAndKeepsCount() {
        var level = new DataRaidLevel(null);
        Raid raid = raid(level, 0);
        var first = pillager(level, raid);
        assertTrue(raid.addWaveMob(1, first, false));
        assertEquals(1, raid.getTotalRaidersAlive());
        // 청크가 내려갔다 올라온 같은 UUID 의 새 개체: 바닐라는 옛 개체를 빼고 새 개체를 넣고 참을 돌려준다
        var second = new Pillager(level, level.nextEntityId());
        second.setUuid(first.uuid());
        second.moveTo(8.5, -60, 8.5, 0, 0);
        assertTrue(raid.addWaveMob(1, second, false), "바닐라 addWaveMob 은 항상 참");
        assertEquals(1, raid.getTotalRaidersAlive(), "같은 UUID 는 하나로 센다");
        // 옛 개체가 사라져도(updateRaiders 의 제거) 새 개체가 남아 습격이 계속된다
        raid.removeFromRaid(first, true);
        assertEquals(1, raid.getTotalRaidersAlive(), "새 개체가 집합에 남아 있어야 한다");
    }

    @Test
    void enchantOddsByOmenLevel() {
        var level = new DataRaidLevel(null);
        assertEquals(0.0F, raid(level, 0).getEnchantOdds());
        assertEquals(0.0F, raid(level, 1).getEnchantOdds());
        assertEquals(0.1F, raid(level, 2).getEnchantOdds());
        assertEquals(0.25F, raid(level, 3).getEnchantOdds());
        assertEquals(0.5F, raid(level, 4).getEnchantOdds());
        assertEquals(0.75F, raid(level, 5).getEnchantOdds());
    }

    @Test
    void pillagerRaidCrossbowEnchantByWaveAndOdds() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var level = new DataRaidLevel(data);
        // 징조 5 단계(확률 0.75): 파도 6 은 post_wave_5(속사 2), 파도 4 는 post_wave_3, 파도 3 은 바뀌지 않는다
        Raid raid = raid(level, 5);
        int wave6 = 0, wave4 = 0, wave3 = 0, n = 400;
        for (int i = 0; i < n; i++) {
            var p = pillager(level, raid);
            p.applyRaidBuffs(6, false);
            if (!EnchantTools.levels(p.getItemBySlot(EquipmentSlot.MAINHAND), EnchantTools.ENCHANTMENTS).isEmpty()) {
                assertEquals(2, EnchantTools.levels(p.getItemBySlot(EquipmentSlot.MAINHAND), EnchantTools.ENCHANTMENTS).get("minecraft:quick_charge"));
                wave6++;
            }
            var q = pillager(level, raid);
            q.applyRaidBuffs(4, false);
            if (!EnchantTools.levels(q.getItemBySlot(EquipmentSlot.MAINHAND), EnchantTools.ENCHANTMENTS).isEmpty()) wave4++;
            var r = pillager(level, raid);
            r.applyRaidBuffs(3, false);
            if (!r.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) wave3++;
        }
        assertEquals(0, wave3, "파도 3 이하는 석궁을 바꾸지 않는다");
        assertTrue(wave6 > n * 0.65 && wave6 < n * 0.85, "확률 0.75: " + wave6);
        assertTrue(wave4 > n * 0.65 && wave4 < n * 0.85, "확률 0.75: " + wave4);
        // 징조 0~1 단계(확률 0): 마법 없음
        Raid calm = raid(level, 0);
        for (int i = 0; i < 100; i++) {
            var p = pillager(level, calm);
            p.applyRaidBuffs(7, false);
            assertTrue(p.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() || EnchantTools.levels(p.getItemBySlot(EquipmentSlot.MAINHAND), EnchantTools.ENCHANTMENTS).isEmpty());
        }
    }

    private static Player player() {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        return p;
    }

    @Test
    void heroOfTheVillageOnlyWhenPlayerIsDamageSourceEntity() {
        var level = new DataRaidLevel(null);
        var alex = player();
        var hit = new Entity.Damage("minecraft:player_attack", false, false, false, ItemStack.EMPTY);
        var mob = new Entity.Damage("minecraft:mob_attack", false, false, false, ItemStack.EMPTY);

        // 플레이어가 죽이면 영웅 후보
        Raid raid = raid(level, 0);
        var killed = pillager(level, raid);
        raid.addWaveMob(1, killed, false);
        killed.hurtBy(hit, 1000.0F, alex);
        assertTrue(raid.heroesOfTheVillage().contains(alex.uuid()), "플레이어 처치");

        // 플레이어가 때린 뒤 다른 원인(원인 엔티티 없음)으로 죽으면 영웅이 아니다(바닐라 damageSource.getEntity() == null)
        Raid raid2 = raid(level, 0);
        var other = pillager(level, raid2);
        raid2.addWaveMob(1, other, false);
        other.hurtBy(hit, 1.0F, alex);
        other.hurtBy(new Entity.Damage("minecraft:generic_kill", false, false, true, ItemStack.EMPTY), 1000.0F, null);
        assertTrue(raid2.heroesOfTheVillage().isEmpty(), "때린 뒤 원인 없는 죽음은 영웅이 아니다");

        // 몹이 죽이면 영웅이 아니다
        Raid raid3 = raid(level, 0);
        var third = pillager(level, raid3);
        raid3.addWaveMob(1, third, false);
        third.hurtBy(mob, 1000.0F, pillager(level, raid3));
        assertTrue(raid3.heroesOfTheVillage().isEmpty());
    }
}
