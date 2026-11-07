// M1: 영혼 가속 tick 효과(입자·소리), 착지 블록 입자, 낙타 질주 진동, 워든·해골 걸음 소리.
// 기대값은 바닐라 1.21.1 바이트코드(SpawnParticlesEffect·PlaySoundEffect·LivingEntity.checkFallDamage·Camel.handleStartJump)를 손으로 계산한 것이다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParticleSoundEventsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    record Particle(ParticleOptions options, boolean force, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {}

    record Sound(String sound, String source, double x, double y, double z, float volume, float pitch) {}

    /** 입자·소리·진동을 기록하는 시험용 레벨. 레벨 난수는 시드로 고정한다. */
    static final class RecLevel extends BlockPlacementTest.TestLevel {
        final List<Particle> particles = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();
        final List<GameEvent> events = new ArrayList<>();
        final LegacyRandomSource rnd;

        RecLevel(long seed) { rnd = new LegacyRandomSource(seed); }

        @Override public org.domi.furnace.server.block.RandomSource random() { return rnd; }

        @Override
        public int sendParticles(ParticleOptions p, boolean force, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
            particles.add(new Particle(p, force, x, y, z, count, dx, dy, dz, speed));
            return 1;
        }

        @Override
        public void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch) {
            sounds.add(new Sound(sound, source, x, y, z, volume, pitch));
        }

        @Override
        public void gameEvent(GameEvent event, Vec3 pos, org.domi.furnace.server.world.gameevent.GameEventContext ctx) {
            events.add(event);
            super.gameEvent(event, pos, ctx);
        }
    }

    private static ItemStack enchanted(String item, String enchant, int level) {
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", item), 1);
        return EnchantTools.update(s, m -> m.put(enchant, level));
    }

    private static Player player(RecLevel level) {
        var p = new Player(5, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.currentLevel = level;
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        p.applyMove(0.6, -60, 0.5, 0, 0, true);   // known movement = (0.1, 0, 0)
        return p;
    }

    /** 첫 nextFloat 이 기준 아래/이상인 시드. */
    private static long seedWhere(boolean below035) {
        for (long s = 1; ; s++) {
            if ((new LegacyRandomSource(s).nextFloat() < 0.35F) == below035) return s;
        }
    }

    // ------------------------------------------------------------------ 영혼 가속 tick 효과

    @Test
    void soulSpeedSpawnsSoulParticleAndSound() {
        var level = new RecLevel(seedWhere(true));
        var p = player(level);
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:soul_speed", 1));
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertEquals(1, level.particles.size());
        var pt = level.particles.get(0);
        assertEquals("minecraft:soul", pt.options().type());
        assertFalse(pt.force());
        assertEquals(0, pt.count(), "count 0 = 속도 입자");
        assertEquals(1.0, pt.speed());
        assertEquals(-0.02, pt.dx(), 1e-6);   // known movement × -0.2
        assertEquals(0.1, pt.dy(), 1e-6);
        assertEquals(0.0, pt.dz(), 1e-9);
        assertEquals(-59.9, pt.y(), 1e-6);    // 발 + 0.1
        assertTrue(Math.abs(pt.x() - 0.6) <= 0.3 && Math.abs(pt.z() - 0.5) <= 0.3, "몸 너비 0.6 안");
        assertEquals(1, level.sounds.size());
        var s = level.sounds.get(0);
        assertEquals("minecraft:particle.soul_escape", s.sound());
        assertEquals("player", s.source());
        assertEquals(0.6F, s.volume());
        assertTrue(s.pitch() >= 0.6F && s.pitch() < 1.0F);
    }

    @Test
    void soulSpeedSoundOnlyWithChanceButParticleAlways() {
        var level = new RecLevel(seedWhere(false));   // 첫 nextFloat ≥ 0.35 → 소리 없음
        var p = player(level);
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:soul_speed", 3));
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertEquals(1, level.particles.size());
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void soulSpeedNeedsGroundSoulBlockMovementAndFifthTick() {
        // 조건 하나라도 어긋나면 입자·소리 없음
        var level = new RecLevel(seedWhere(true));
        var p = player(level);
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:soul_speed", 1));
        PlayerLocationEffects.tickEffects(p, level, () -> false);   // 영혼 블록 위가 아님
        assertTrue(level.particles.isEmpty() && level.sounds.isEmpty());

        p.combatTick();   // tickCount % 5 != 0
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertTrue(level.particles.isEmpty() && level.sounds.isEmpty());
        for (int i = 0; i < 4; i++) p.combatTick();   // 다시 5의 배수
        assertEquals(5, p.combatTicks());
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertEquals(1, level.particles.size());

        // 멈춰 있으면(known movement 0) 없음
        level.particles.clear();
        p.applyMove(0.6, -60, 0.5, 0, 0, true);
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertTrue(level.particles.isEmpty());

        // 공중
        p.applyMove(0.7, -59, 0.5, 0, 0, false);
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertTrue(level.particles.isEmpty());
    }

    @Test
    void withoutSoulSpeedBootsNothingAndNoLevelRandomUsed() {
        var level = new RecLevel(7);
        var p = player(level);
        var probe = new LegacyRandomSource(7);
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        assertTrue(level.particles.isEmpty() && level.sounds.isEmpty());
        assertEquals(probe.nextFloat(), level.rnd.nextFloat(), "영혼 가속이 없으면 레벨 난수를 쓰지 않는다");
    }

    @Test
    void soulSpeedConsumesOneLevelRandomFloatEveryTick() {
        // random_chance 는 all_of 의 첫 항이라 입자 조건이 거짓이어도 매 틱 레벨 난수 하나(nextFloat)를 쓴다
        var level = new RecLevel(11);
        var p = player(level);
        p.inventory().set(8, enchanted("minecraft:iron_boots", "minecraft:soul_speed", 1));
        p.combatTick();   // 5의 배수가 아님
        PlayerLocationEffects.tickEffects(p, level, () -> true);
        var probe = new LegacyRandomSource(11);
        probe.nextFloat();
        assertEquals(probe.nextFloat(), level.rnd.nextFloat());
    }

    // ------------------------------------------------------------------ 착지 블록 입자

    @Test
    void landingParticleCountAndCenter() {
        assertEquals(100, LandingParticles.count(10.0F, 3.0));   // ceil(7)=7 → 0.2+7/15 = 0.6667 → 100
        assertEquals((int) (150.0 * 2.5), LandingParticles.count(100.0F, 3.0));   // 상한 2.5
        assertEquals((int) (150.0 * (double) (0.2F + 1.0F / 15.0F)), LandingParticles.count(3.5F, 3.0));   // ceil(0.5)=1

        var level = new RecLevel(1);
        int stone = B.defaultState("minecraft:stone");
        LandingParticles.spawn(level, 0.5, -60, 0.5, new Pos(0, -60, 0), new Pos(0, -61, 0), stone, 10.0F, 3.0);
        assertEquals(1, level.particles.size());
        var pt = level.particles.get(0);
        assertEquals("minecraft:block", pt.options().type());
        assertEquals(stone, ((ParticleOptions.Block) pt.options()).blockState());
        assertEquals(100, pt.count());
        assertEquals(0.15000000596046448, pt.speed());
        assertEquals(0.0, pt.dx());
        assertEquals(0.5, pt.x());
        assertEquals(-60, pt.y());
    }

    @Test
    void landingParticleSkippedForShortFallAirOrUnloaded() {
        var level = new RecLevel(1);
        int stone = B.defaultState("minecraft:stone");
        LandingParticles.spawn(level, 0.5, -60, 0.5, new Pos(0, -60, 0), new Pos(0, -61, 0), stone, 3.0F, 3.0);   // fall > safe 가 아님
        LandingParticles.spawn(level, 0.5, -60, 0.5, new Pos(0, -60, 0), new Pos(0, -61, 0), B.defaultState("minecraft:air"), 10.0F, 3.0);
        LandingParticles.spawn(level, 0.5, -60, 0.5, new Pos(0, -60, 0), new Pos(0, -61, 0), -1, 10.0F, 3.0);
        assertTrue(level.particles.isEmpty());
    }

    @Test
    void landingParticlePulledToBlockEdgeWhenStandingOffBlock() {
        var level = new RecLevel(1);
        int stone = B.defaultState("minecraft:stone");
        // 엔티티는 칸 (0,.,0) 에 있는데 발밑 칸은 (1,.,0): x 로 0.9 → 칸 가운데(1.5) 기준 dx=-0.6, dz=0.0 → max 0.6 → x=1.5+(-0.6/0.6)*0.5=1.0, z=0.5
        LandingParticles.spawn(level, 0.9, -60, 0.5, new Pos(0, -60, 0), new Pos(1, -61, 0), stone, 10.0F, 3.0);
        var pt = level.particles.get(0);
        assertEquals(1.0, pt.x(), 1e-9);
        assertEquals(0.5, pt.z(), 1e-9);
    }

    @Test
    void mobLandingSendsBlockParticleOnlyWhenFallingFarEnough() {
        var level = new RecLevel(1);
        Zombie z = (Zombie) Entities.create(level, "minecraft:zombie");
        z.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(z);
        int grass = B.defaultState("minecraft:grass_block");
        z.fallDistance = 10.0F;
        z.checkFallDamage(0.0, true, grass, new Pos(0, -61, 0));
        assertEquals(1, level.particles.size());
        assertEquals(100, level.particles.get(0).count());
        assertEquals(0.0F, z.fallDistance, "Entity.checkFallDamage 가 낙하 거리를 지운다");
        level.particles.clear();
        z.fallDistance = 2.0F;
        z.checkFallDamage(0.0, true, grass, new Pos(0, -61, 0));
        assertTrue(level.particles.isEmpty());
        z.fallDistance = 10.0F;
        z.checkFallDamage(-0.5, false, grass, new Pos(0, -61, 0));   // 공중이면 없음
        assertTrue(level.particles.isEmpty());
    }

    // ------------------------------------------------------------------ 낙타 질주

    @Test
    void camelDashEmitsEntityActionAndSetsDashing() {
        var level = new RecLevel(1);
        Camel camel = (Camel) Entities.create(level, "minecraft:camel");
        camel.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(camel);
        level.events.clear();
        level.sounds.clear();
        assertTrue(camel.canJump(), "안장 여부와 상관없이 true");
        assertFalse(camel.isDashing());
        camel.handleStartJump(80);
        assertTrue(camel.isDashing());
        assertEquals(List.of(GameEvent.ENTITY_ACTION), level.events);
        assertEquals(1, level.sounds.size());
        assertEquals("entity.camel.dash", level.sounds.get(0).sound());
        assertEquals(0.8F, level.sounds.get(0).volume(), "makeSound: 말 계열 크기 0.8");
    }

    @Test
    void flyingMobsHaveNoLandingParticles() {
        var level = new RecLevel(1);
        int grass = B.defaultState("minecraft:grass_block");
        for (String type : List.of("minecraft:parrot", "minecraft:bat", "minecraft:allay", "minecraft:phantom", "minecraft:ghast")) {
            Entity e = Entities.create(level, type);
            e.moveTo(0.5, -60, 0.5, 0, 0);
            level.addFreshEntity(e);
            e.fallDistance = 10.0F;
            e.checkFallDamage(0.0, true, grass, new Pos(0, -61, 0));
        }
        assertTrue(level.particles.isEmpty());
    }

    // ------------------------------------------------------------------ 걸음 소리

    @Test
    void wardenStepSoundIsLoudAndBlockIndependent() {
        var level = new RecLevel(1);
        Warden w = (Warden) Entities.create(level, "minecraft:warden");
        w.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(w);
        level.sounds.clear();
        w.walkingStepSound(new Pos(0, -61, 0), B.defaultState("minecraft:stone"));
        assertEquals(1, level.sounds.size());
        assertEquals("minecraft:entity.warden.step", level.sounds.get(0).sound());
        assertEquals(10.0F, level.sounds.get(0).volume());
        assertEquals(1.0F, level.sounds.get(0).pitch());
    }

    @Test
    void skeletonStepSoundIsQuiet() {
        var level = new RecLevel(1);
        Skeleton s = (Skeleton) Entities.create(level, "minecraft:skeleton");
        s.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(s);
        level.sounds.clear();
        s.walkingStepSound(new Pos(0, -61, 0), B.defaultState("minecraft:stone"));
        assertEquals(1, level.sounds.size());
        assertEquals("entity.skeleton.step", level.sounds.get(0).sound());
        assertEquals(0.15F, level.sounds.get(0).volume());
    }
}
