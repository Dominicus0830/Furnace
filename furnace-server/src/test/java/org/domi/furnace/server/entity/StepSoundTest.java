// M15: 블록 SoundType 걸음 소리(엔티티 기본·몹별 덮어쓰기), 자수정 울림, 헤엄 소리, 플레이어 걸음·조합 블록·hit_ground.
// 기대값은 바닐라 1.21.1 바이트코드(Entity.playStepSound/walkingStepSound/playAmethystStepSound, Player.playStepSound, 각 몹 playStepSound, dnm=SoundType 표)를 읽은 것이다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockSounds;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StepSoundTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Pos FLOOR = new Pos(0, -61, 0);

    private static ParticleSoundEventsTest.RecLevel level() { return new ParticleSoundEventsTest.RecLevel(7); }

    private static <T extends Entity> T spawn(ParticleSoundEventsTest.RecLevel level, String id) {
        @SuppressWarnings("unchecked") T e = (T) Entities.create(level, id);
        e.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(e);
        level.sounds.clear();
        return e;
    }

    private static int stone() { return B.defaultState("minecraft:stone"); }

    // ------------------------------------------------------------------ 블록 SoundType 표

    @Test
    void blockSoundTable() {
        var s = BlockSounds.get();
        assertEquals("minecraft:block.stone.step", s.of(stone()).stepSound());
        assertEquals("minecraft:block.grass.step", s.of(B.defaultState("minecraft:grass_block")).stepSound());
        assertEquals("minecraft:block.wood.step", s.of(B.defaultState("minecraft:oak_planks")).stepSound());
        assertEquals("minecraft:block.wood.step", s.of(B.defaultState("minecraft:oak_stairs")).stepSound());
        // 문·버튼·압력판·울타리 문·표지판은 BlockSetType/WoodType 소리를 properties.sound 로 덮는다
        assertEquals("minecraft:block.wood.step", s.of(B.defaultState("minecraft:oak_button")).stepSound());
        assertEquals("minecraft:block.stone.step", s.of(B.defaultState("minecraft:stone_button")).stepSound());
        assertEquals("minecraft:block.nether_wood.step", s.of(B.defaultState("minecraft:crimson_door")).stepSound());
        assertEquals("minecraft:block.copper.step", s.of(B.defaultState("minecraft:copper_door")).stepSound());
        assertEquals("minecraft:block.metal.step", s.of(B.defaultState("minecraft:iron_door")).stepSound());
        assertEquals("minecraft:block.bamboo_wood.step", s.of(B.defaultState("minecraft:bamboo_sign")).stepSound());
        assertEquals("minecraft:block.hanging_sign.step", s.of(B.defaultState("minecraft:oak_hanging_sign")).stepSound());
        assertEquals("minecraft:block.cherry_wood_hanging_sign.step", s.of(B.defaultState("minecraft:cherry_hanging_sign")).stepSound());
        // 복사(ofFullCopy)·도우미 메서드
        assertEquals("minecraft:block.cherry_wood.step", s.of(B.defaultState("minecraft:cherry_log")).stepSound());
        assertEquals("minecraft:block.candle.step", s.of(B.defaultState("minecraft:candle")).stepSound());
        assertEquals("minecraft:block.wood.step", s.of(B.defaultState("minecraft:melon")).stepSound());
        // 크기·높이가 1 이 아닌 종류
        var egg = s.of(B.defaultState("minecraft:sniffer_egg"));
        assertEquals("minecraft:block.metal.step", egg.stepSound());
        assertEquals(1.5F, egg.pitch());
        var bell = s.of(B.defaultState("minecraft:bell"));
        assertEquals("minecraft:block.anvil.step", bell.stepSound());
        assertEquals(0.3F, bell.volume());
        // 블록마다 표에 있다
        var blocks = org.domi.furnace.server.block.Blocks.get();
        for (int b = 0; b < blocks.blockCount(); b++) assertNotNull(s.ofBlock(b), blocks.name(b));
    }

    // ------------------------------------------------------------------ Entity 기본 걸음 소리

    @Test
    void defaultStepUsesBlockSoundTypeAtQuarterVolume() {
        var level = level();
        Creeper c = spawn(level, "minecraft:creeper");
        c.walkingStepSound(FLOOR, stone());
        assertEquals(1, level.sounds.size());
        var s = level.sounds.get(0);
        assertEquals("minecraft:block.stone.step", s.sound());
        assertEquals(0.15F, s.volume());
        assertEquals(1.0F, s.pitch());
        level.sounds.clear();
        c.walkingStepSound(FLOOR, B.defaultState("minecraft:sniffer_egg"));
        assertEquals(0.15F, level.sounds.get(0).volume());
        assertEquals(1.5F, level.sounds.get(0).pitch());
        level.sounds.clear();
        c.walkingStepSound(FLOOR, B.defaultState("minecraft:bell"));
        assertEquals(0.3F * 0.15F, level.sounds.get(0).volume());
    }

    @Test
    void amethystClusterChimesOncePerTwentyTicks() {
        var level = level();
        Creeper c = spawn(level, "minecraft:creeper");
        c.tickCount = 100;
        int amethyst = B.defaultState("minecraft:amethyst_block");
        c.walkingStepSound(FLOOR, amethyst);
        assertEquals(2, level.sounds.size());
        assertEquals("minecraft:block.amethyst_block.step", level.sounds.get(0).sound());
        var chime = level.sounds.get(1);
        assertEquals("minecraft:block.amethyst_block.chime", chime.sound());
        // 세기 0 → 0.07 (식는 항은 0 에 곱해 0), 크기 0.1 + 0.07·1.2, 높이 0.5 + 0.07·nextFloat·1.2
        float intensity = 0.07F;
        assertEquals(0.1F + intensity * 1.2F, chime.volume());
        assertTrue(chime.pitch() >= 0.5F && chime.pitch() <= 0.5F + intensity * 1.2F + 1e-6F, "" + chime.pitch());
        // 20틱이 안 지나면 걸음만
        level.sounds.clear();
        c.tickCount = 119;
        c.walkingStepSound(FLOOR, amethyst);
        assertEquals(1, level.sounds.size());
        // 20틱 뒤 다시 울린다, 세기는 0.997^20 로 식은 값에 0.07 이 더해진다
        level.sounds.clear();
        c.tickCount = 120;
        c.walkingStepSound(FLOOR, amethyst);
        assertEquals(2, level.sounds.size());
        float cooled = intensity * (float) Math.pow(0.997, 20);
        assertEquals(0.1F + Math.min(1.0F, cooled + 0.07F) * 1.2F, level.sounds.get(1).volume(), 1e-6F);
    }

    // ------------------------------------------------------------------ 몹별 걸음 소리

    private static void assertStep(ParticleSoundEventsTest.RecLevel level, String sound, float volume, float pitch) {
        assertEquals(1, level.sounds.size(), level.sounds.toString());
        var s = level.sounds.get(0);
        assertEquals(sound, s.sound());
        assertEquals(volume, s.volume());
        assertEquals(pitch, s.pitch());
    }

    @Test
    void mobSpecificSteps() {
        String[][] simple = {
                {"minecraft:chicken", "entity.chicken.step"}, {"minecraft:cow", "entity.cow.step"}, {"minecraft:mooshroom", "entity.cow.step"},
                {"minecraft:panda", "entity.panda.step"}, {"minecraft:parrot", "entity.parrot.step"}, {"minecraft:pig", "entity.pig.step"},
                {"minecraft:polar_bear", "entity.polar_bear.step"}, {"minecraft:sheep", "entity.sheep.step"}, {"minecraft:wolf", "entity.wolf.step"},
                {"minecraft:armadillo", "entity.armadillo.step"}, {"minecraft:frog", "entity.frog.step"}, {"minecraft:goat", "entity.goat.step"},
                {"minecraft:llama", "entity.llama.step"}, {"minecraft:trader_llama", "entity.llama.step"}, {"minecraft:sniffer", "entity.sniffer.step"},
                {"minecraft:endermite", "entity.endermite.step"}, {"minecraft:ravager", "entity.ravager.step"},
                {"minecraft:silverfish", "entity.silverfish.step"}, {"minecraft:spider", "entity.spider.step"}, {"minecraft:cave_spider", "entity.spider.step"},
                {"minecraft:zoglin", "entity.zoglin.step"}, {"minecraft:hoglin", "entity.hoglin.step"}, {"minecraft:piglin", "entity.piglin.step"},
                {"minecraft:piglin_brute", "entity.piglin_brute.step"}};
        for (String[] m : simple) {
            var level = level();
            Entity e = spawn(level, m[0]);
            e.walkingStepSound(FLOOR, stone());
            assertStep(level, "minecraft:" + m[1], 0.15F, 1.0F);
        }
    }

    @Test
    void ironGolemStepIsFullVolume() {
        var level = level();
        Entity e = spawn(level, "minecraft:iron_golem");
        e.walkingStepSound(FLOOR, stone());
        assertStep(level, "minecraft:entity.iron_golem.step", 1.0F, 1.0F);
    }

    @Test
    void zombieFamilySteps() {
        String[][] z = {{"minecraft:zombie", "entity.zombie.step"}, {"minecraft:husk", "entity.husk.step"}, {"minecraft:drowned", "entity.drowned.step"},
                {"minecraft:zombie_villager", "entity.zombie_villager.step"}, {"minecraft:zombified_piglin", "entity.zombie.step"}};
        for (String[] m : z) {
            var level = level();
            Entity e = spawn(level, m[0]);
            e.walkingStepSound(FLOOR, stone());
            assertStep(level, "minecraft:" + m[1], 0.15F, 1.0F);
        }
    }

    @Test
    void silentSteppers() {
        for (String id : new String[] {"minecraft:bee", "minecraft:allay", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish"}) {
            var level = level();
            Entity e = spawn(level, id);
            e.walkingStepSound(FLOOR, stone());
            assertTrue(level.sounds.isEmpty(), id + " " + level.sounds);
        }
    }

    @Test
    void turtleStepDependsOnAgeAndWalksSlowerBetweenSteps() {
        var level = level();
        Turtle t = spawn(level, "minecraft:turtle");
        t.walkingStepSound(FLOOR, stone());
        assertStep(level, "minecraft:entity.turtle.shamble", 0.15F, 1.0F);
        t.moveDist = 3.0F;
        assertEquals(3.15F, t.nextStep());
        level.sounds.clear();
        t.setAge(-24000);
        t.walkingStepSound(FLOOR, stone());
        assertStep(level, "minecraft:entity.turtle.shamble_baby", 0.15F, 1.0F);
    }

    @Test
    void striderStepDependsOnLava() {
        var level = level();
        Strider s = spawn(level, "minecraft:strider");
        s.walkingStepSound(FLOOR, stone());
        assertStep(level, "minecraft:entity.strider.step", 1.0F, 1.0F);
    }

    @Test
    void camelStepOnSandUsesSandSound() {
        var level = level();
        Camel c = spawn(level, "minecraft:camel");
        c.walkingStepSound(FLOOR, B.defaultState("minecraft:sand"));
        assertStep(level, "minecraft:entity.camel.step_sand", 1.0F, 1.0F);
        level.sounds.clear();
        c.walkingStepSound(FLOOR, stone());
        assertStep(level, "minecraft:entity.camel.step", 1.0F, 1.0F);
    }

    @Test
    void horseStepWoodVsNormalAndSnow() {
        var level = level();
        Horse h = spawn(level, "minecraft:horse");
        h.walkingStepSound(FLOOR, stone());
        assertStep(level, "minecraft:entity.horse.step", 0.15F, 1.0F);
        level.sounds.clear();
        h.walkingStepSound(FLOOR, B.defaultState("minecraft:oak_planks"));
        assertStep(level, "minecraft:entity.horse.step_wood", 0.15F, 1.0F);
        // 액체 위에서는 소리 없음
        level.sounds.clear();
        h.walkingStepSound(FLOOR, B.defaultState("minecraft:water"));
        assertTrue(level.sounds.isEmpty());
        // 위 칸이 눈이면 눈 소리(snow 블록 SoundType, 나무 아님)
        level.setBlock(FLOOR.above(), B.defaultState("minecraft:snow"), 2);
        level.sounds.clear();
        h.walkingStepSound(FLOOR, B.defaultState("minecraft:oak_planks"));
        assertStep(level, "minecraft:entity.horse.step", 0.15F, 1.0F);
    }

    @Test
    void ridddenHorseGallopsEveryThirdStepAfterFive() {
        var level = level();
        Horse h = spawn(level, "minecraft:horse");
        Zombie rider = spawn(level, "minecraft:zombie");
        assertTrue(rider.startRiding(h));
        level.sounds.clear();
        java.util.List<String> got = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            level.sounds.clear();
            h.walkingStepSound(FLOOR, stone());
            got.add(level.sounds.isEmpty() ? "-" : level.sounds.get(0).sound().substring("minecraft:entity.horse.".length()));
        }
        // 1~5번째 step_wood, 6번째 카운터 6(%3==0) gallop, 7·8 없음, 9 gallop, 10·11 없음, 12 gallop
        assertEquals(java.util.List.of("step_wood", "step_wood", "step_wood", "step_wood", "step_wood", "gallop", "-", "-", "gallop", "-", "-", "gallop"), got);
    }

    @Test
    void donkeyGallopSometimesBreathes() {
        var level = level();
        Donkey d = spawn(level, "minecraft:donkey");
        var type = BlockSounds.get().of(stone());
        int breathes = 0;
        for (int i = 0; i < 200; i++) {
            level.sounds.clear();
            d.playGallopSound(type);
            assertEquals("minecraft:entity.horse.gallop", level.sounds.get(0).sound());
            if (level.sounds.size() == 2) {
                breathes++;
                assertEquals("minecraft:entity.horse.breathe", level.sounds.get(1).sound());
                assertEquals(0.6F, level.sounds.get(1).volume());
            }
        }
        assertTrue(breathes > 5 && breathes < 50, "10분의 1 확률: " + breathes);
    }

    @Test
    void wardenNextStepAndStriderNextStep() {
        var level = level();
        Warden w = spawn(level, "minecraft:warden");
        w.moveDist = 2.0F;
        assertEquals(2.55F, w.nextStep());
        Strider s = spawn(level, "minecraft:strider");
        s.moveDist = 2.0F;
        assertEquals(2.6F, s.nextStep());
    }

    // ------------------------------------------------------------------ 헤엄 소리

    @Test
    void swimSounds() {
        var level = level();
        assertEquals("minecraft:entity.generic.swim", ((Entity) spawn(level, "minecraft:cow")).getSwimSound());
        assertEquals("minecraft:entity.hostile.swim", ((Entity) spawn(level, "minecraft:creeper")).getSwimSound());
        assertEquals("minecraft:entity.hostile.swim", ((Entity) spawn(level, "minecraft:hoglin")).getSwimSound());
        assertEquals("minecraft:entity.drowned.swim", ((Entity) spawn(level, "minecraft:drowned")).getSwimSound());
        assertEquals("minecraft:entity.fish.swim", ((Entity) spawn(level, "minecraft:cod")).getSwimSound());
        assertEquals("minecraft:entity.dolphin.swim", ((Entity) spawn(level, "minecraft:dolphin")).getSwimSound());
        assertEquals("minecraft:entity.turtle.swim", ((Entity) spawn(level, "minecraft:turtle")).getSwimSound());
        assertEquals("minecraft:entity.axolotl.swim", ((Entity) spawn(level, "minecraft:axolotl")).getSwimSound());
        assertEquals("minecraft:entity.hostile.splash", ((Entity) spawn(level, "minecraft:zombie")).swimSplashSound());
        SkeletonHorse sh = spawn(level, "minecraft:skeleton_horse");
        assertEquals("minecraft:entity.skeleton_horse.swim", sh.getSwimSound());   // 땅에서 떴을 때
    }

    // ------------------------------------------------------------------ 플레이어

    private static Player player(ParticleSoundEventsTest.RecLevel level) {
        var p = new Player(5, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.currentLevel = level;
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    @Test
    void playerStepSoundsGoToOthersWithPlayersSource() {
        var level = level();
        var p = player(level);
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 2.0, 0, 0, true, false);
        assertEquals(1, level.sounds.size(), level.sounds.toString());
        var s = level.sounds.get(0);
        assertEquals("players", s.source());
        assertEquals(0.15F, s.volume());
        assertEquals(1.0F, s.pitch());
        assertEquals(0.5, s.x());
        assertEquals(-60.0, s.y());
        assertTrue(s.sound().startsWith("minecraft:block."), s.sound());
        // 같은 걸음 안에서 또 부르지 않는다(moveDist ≤ nextStep)
        level.sounds.clear();
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 0.1, 0, 0, true, false);
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void playerSneakingOnGroundMakesNoSound() {
        var level = level();
        var p = player(level);
        p.sneaking(true);
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 2.0, 0, 0, true, false);
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void playerOnCombinationBlockPlaysBothSounds() {
        var level = level();
        var p = player(level);
        level.setBlock(FLOOR.above(), B.defaultState("minecraft:moss_carpet"), 2);
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 2.0, 0, 0, true, false);
        assertEquals(2, level.sounds.size(), level.sounds.toString());
        assertEquals("minecraft:block.moss_carpet.step", level.sounds.get(0).sound());
        assertEquals(0.15F, level.sounds.get(0).volume());
        var floor = BlockSounds.get().of(level.getBlockState(FLOOR));
        assertEquals(floor.stepSound(), level.sounds.get(1).sound());
        assertEquals(floor.volume() * 0.05F, level.sounds.get(1).volume());
        assertEquals(floor.pitch() * 0.8F, level.sounds.get(1).pitch());
    }

    @Test
    void playerInWaterSwimSoundThenMuffledStep() {
        var level = level();
        var p = player(level);
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 2.0, 0, 0, true, true);
        assertEquals(2, level.sounds.size(), level.sounds.toString());
        assertEquals("minecraft:entity.player.swim", level.sounds.get(0).sound());
        assertTrue(level.sounds.get(0).volume() >= 0.0F && level.sounds.get(0).volume() <= 1.0F);
        var floor = BlockSounds.get().of(level.getBlockState(FLOOR));
        assertEquals(floor.stepSound(), level.sounds.get(1).sound());
        assertEquals(floor.volume() * 0.05F, level.sounds.get(1).volume());
    }

    @Test
    void playerSwimmingMakesNoStepSoundOrEvent() {
        var level = level();
        var p = player(level);
        p.swimming = true;
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 2.0, 0, 0, true, false);
        assertTrue(level.sounds.isEmpty());
        assertFalse(level.events.contains(GameEvent.STEP));
    }

    @Test
    void playerAmethystChime() {
        var level = level();
        var p = player(level);
        level.setBlock(FLOOR, B.defaultState("minecraft:amethyst_block"), 2);
        for (int i = 0; i < 25; i++) p.combatTick();
        p.movementEmission.onMove(level, p, 0.5, -60, 0.5, 2.0, 0, 0, true, false);
        assertEquals(2, level.sounds.size(), level.sounds.toString());
        assertEquals("minecraft:block.amethyst_block.step", level.sounds.get(0).sound());
        assertEquals("minecraft:block.amethyst_block.chime", level.sounds.get(1).sound());
        assertEquals(0.1F + 0.07F * 1.2F, level.sounds.get(1).volume());
    }
}
