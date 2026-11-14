// 엔티티가 닿거나 밟거나 떨어질 때의 블록 훅(entityInside·stepOn·fallOn): 큰 흘림잎 기울기, 석순 낙하 피해, 파괴수 작물 짓밟기,
// 달콤한 열매 덤불, 수련잎·보트, 개구리알·떨어지는 블록, 레드스톤 광석, 거북 알, 화살 버튼과 그 플레이어 쪽.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BigDripleafBlock;
import org.domi.furnace.server.block.types.SweetBerryBushBlock;
import org.domi.furnace.server.block.types.TurtleEggBlock;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityBlockHookTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** mobGriefing 을 바꿀 수 있는 시험 레벨. */
    static final class Level extends BlockPlacementTest.TestLevel {
        boolean griefing = true;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public boolean gameRule(String name) { return name.equals("mobGriefing") ? griefing : super.gameRule(name); }
            };
        }
    }

    private static final Pos AT = new Pos(3, -60, 3);

    private static int state(String name) { return B.defaultState(B.id("minecraft:" + name)); }

    private static int with(int state, String prop, String value) { return B.prop(prop).with(state, value); }

    private static Entity mob(Level level, String type, double x, double y, double z) {
        var e = (Entity) Entities.create(level, "minecraft:" + type);
        e.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    private static String tilt(Level level) { return B.prop("tilt").valueName(level.getBlockState(AT)); }

    // ------------------------------------------------------------------ 큰 흘림잎

    @Test
    void dripleafTiltsUnderGroundedEntityAboveLeaf() {
        var level = new Level();
        level.setBlock(AT, state("big_dripleaf"), 3);
        var cow = mob(level, "cow", AT.x() + 0.5, AT.y() + 0.9375, AT.z() + 0.5);
        cow.setOnGround(false);
        B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, cow);
        assertEquals("none", tilt(level), "공중이면 안 기운다");
        cow.setOnGround(true);
        cow.moveTo(AT.x() + 0.5, AT.y() + 0.6875, AT.z() + 0.5, 0, 0);
        B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, cow);
        assertEquals("none", tilt(level), "0.6875 이하(옆에서 스친 것)는 안 기운다");
        cow.moveTo(AT.x() + 0.5, AT.y() + 0.9375, AT.z() + 0.5, 0, 0);
        B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, cow);
        assertEquals("unstable", tilt(level));
        assertTrue(level.hasScheduledTick(AT, B.id("minecraft:big_dripleaf")), "10틱 뒤 partial");
    }

    @Test
    void dripleafPlayerSideAndSignalBlocksIt() {
        var level = new Level();
        level.setBlock(AT, state("big_dripleaf"), 3);
        level.setBlock(AT.east(), state("redstone_block"), 3);
        var leaf = (BigDripleafBlock) B.behavior(level.getBlockState(AT));
        leaf.entityInsideAt(level.getBlockState(AT), level, AT, true, AT.y() + 0.9375, null);
        assertEquals("none", tilt(level), "신호를 받으면 안 기운다");
        level.setBlock(AT.east(), state("air"), 3);
        leaf.entityInsideAt(level.getBlockState(AT), level, AT, true, AT.y() + 0.9375, null);
        assertEquals("unstable", tilt(level));
    }

    // ------------------------------------------------------------------ 석순

    @Test
    void stalagmiteTipDoublesFallDamage() {
        var level = new Level();
        int tip = with(with(state("pointed_dripstone"), "vertical_direction", "up"), "thickness", "tip");
        var cow = (LivingEntity) mob(level, "cow", 0.5, -60, 0.5);
        B.behavior(tip).fallOn(level, tip, AT, cow, 5.0F);
        assertEquals(10.0F - 8.0F, cow.health(), 1e-4, "ceil((5 + 2 - 3) × 2) = 8");
        assertTrue(String.valueOf(cow.getLastDamageType()).endsWith("stalagmite"), cow.getLastDamageType());

        var cow2 = (LivingEntity) mob(level, "cow", 1.5, -60, 1.5);
        int frustum = with(tip, "thickness", "frustum");
        B.behavior(frustum).fallOn(level, frustum, AT, cow2, 5.0F);
        assertEquals(10.0F - 2.0F, cow2.health(), 1e-4, "끝이 아니면 보통 낙하 피해");
    }

    @Test
    void playerLandingOnStalagmiteTip() {
        var p = new Player(7, UUID.randomUUID(), "Steve", new io.netty.channel.embedded.EmbeddedChannel());
        var v = p.vitals();
        v.env(new PlayerVitalsTest.TestEnv());
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
        var dry = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
        for (int i = 0; i < 61; i++) v.tick(dry);
        v.onMove(0, -5.0, 0, false, false, false, false, false, false, null, "minecraft:air", false);
        v.landingStalagmite(true);
        v.onMove(0, -0.01, 0, false, true, false, false, false, false, null, "minecraft:pointed_dripstone", false);
        assertTrue(v.landedOnLastMove());
        assertEquals(20.0F - 8.0F, v.health(), 1e-4, "ceil((5 + 2 - 3) × 2)");
    }

    // ------------------------------------------------------------------ 작물

    @Test
    void ravagerTramplesCropsWhenGriefing() {
        for (String crop : new String[] {"wheat", "pitcher_crop"}) {
            var level = new Level();
            level.setBlock(AT.below(), state("farmland"), 3);
            level.setBlock(AT, state(crop), 3);
            var cow = mob(level, "cow", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
            B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, cow);
            assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:" + crop)), "파괴수만");
            var ravager = mob(level, "ravager", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
            level.griefing = false;
            B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, ravager);
            assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:" + crop)), "mobGriefing 꺼짐");
            level.griefing = true;
            B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, ravager);
            assertTrue(B.isAir(level.getBlockState(AT)), crop);
        }
    }

    // ------------------------------------------------------------------ 달콤한 열매 덤불

    @Test
    void sweetBerryBushSlowsAndHurtsMovingMobs() {
        var level = new Level();
        int bush = B.prop("age").withInt(state("sweet_berry_bush"), 1);
        var cow = (LivingEntity) mob(level, "cow", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
        cow.fallDistance = 3.0F;
        B.behavior(bush).entityInside(bush, level, AT, cow);
        assertEquals(0.0F, cow.fallDistance, "끼이면 낙하 거리 초기화");
        assertEquals(10.0F, cow.health(), "가만히 있으면 피해 없음");
        cow.xOld = cow.x() - 0.002;
        B.behavior(bush).entityInside(bush, level, AT, cow);
        assertEquals(10.0F, cow.health(), "0.003 미만");
        cow.xOld = cow.x() - 0.01;
        B.behavior(bush).entityInside(bush, level, AT, cow);
        assertEquals(9.0F, cow.health());

        int young = state("sweet_berry_bush");
        var cow2 = (LivingEntity) mob(level, "cow", 0.5, -60, 0.5);
        cow2.xOld = cow2.x() - 0.5;
        B.behavior(young).entityInside(young, level, AT, cow2);
        assertEquals(10.0F, cow2.health(), "나이 0 은 찌르지 않는다");

        var fox = (LivingEntity) mob(level, "fox", 1.5, -60, 1.5);
        fox.fallDistance = 3.0F;
        fox.xOld = fox.x() - 0.5;
        float hp = fox.health();
        B.behavior(bush).entityInside(bush, level, AT, fox);
        assertEquals(3.0F, fox.fallDistance, "여우는 안 끼인다");
        assertEquals(hp, fox.health());

        assertTrue(SweetBerryBushBlock.hurtsPlayer(bush, 0.0, 0.004));
        assertFalse(SweetBerryBushBlock.hurtsPlayer(bush, 0.001, 0.002));
        assertFalse(SweetBerryBushBlock.hurtsPlayer(young, 0.5, 0.0));
    }

    // ------------------------------------------------------------------ 수련잎·개구리알

    @Test
    void boatBreaksLilyPadAndFallingBlockBreaksFrogspawn() {
        var level = new Level();
        level.setBlock(AT, state("lily_pad"), 3);
        var cow = mob(level, "cow", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
        B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, cow);
        assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:lily_pad")));
        var boat = mob(level, "boat", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
        B.behavior(level.getBlockState(AT)).entityInside(level.getBlockState(AT), level, AT, boat);
        assertTrue(B.isAir(level.getBlockState(AT)));

        Pos spawn = new Pos(AT.x() + 2, AT.y(), AT.z());
        level.setBlock(spawn, state("frogspawn"), 3);
        B.behavior(level.getBlockState(spawn)).entityInside(level.getBlockState(spawn), level, spawn, cow);
        assertTrue(B.is(level.getBlockState(spawn), B.id("minecraft:frogspawn")));
        var sand = new FallingBlockEntity(level, level.nextEntityId());
        sand.moveTo(spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5, 0, 0);
        B.behavior(level.getBlockState(spawn)).entityInside(level.getBlockState(spawn), level, spawn, sand);
        assertTrue(B.isAir(level.getBlockState(spawn)));
    }

    // ------------------------------------------------------------------ 레드스톤 광석·거북 알

    @Test
    void steppingOnRedstoneOreLightsIt() {
        var level = new Level();
        level.setBlock(AT, state("redstone_ore"), 3);
        var cow = mob(level, "cow", AT.x() + 0.5, AT.y() + 1, AT.z() + 0.5);
        B.behavior(level.getBlockState(AT)).stepOn(level, AT, level.getBlockState(AT), cow);
        assertTrue(B.prop("lit").is(level.getBlockState(AT)));
    }

    @Test
    void turtleEggsBreakUnderMobsButNotTurtles() {
        var level = new Level();
        int eggs = B.prop("eggs").withInt(state("turtle_egg"), 4);
        level.setBlock(AT, eggs, 3);
        var turtle = mob(level, "turtle", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
        var zombie = mob(level, "zombie", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
        for (int i = 0; i < 300; i++) {
            B.behavior(level.getBlockState(AT)).stepOn(level, AT, level.getBlockState(AT), turtle);
            B.behavior(level.getBlockState(AT)).fallOn(level, level.getBlockState(AT), AT, turtle, 0.5F);
            B.behavior(level.getBlockState(AT)).fallOn(level, level.getBlockState(AT), AT, zombie, 0.5F);
        }
        assertEquals(4, B.prop("eggs").intValue(level.getBlockState(AT)), "거북은 못 깨고 좀비는 떨어져도 안 깬다");

        var cow = mob(level, "cow", AT.x() + 0.5, AT.y(), AT.z() + 0.5);
        level.griefing = false;
        for (int i = 0; i < 300; i++) B.behavior(level.getBlockState(AT)).fallOn(level, level.getBlockState(AT), AT, cow, 0.5F);
        assertEquals(4, B.prop("eggs").intValue(level.getBlockState(AT)), "mobGriefing 꺼짐");
        level.griefing = true;
        for (int i = 0; i < 300 && B.prop("eggs").intValue(level.getBlockState(AT)) == 4; i++) {
            B.behavior(level.getBlockState(AT)).fallOn(level, level.getBlockState(AT), AT, cow, 0.5F);
        }
        assertEquals(3, B.prop("eggs").intValue(level.getBlockState(AT)), "1/3 로 하나 깨진다");

        // 플레이어: mobGriefing 과 상관없이 깬다, 웅크리면 밟아도 안 깬다
        level.griefing = false;
        var egg = (TurtleEggBlock) B.behavior(eggs);
        for (int i = 0; i < 1000; i++) egg.stepOnPlayer(level, AT, level.getBlockState(AT), true);
        assertEquals(3, B.prop("eggs").intValue(level.getBlockState(AT)));
        for (int i = 0; i < 2000 && B.prop("eggs").intValue(level.getBlockState(AT)) == 3; i++) egg.stepOnPlayer(level, AT, level.getBlockState(AT), false);
        assertEquals(2, B.prop("eggs").intValue(level.getBlockState(AT)));
        for (int i = 0; i < 300 && B.is(level.getBlockState(AT), B.id("minecraft:turtle_egg")); i++) egg.fallOnPlayer(level, AT, level.getBlockState(AT));
        assertTrue(B.isAir(level.getBlockState(AT)), "마지막 알이 깨지면 블록이 없어진다");
    }

    // ------------------------------------------------------------------ 화살 버튼

    @Test
    void arrowPressesWoodenButtonOnly() {
        var level = new Level();
        level.setBlock(AT.below(), state("stone"), 3);
        int wood = with(state("oak_button"), "face", "floor");
        level.setBlock(AT, wood, 3);
        var arrow = mob(level, "arrow", AT.x() + 0.5, AT.y() + 0.05, AT.z() + 0.5);
        B.behavior(wood).entityInside(level.getBlockState(AT), level, AT, arrow);
        assertTrue(B.prop("powered").is(level.getBlockState(AT)), "나무 버튼은 화살에 눌린다");
        assertTrue(level.hasScheduledTick(AT, B.id("minecraft:oak_button")));
        // 화살이 있는 동안 예약 틱은 켜 둔다, 없어지면 끈다
        B.behavior(wood).tick(level.getBlockState(AT), level, AT, level.random());
        assertTrue(B.prop("powered").is(level.getBlockState(AT)));
        arrow.discard();
        B.behavior(wood).tick(level.getBlockState(AT), level, AT, level.random());
        assertFalse(B.prop("powered").is(level.getBlockState(AT)));

        Pos s = new Pos(AT.x() + 2, AT.y(), AT.z());
        level.setBlock(s.below(), state("stone"), 3);
        int stone = with(state("stone_button"), "face", "floor");
        level.setBlock(s, stone, 3);
        var arrow2 = mob(level, "arrow", s.x() + 0.5, s.y() + 0.05, s.z() + 0.5);
        B.behavior(stone).entityInside(level.getBlockState(s), level, s, arrow2);
        assertFalse(B.prop("powered").is(level.getBlockState(s)), "돌 버튼은 화살에 안 눌린다");
    }
}
