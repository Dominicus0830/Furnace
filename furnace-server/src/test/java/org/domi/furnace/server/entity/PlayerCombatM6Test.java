// M6: 플레이어 전투 — 쓸기·피해 표시 입자, 힘/나약함이 생물이 아닌 대상에도, 찌르기 근접 피해, 철퇴 착지 먼지 기둥, 살충 느림.
// 기대값은 바닐라 1.21.1 바이트코드(Player.attack = cmx.e, sweepAttack = cmx.fV, MaceItem = cvb, ServerPlayer.checkFallDamage = aqv.a(DDDZ))를 손으로 계산한 것이다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerCombatM6Test {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1, null); }

    private static ItemStack enchanted(String item, String enchant, int level) {
        return EnchantTools.update(item(item), m -> m.put(enchant, level));
    }

    private static Player player(int id, double x, float yaw, PlayerVitalsTest.TestEnv env) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        return p;
    }

    private static PlayerMelee.World world(ParticleSoundEventsTest.RecLevel level, Player... ps) {
        return new PlayerMelee.World() {
            public org.domi.furnace.server.block.ChunkLevel level() { return level; }
            public Iterable<Player> players() { return List.of(ps); }
        };
    }

    private static void ready(Player a, ItemStack weapon, int ticker) {
        a.inventory().set(a.inventory().heldWindowSlot(), weapon);
        a.tickAttackStrength();
        a.attackStrengthTicker = ticker;
    }

    // ------------------------------------------------------------------ 쓸기·피해 표시 입자 (바닐라 sweepAttack / damage_indicator)

    @Test
    void sweepAndDamageIndicatorParticles() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, -90, env);   // +x 를 본다
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(1.5, -60, 0.5, 0, 0);
        z.setOnGround(true);
        ready(a, item("minecraft:diamond_sword"), 100);
        var r = PlayerMelee.attack(world(level, a), a, z);
        assertTrue(r.hurt());
        // sweep_attack: 위치 = 플레이어 + (-sin(yaw), 0.5 × 키, cos(yaw)), count 0 → 속도 입자: (dx, 0, dz) = 같은 방향, 속도 0
        var sweep = level.particles.stream().filter(pt -> pt.options() instanceof ParticleOptions.Simple s && s.type().equals("minecraft:sweep_attack")).toList();
        assertEquals(1, sweep.size());
        var s = sweep.get(0);
        assertEquals(0, s.count());
        assertEquals(1.5, s.x(), 1e-5, "x + 1");
        assertEquals(-60 + a.bbHeight() * 0.5, s.y(), 1e-5);
        assertEquals(0.5, s.z(), 1e-5);
        assertEquals(1.0, s.dx(), 1e-5);
        assertEquals(0.0, s.dy(), 1e-9);
        assertEquals(0.0, s.speed(), 1e-9);
        // damage_indicator: 입힌 양 7 > 2 → (int)(7 × 0.5) = 3 개, 위치는 대상 (x, 0.5 키, z), 퍼짐 (0.1, 0, 0.1), 속도 0.2
        var ind = level.particles.stream().filter(pt -> pt.options() instanceof ParticleOptions.Simple si && si.type().equals("minecraft:damage_indicator")).toList();
        assertEquals(1, ind.size());
        var d = ind.get(0);
        assertEquals(3, d.count());
        assertEquals(1.5, d.x(), 1e-9);
        assertEquals(-60 + z.bbHeight() * 0.5, d.y(), 1e-5);
        assertEquals(0.1, d.dx(), 1e-9);
        assertEquals(0.0, d.dy(), 1e-9);
        assertEquals(0.2, d.speed(), 1e-9);
    }

    @Test
    void damageIndicatorSkippedForTwoOrLess() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, -90, env);
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(1.5, -60, 0.5, 0, 0);
        ready(a, item("minecraft:stick"), 100);   // 맨손과 같은 공격력 1
        assertTrue(PlayerMelee.attack(world(level, a), a, z).hurt());
        assertTrue(level.particles.stream().noneMatch(pt -> pt.options() instanceof ParticleOptions.Simple s && s.type().equals("minecraft:damage_indicator")));
    }

    // ------------------------------------------------------------------ 생물이 아닌 대상도 같은 경로(쿨다운·힘/나약함)

    private static Boat boat(ParticleSoundEventsTest.RecLevel level) {
        var b = new Boat(level, level.nextEntityId());
        b.moveTo(1.5, -60, 0.5, 0, 0);
        return b;
    }

    @Test
    void nonLivingTargetFeelsStrengthAndCooldown() {
        // 맨손 공격력 1. 보트는 받은 피해 × 10 이 쌓인다(40 을 넘으면 부서진다).
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, -90, env);
        var b = boat(level);
        ready(a, ItemStack.EMPTY, 100);
        assertTrue(PlayerMelee.attack(world(level, a), a, new PlayerMelee.Raw(b)).hurt());
        assertEquals(10.0F, b.getDamage(), 1e-4, "쿨다운 가득: 1 × 10");
        assertEquals(0, a.attackStrengthTicker, "때리면 쿨다운 초기화");

        // 힘 I(+3) → 4 → 보트 피해 40 (넘지 않으니 안 부서진다)
        var b2 = boat(level);
        a.vitals().addEffect(new MobEffectInstance(MobEffect.STRENGTH, 6000, 0));
        a.attackStrengthTicker = 100;
        assertTrue(PlayerMelee.attack(world(level, a), a, new PlayerMelee.Raw(b2)).hurt());
        assertEquals(40.0F, b2.getDamage(), 1e-4, "힘 I: (1 + 3) × 10");
        assertFalse(b2.isRemoved());

        // 쿨다운 0: 공격력 × (0.2 + h² × 0.8), h = 0.5 / 5틱 = 0.1 → 4 × 0.208
        var b3 = boat(level);
        a.attackStrengthTicker = 0;
        assertTrue(PlayerMelee.attack(world(level, a), a, new PlayerMelee.Raw(b3)).hurt());
        float h = 0.5F / 5.0F;
        assertEquals(4.0F * (0.2F + h * h * 0.8F) * 10.0F, b3.getDamage(), 1e-3);
    }

    @Test
    void nonLivingTargetWeaknessBelowZeroMisses() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, -90, env);
        var b = boat(level);
        a.vitals().addEffect(new MobEffectInstance(MobEffect.WEAKNESS, 6000, 0));   // 1 − 4 → 0 으로 잘린다
        ready(a, ItemStack.EMPTY, 100);
        assertFalse(PlayerMelee.attack(world(level, a), a, new PlayerMelee.Raw(b)).hurt());
        assertEquals(0.0F, b.getDamage());
        assertEquals(0, a.attackStrengthTicker);
    }

    @Test
    void nonLivingTargetSkipsLivingOnlySteps() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, -90, env);
        var b = boat(level);
        ready(a, item("minecraft:diamond_sword"), 100);
        var r = PlayerMelee.attack(world(level, a), a, new PlayerMelee.Raw(b));
        assertTrue(r.hurt());
        assertEquals(0, r.weaponDamage(), "postHurtEnemy 는 LivingEntity 대상일 때만");
        assertTrue(level.particles.stream().noneMatch(pt -> pt.options() instanceof ParticleOptions.Simple s && s.type().equals("minecraft:damage_indicator")));
        assertNull(a.getLastHurtMob(), "setLastHurtMob: LivingEntity 가 아니면 null");
    }

    @Test
    void sharpnessAppliesToNonLivingButSmiteDoesNot() {
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var b = new PlayerMelee.Raw(boat(level));
        assertEquals(2.0F, PlayerMelee.enchantDamageBonus(enchanted("minecraft:iron_sword", "minecraft:sharpness", 3), b), 1e-6);
        assertEquals(0.0F, PlayerMelee.enchantDamageBonus(enchanted("minecraft:iron_sword", "minecraft:smite", 5), b), 1e-6);
    }

    @Test
    void isAttackableMatchesVanillaOverrides() {
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        assertTrue(boat(level).isAttackable());
        assertFalse(new ExperienceOrb(level, level.nextEntityId(), 0.5, -60, 0.5, 3).isAttackable(), "경험치 구슬");
        assertFalse(new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, item("minecraft:stick")).isAttackable(), "아이템");
        assertFalse(new Arrow(level, level.nextEntityId()).isAttackable(), "화살은 redirectable_projectile 가 아니다");
        assertTrue(new EndCrystal(level, level.nextEntityId()).isAttackable());
    }

    // ------------------------------------------------------------------ 찌르기 근접 피해

    @Test
    void impalingAddsInMeleeAgainstAquaticMobs() {
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var trident = enchanted("minecraft:trident", "minecraft:impaling", 3);
        var squid = new Squid(level, level.nextEntityId());
        var zombie = new Zombie(level, level.nextEntityId());
        assertEquals(7.5F, PlayerMelee.enchantDamageBonus(trident, squid), 1e-6, "2.5 × 3, #sensitive_to_impaling");
        assertEquals(0.0F, PlayerMelee.enchantDamageBonus(trident, zombie), 1e-6);
    }

    // ------------------------------------------------------------------ 살충 느림

    @Test
    void baneOfArthropodsSlowsSpiderForSecondsScaledByLevel() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, -90, env);
        var spider = new Spider(level, level.nextEntityId());
        spider.moveTo(1.5, -60, 0.5, 0, 0);
        ready(a, enchanted("minecraft:iron_sword", "minecraft:bane_of_arthropods", 5), 100);
        assertTrue(PlayerMelee.attack(world(level, a), a, spider).hurt());
        var slow = spider.getEffect(MobEffect.SLOWNESS);
        assertNotNull(slow, "느림");
        assertEquals(3, slow.amplifier(), "느림 IV");
        // 지속: 1.5초 ~ 1.5 + 0.5 × 4 = 3.5초 (×20 반올림)
        assertTrue(slow.duration() >= 30 && slow.duration() <= 70, "지속 " + slow.duration());
    }

    // ------------------------------------------------------------------ 철퇴 착지 먼지 기둥

    @Test
    void maceSmashOnGroundedTargetArmsDustPillarAndAirTargetDoesNot() {
        for (boolean onGround : new boolean[] {true, false}) {
            var env = new PlayerVitalsTest.TestEnv();
            var level = new ParticleSoundEventsTest.RecLevel(1L);
            var a = player(1, 0.5, -90, env);
            var z = new Zombie(level, level.nextEntityId());
            z.moveTo(1.5, -60, 0.5, 0, 0);
            z.setOnGround(onGround);
            ready(a, item("minecraft:mace"), 200);
            a.vitals().setFallDistance(4.0F);
            assertTrue(PlayerMelee.attack(world(level, a), a, z).hurt());
            assertEquals(onGround, a.vitals().spawnExtraParticlesOnFall(), onGround ? "땅 위 대상: 표시 켜짐" : "공중 대상: 표시 없음");
        }
    }

    @Test
    void maceDustPillarOnLanding() {
        var level = new ParticleSoundEventsTest.RecLevel(1L);
        var a = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        int stone = B.defaultState("minecraft:stone");
        assertFalse(LandingParticles.spawnMaceDustPillar(level, a.vitals(), 3, -61, 4, stone, 2.0F), "표시가 꺼져 있으면 아무 일도 없다");
        assertTrue(level.particles.isEmpty());
        a.vitals().setSpawnExtraParticlesOnFall(true);
        assertFalse(LandingParticles.spawnMaceDustPillar(level, a.vitals(), 3, -61, 4, stone, 0.0F), "낙하 거리 0 은 제외");
        assertTrue(a.vitals().spawnExtraParticlesOnFall(), "조건이 안 맞으면 표시는 그대로");
        assertTrue(LandingParticles.spawnMaceDustPillar(level, a.vitals(), 3, -61, 4, stone, 2.0F));
        assertFalse(a.vitals().spawnExtraParticlesOnFall(), "쓰면 꺼진다");
        assertEquals(1, level.particles.size());
        var pt = level.particles.get(0);
        assertEquals(new ParticleOptions.Block("minecraft:dust_pillar", stone), pt.options());
        assertEquals(100, pt.count(), "(int)clamp(50 × 2, 0, 200)");
        assertEquals(3.5, pt.x(), 1e-9);
        assertEquals(-60.0, pt.y(), 1e-9, "칸 가운데 + 0.5");
        assertEquals(4.5, pt.z(), 1e-9);
        assertEquals(0.30000001192092896, pt.dx(), 1e-12);
        assertEquals(0.15000000596046448, pt.speed(), 1e-12);
        level.particles.clear();
        a.vitals().setSpawnExtraParticlesOnFall(true);
        LandingParticles.spawnMaceDustPillar(level, a.vitals(), 3, -61, 4, stone, 30.0F);
        assertEquals(200, level.particles.get(0).count(), "상한 200");
    }

    @Test
    void dustPillarFlagIsSavedLikeVanilla() {
        var a = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        a.vitals().setSpawnExtraParticlesOnFall(true);
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        a.vitals().save(tag);
        assertTrue(tag.getBoolean("spawn_extra_particles_on_fall"));
        var b = player(2, 0.5, 0, new PlayerVitalsTest.TestEnv());
        assertFalse(b.vitals().spawnExtraParticlesOnFall());
        b.vitals().load(tag);
        assertTrue(b.vitals().spawnExtraParticlesOnFall());
    }
}
