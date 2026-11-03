// 속성 위의 인챈트: 물갈퀴·호흡·민첩한 웅크리기·친수성(속성 수정자), 영혼 가속·서리 걸음(위치 변화 효과), 호흡의 공기 소모.
// 기대값은 data/minecraft/enchantment/*.json 과 디컴파일(LivingEntity.decreaseAirSupply, Enchantment.runLocationChangedEffects,
// ReplaceDisk)을 손으로 계산한 것이다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerEnchantAttributeTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack enchanted(String item, String enchant, int level) {
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", item), 1);
        return EnchantTools.update(s, m -> m.put(enchant, level));
    }

    private static Player player() {
        var p = new Player(5, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    /** 레벨 난수를 고정한 시험용 레벨. */
    private static BlockPlacementTest.TestLevel seededLevel(long seed) {
        var rnd = new LegacyRandomSource(seed);
        return new BlockPlacementTest.TestLevel() {
            @Override public org.domi.furnace.server.block.RandomSource random() { return rnd; }
        };
    }

    private static double attr(Player p, Attribute a) {
        p.syncAttributes();
        return p.attributes().value(a);
    }

    @Test
    void depthStriderGivesThirdPerLevelOnFeetOnly() {
        var p = player();
        // linear(0.33333334, +0.33333334): 레벨 1 = 0.33333334F, 레벨 2 = 0.6666667F, 레벨 3 = 1.0000001 → 범위 [0,1] 로 1.0
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:depth_strider", 1));
        assertEquals(0.33333334F, attr(p, Attribute.WATER_MOVEMENT_EFFICIENCY), 1e-9);
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:depth_strider", 2));
        assertEquals(0.6666667F, attr(p, Attribute.WATER_MOVEMENT_EFFICIENCY), 1e-9);
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:depth_strider", 3));
        assertEquals(1.0, attr(p, Attribute.WATER_MOVEMENT_EFFICIENCY));
        assertTrue(p.attributes().get(Attribute.WATER_MOVEMENT_EFFICIENCY).hasModifier("minecraft:enchantment.depth_strider/feet"));
        // 발 칸이 아니면 켜지지 않는다(slots = feet)
        p.inventory().set(8, ItemStack.EMPTY);
        p.inventory().set(p.inventory().heldWindowSlot(), enchanted("minecraft:iron_boots", "minecraft:depth_strider", 3));
        assertEquals(0.0, attr(p, Attribute.WATER_MOVEMENT_EFFICIENCY));
    }

    @Test
    void respirationSwiftSneakAquaAffinityValues() {
        var p = player();
        p.inventory().set(5, enchanted("minecraft:iron_helmet", "minecraft:respiration", 3));   // linear(1,+1): 3
        assertEquals(3.0, attr(p, Attribute.OXYGEN_BONUS));
        assertTrue(p.attributes().get(Attribute.OXYGEN_BONUS).hasModifier("minecraft:enchantment.respiration/head"));
        p.inventory().set(7, enchanted("minecraft:iron_leggings", "minecraft:swift_sneak", 3));   // 0.3 + 0.15 × 3 = 0.75
        assertEquals(0.75, attr(p, Attribute.SNEAKING_SPEED), 1e-6);
        p.inventory().set(7, enchanted("minecraft:iron_leggings", "minecraft:swift_sneak", 1));   // 0.45
        assertEquals(0.45, attr(p, Attribute.SNEAKING_SPEED), 1e-6);
        p.inventory().set(5, enchanted("minecraft:iron_helmet", "minecraft:aqua_affinity", 1));   // 0.2 × (1 + 4) = 1.0
        assertEquals(1.0, attr(p, Attribute.SUBMERGED_MINING_SPEED), 1e-6);
        assertEquals(1.0, PlayerAttributes.value(p, Attribute.SUBMERGED_MINING_SPEED), 1e-6);
    }

    @Test
    void respirationSlowsAirLossByOneOverBonusPlusOne() {
        var surround = new PlayerVitals.Surroundings(true, true, true, false, null, false, -64);
        var p = player();
        p.vitals().attributeInputs(20.0, java.util.List.of(), 3.0);
        p.random().setSeed(12345L);
        var ref = new LegacyRandomSource(12345L);
        int expected = 300;
        for (int i = 0; i < 100; i++) {
            if (!(ref.nextDouble() >= 1.0 / (3.0 + 1.0))) expected--;   // 난수가 1/(b+1) 미만일 때만 깎는다
            p.vitals().tick(surround);
        }
        assertEquals(expected, p.vitals().air());
        assertTrue(expected > 300 - 100 && expected < 300 - 10, "4틱에 약 1 — 100틱에 대략 25");
    }

    @Test
    void noBonusLosesOneAirPerTickWithoutConsumingRandom() {
        var surround = new PlayerVitals.Surroundings(true, true, true, false, null, false, -64);
        var p = player();
        p.random().setSeed(777L);
        long before = p.random().rawState();
        for (int i = 0; i < 10; i++) p.vitals().tick(surround);
        assertEquals(290, p.vitals().air());
        assertEquals(before, p.random().rawState(), "산소 보너스 0 이면 난수를 쓰지 않는다(단락 평가)");
    }

    @Test
    void soulSpeedAddsModifiersOnSoulBlockAndKeepsThemWhileAirborne() {
        // 영혼 블록 위 틱마다 4% 로 부츠가 닳는데, 첫 틱 마지막 계산에서 닳으면 다음 틱에 장비 변경으로 효과를 다시 계산해
        // 공중 유지를 못 본다(바닐라도 같음). 레벨 난수를 첫 두 nextFloat 이 0.04 이상인 시드로 고정한다.
        var level = seededLevel(1L);
        var p = player();
        p.inventory().set(8, enchanted("minecraft:diamond_boots", "minecraft:soul_speed", 2));
        // 영혼 블록 위: 속도 +0.0405 + 0.0105 = 0.051, 이동 효율 +1.0
        PlayerLocationEffects.tick(p, level, () -> true);
        assertTrue(p.locationEffects.soulSpeedActive());
        assertTrue(p.locationEffects.soulEfficiencyActive());
        assertEquals(0.1F + 0.0405F + 0.0105F, attr(p, Attribute.MOVEMENT_SPEED), 1e-6);
        assertEquals(1.0, attr(p, Attribute.MOVEMENT_EFFICIENCY));
        assertTrue(p.attributes().get(Attribute.MOVEMENT_SPEED).hasModifier("minecraft:enchantment.soul_speed/feet"));
        assertEquals(attr(p, Attribute.MOVEMENT_SPEED), PlayerAttributes.value(p, Attribute.MOVEMENT_SPEED), 1e-9);

        // 점프로 다른 칸의 공중: 영혼 블록이 아니어도 활성 속도는 유지, 이동 효율은 꺼진다
        p.applyMove(1.5, -58, 0.5, 0, 0, false);
        PlayerLocationEffects.tick(p, level, () -> false);
        assertTrue(p.locationEffects.soulSpeedActive());
        assertFalse(p.locationEffects.soulEfficiencyActive());
        assertEquals(0.0, attr(p, Attribute.MOVEMENT_EFFICIENCY));

        // 영혼 블록이 아닌 곳에 착지하면 속도도 꺼진다
        p.applyMove(2.5, -60, 0.5, 0, 0, true);
        PlayerLocationEffects.tick(p, level, () -> false);
        assertFalse(p.locationEffects.soulSpeedActive());
        assertEquals(0.1F, attr(p, Attribute.MOVEMENT_SPEED), 1e-9);
        assertFalse(p.attributes().get(Attribute.MOVEMENT_SPEED).hasModifier("minecraft:enchantment.soul_speed/feet"));
    }

    @Test
    void soulSpeedNeverActivatesWhileFlyingOrWithoutEnchant() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player();
        PlayerLocationEffects.tick(p, level, () -> true);
        assertFalse(p.locationEffects.soulSpeedActive(), "인챈트 없음");
        p.inventory().set(8, enchanted("minecraft:diamond_boots", "minecraft:soul_speed", 1));
        p.setFallFlying(true);
        PlayerLocationEffects.tick(p, level, () -> true);
        assertFalse(p.locationEffects.soulSpeedActive(), "겉날개 비행 중");
        assertTrue(p.locationEffects.soulEfficiencyActive(), "이동 효율은 비행과 무관(영혼 블록 위면 켜진다)");
    }

    @Test
    void soulSpeedDamagesBootsAboutFourPercentOfLocationChanges() {
        var level = seededLevel(4242L);
        var p = player();
        p.inventory().set(8, enchanted("minecraft:netherite_boots", "minecraft:soul_speed", 1));
        int moves = 1500;
        for (int i = 0; i < moves; i++) {
            p.applyMove(0.5 + (i % 2 == 0 ? 1 : 0), -60, 0.5, 0, 0, true);   // 칸을 오가며 매번 위치 변화
            PlayerLocationEffects.tick(p, level, () -> true);
        }
        int dmg = ItemTools.damage(p.inventory().get(8));
        // 시드 고정: 같은 난수열을 따로 굴려 센 값과 정확히 같아야 한다(칸 변화마다 1회 + 내구가 바뀐 다음 틱의 장비 갱신 재실행 1회)
        var ref = new LegacyRandomSource(4242L);
        int expected = 0, seen = -1;   // seen: 마지막으로 본 발 장비 상태(처음엔 빈 장비라 첫 틱에 장비 갱신 재실행)
        for (int i = 0; i < moves; i++) {
            if (ref.nextFloat() < 0.04F) expected++;
            if (expected != seen) {
                seen = expected;
                if (ref.nextFloat() < 0.04F) expected++;
            }
        }
        assertEquals(expected, dmg);
        assertTrue(dmg > 0);
        // 영혼 블록이 아니면 난수는 굴리되 깎지 않는다
        int before = dmg;
        for (int i = 0; i < 300; i++) {
            p.applyMove(0.5 + (i % 2 == 0 ? 1 : 0), -60, 0.5, 0, 0, true);
            PlayerLocationEffects.tick(p, level, () -> false);
        }
        assertEquals(before, ItemTools.damage(p.inventory().get(8)));
    }

    @Test
    void frostWalkerFreezesFiveByFiveDiskOfWaterSourcesUnderFeetOnly() {
        var level = new BlockPlacementTest.TestLevel();
        int water = B.defaultState("minecraft:water"), air = B.defaultState("minecraft:air");
        // 발 아래 y=-61 에 물 원천 9×9, 위(y=-60)는 공기. 플레이어는 (0.5, -60, 0.5) = 칸 (0,-60,0).
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlock(new Pos(x, -61, z), water, 2, 0);
            level.setBlock(new Pos(x, -60, z), air, 2, 0);
        }
        var p = player();
        p.inventory().set(8, enchanted("minecraft:diamond_boots", "minecraft:frost_walker", 1));
        PlayerLocationEffects.tick(p, level, () -> false);
        int frosted = 0;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            boolean ice = B.is(level.getBlockState(new Pos(x, -61, z)), "minecraft:frosted_ice");
            // 반경 3(레벨 1): (x+0.5-0.5)² + (z+0.5-0.5)² < 9 → |x|,|z| ≤ 2 인 25칸(3² + 0 = 9 는 포함 안 됨)
            assertEquals(Math.abs(x) <= 2 && Math.abs(z) <= 2, ice, "(" + x + "," + z + ")");
            if (ice) frosted++;
        }
        assertEquals(25, frosted);
        assertEquals(0, B.prop("age").intValue(level.getBlockState(new Pos(0, -61, 0))), "age 0");
    }

    @Test
    void frostWalkerNeedsGroundAndWaterSourceWithAirAbove() {
        var level = new BlockPlacementTest.TestLevel();
        int water = B.defaultState("minecraft:water");
        level.setBlock(new Pos(0, -61, 0), water, 2, 0);
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:air"), 2, 0);
        var p = player();
        p.inventory().set(8, enchanted("minecraft:diamond_boots", "minecraft:frost_walker", 2));
        p.applyMove(0.5, -60, 0.5, 0, 0, false);   // 공중: 지면 조건 실패
        PlayerLocationEffects.tick(p, level, () -> false);
        assertTrue(B.is(level.getBlockState(new Pos(0, -61, 0)), "minecraft:water"));
    }

    @Test
    void frostedIceMeltsStepwiseToWaterUnderSeededRandom() {
        var level = seededLevel(99L);
        Pos pos = new Pos(0, -61, 0);
        level.setBlock(pos, B.defaultState("minecraft:frosted_ice"), 2, 0);
        int maxAge = 0, ticks = 0;
        while (B.is(level.getBlockState(pos), "minecraft:frosted_ice") && ticks < 2000) {
            int st = level.getBlockState(pos);
            maxAge = Math.max(maxAge, B.prop("age").intValue(st));
            B.behavior(st).tick(st, level, pos, level.random());
            ticks++;
        }
        assertEquals(3, maxAge, "age 0→3 을 거쳐");
        assertTrue(B.is(level.getBlockState(pos), "minecraft:water"), "age 3 에서 한 번 더 녹으면 물");
        assertTrue(ticks < 2000);
    }
}
