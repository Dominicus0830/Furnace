// A11 중간·낮음 항목 회귀 시험(점검 보고서 docs/audit/findings/A11-entity-physics-combat.md): 화염 저항 몹(A11-06)·창조 모드 대 Invulnerable(A11-07)·
// 몹 무기 마법(A11-08)·슬로 폴링 낙하 거리(A11-10)·효과 틱 순서(A11-11)·플레이어 낙하 속성(A11-12)·무적 시간 우회 태그(A11-13)·step_height(A11-14),
// 그리고 점검 중 찾은 NoAI 몹 중력·#damages_helmet 투구 0.75 배. 바닐라 1.21.1 바이트코드 근거는 context-notes/2026-11-11-a11-medium-low.md.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A11MediumLowTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void tick(SkeletonCombatTest.ArenaLevel level, int n) {
        for (int t = 0; t < n; t++) {
            level.time++;
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static Player survivalPlayer() {
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        return p;
    }

    private static Zombie zombieAt(SkeletonCombatTest.ArenaLevel level, double x, double z) {
        var zb = new Zombie(level, level.nextEntityId());
        zb.moveTo(x, -60, z, 0, 0);
        level.addFreshEntity(zb);
        return zb;
    }

    // ---------------------------------------------------------------- A11-06 화염 저항 몹

    /** btn.a(brk,F) 오프셋 31~52: #is_fire 피해이고 화염 저항 효과가 있으면 false(피해·표시 없음). */
    @Test
    void fireResistanceMobIgnoresFireDamage() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var z = zombieAt(level, 0.5, 0.5);
        z.addEffect(new MobEffectInstance(MobEffect.FIRE_RESISTANCE, 1000, 0));
        float before = z.health();
        assertFalse(z.hurt(Entity.Damage.LAVA, 4.0F), "바닐라: 화염 저항이면 hurt == false");
        assertFalse(z.hurt(new Entity.Damage("on_fire", true, false, false), 1.0F));
        assertEquals(before, z.health(), 0.0F);
        // 화염이 아닌 피해는 그대로 받는다
        assertTrue(z.hurt(new Entity.Damage("generic", false, false, false), 2.0F));
        assertEquals(before - 2.0F, z.health(), 0.0F);
    }

    // ---------------------------------------------------------------- A11-07 창조 모드 직접 공격

    private static float hitInvulnerableCow(GameMode mode) {
        var level = new SkeletonCombatTest.ArenaLevel();
        var p = new Player(1, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(0.5, -60, 0.5, -90, 0, true);
        p.applyMove(0.5, -60, 0.5, -90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(1.5, -60, 0.5, 0, 0);
        cow.setInvulnerable(true);
        level.addFreshEntity(cow);
        p.tickAttackStrength();
        p.attackStrengthTicker = 200;
        var world = new PlayerMelee.World() {
            public org.domi.furnace.server.block.ChunkLevel level() { return level; }
            public Iterable<Player> players() { return java.util.List.of(p); }
        };
        float before = cow.health();
        PlayerMelee.attack(world, p, cow);
        return before - cow.health();
    }

    @Test
    void survivalPlayerCannotHurtInvulnerableMob() {
        assertEquals(0.0F, hitInvulnerableCow(GameMode.SURVIVAL), 0.0F);
    }

    /** brk.h()(직접 엔티티가 abilities.instabuild 플레이어)가 참이면 Entity.isInvulnerableTo 의 invulnerable 항이 거짓이다. */
    @Test
    void creativePlayerHurtsInvulnerableMob() {
        assertTrue(hitInvulnerableCow(GameMode.CREATIVE) > 0.0F, "바닐라: 창조 모드 직접 공격은 invulnerable 을 무시한다");
    }

    /** 직접 원인이 투사체면 창조 모드 주인이라도 막힌다(brk.d() 가 화살이므로 isCreativePlayer 거짓). */
    @Test
    void projectileFromCreativePlayerDoesNotBypassInvulnerable() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        cow.setInvulnerable(true);
        level.addFreshEntity(cow);
        assertTrue(cow.isInvulnerableTo(new Entity.Damage("arrow", false, false, false)));
        assertFalse(cow.isInvulnerableTo(new Entity.Damage("arrow", false, false, false).withCreativeSource(true)));
    }

    // ---------------------------------------------------------------- A11-08 몹 무기 마법

    private static Zombie armedZombie(SkeletonCombatTest.ArenaLevel level, double z, String ench, int lvl) {
        var zb = new Zombie(level, level.nextEntityId());
        zb.moveTo(0.5, -60, z, 0, 0);
        zb.setItemSlot(EquipmentSlot.MAINHAND, org.domi.furnace.server.item.EnchantTools.update(Mob.stackOf("minecraft:iron_sword"),
                m -> { if (ench != null) m.put(ench, lvl); }));
        level.addFreshEntity(zb);
        return zb;
    }

    /** Mob.doHurtTarget: modifyDamage(getWeaponItem) — 날카로움 V = 1 + 0.5 × (5 − 1) = +3. */
    @Test
    void mobWeaponSharpnessAddsDamage() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var plain = armedZombie(level, 0.5, null, 0);
        var sharp = armedZombie(level, 3.5, "minecraft:sharpness", 5);
        tick(level, 2);   // detectEquipmentUpdates 가 칼의 공격력 수정자를 건다
        float[] dealt = new float[2];
        for (int i = 0; i < 2; i++) {
            var golem = new IronGolem(level, level.nextEntityId());   // 체력 100 — 소(10)는 피해가 체력에 막혀 차이가 안 보인다
            golem.moveTo(10.5 + 3 * i, -60, 0.5, 0, 0);
            golem.setNoAi(true);
            level.addFreshEntity(golem);
            float before = golem.health();
            (i == 0 ? plain : sharp).doHurtTarget(golem);
            dealt[i] = before - golem.health();
        }
        assertEquals(dealt[0] + 3.0F, dealt[1], 1.0E-4F, "날카로움 5 칼을 든 몹은 3 더 때린다 (일반 " + dealt[0] + ", 날카로움 " + dealt[1] + ")");
    }

    /** 공격 후 효과: 불 속성 무기는 맞은 몹에 불을 붙인다(4초 × 레벨). */
    @Test
    void mobWeaponFireAspectIgnitesTarget() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var fiery = armedZombie(level, 0.5, "minecraft:fire_aspect", 2);
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(10.5, -60, 0.5, 0, 0);
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        assertFalse(cow.isOnFire());
        fiery.doHurtTarget(cow);
        assertTrue(cow.isOnFire(), "불 속성 II: 대상에 불");
    }

    // ---------------------------------------------------------------- A11-10 슬로 폴링 낙하 거리

    /** btn.m_ 오프셋 591~611: 슬로 폴링·부양 효과는 매 틱 resetFallDistance. */
    @Test
    void slowFallingMobTakesNoFallDamage() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -30, 0.5, 0, 0);
        level.addFreshEntity(cow);
        cow.addEffect(new MobEffectInstance(MobEffect.SLOW_FALLING, 5000, 0));
        tick(level, 400);
        assertTrue(cow.onGround(), "착지했다 y=" + cow.y());
        assertEquals(cow.maxHealth(), cow.health(), 0.0F, "슬로 폴링 중 착지는 피해 없음");
    }

    // ---------------------------------------------------------------- A11-11 효과 틱 순서

    /** btn.aw: hurtTime-- → invulnerableTime-- → … → tickEffects(오프셋 765). 맞은 뒤 10번째 틱에 첫 위더 피해가 들어간다. */
    @Test
    void effectDamageSeesInvulnerabilityAfterDecrement() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(cow);
        assertTrue(cow.hurt(new Entity.Damage("generic", false, false, false), 4.0F));
        cow.addEffect(new MobEffectInstance(MobEffect.WITHER, 1000, 5));   // 40 >> 5 = 1: 매 틱
        float afterHit = cow.health();
        tick(level, 10);
        assertEquals(afterHit - 1.0F, cow.health(), 0.0F, "바닐라: 10틱째에 첫 위더 피해 1");
    }

    // ---------------------------------------------------------------- A11-12 플레이어 낙하 속성

    /** 바닐라 calculateFallDamage 는 generic.safe_fall_distance·generic.fall_damage_multiplier 속성을 읽는다. 점프 강화는 safe_fall_distance 수정자라 같은 식. */
    @Test
    void playerFallDamageReadsFallAttributes() {
        var p = survivalPlayer();
        p.syncAttributes();
        p.attributes().get(Attribute.SAFE_FALL_DISTANCE).setBaseValue(10.0);
        p.syncAttributes();
        float before = p.vitals().health();
        p.vitals().causePassengerFallDamage(8.0F, 1.0F, "minecraft:fall");
        assertEquals(before, p.vitals().health(), 0.0F, "안전 거리 10 이면 8칸 낙하는 피해 없음");
        p.vitals().causePassengerFallDamage(12.0F, 1.0F, "minecraft:fall");
        assertEquals(before - 2.0F, p.vitals().health(), 0.0F, "12 − 10 = 2");

        var q = survivalPlayer();
        q.syncAttributes();
        q.attributes().get(Attribute.FALL_DAMAGE_MULTIPLIER).setBaseValue(0.5);
        q.syncAttributes();
        float qb = q.vitals().health();
        q.vitals().causePassengerFallDamage(11.0F, 1.0F, "minecraft:fall");
        assertEquals(qb - 4.0F, q.vitals().health(), 0.0F, "ceil((11 − 3) × 0.5) = 4");
    }

    /** 점프 강화(안전 거리 +1 × (단계+1))는 속성 경로로 바뀌어도 같은 결과. */
    @Test
    void playerJumpBoostStillRaisesSafeFallDistance() {
        var p = survivalPlayer();
        p.syncAttributes();
        p.vitals().addEffect(new MobEffectInstance(MobEffect.JUMP_BOOST, 1000, 1, true, true));   // 점프 강화 II: +2
        float before = p.vitals().health();
        p.vitals().causePassengerFallDamage(8.0F, 1.0F, "minecraft:fall");
        assertEquals(before - 3.0F, p.vitals().health(), 0.0F, "8 − 3 − 2 = 3");
    }

    // ---------------------------------------------------------------- A11-13 무적 시간 우회 태그

    /** 무적 시간 창 우회는 #bypasses_cooldown(1.21.1 데이터에는 항목 없음) — 월드 밖 피해도 창을 거친다. */
    @Test
    void outOfWorldDamageObeysInvulnerabilityFrames() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(cow);
        float h0 = cow.health();
        cow.hurt(Entity.Damage.OUT_OF_WORLD, 4.0F);
        cow.hurt(Entity.Damage.OUT_OF_WORLD, 4.0F);   // 같은 틱 두 번째: amount(4) <= lastHurt(4) 라 무시
        assertEquals(h0 - 4.0F, cow.health(), 0.0F, "무적 시간 안의 같은 피해는 한 번만");
        // /kill 은 Float.MAX_VALUE 라 창과 상관없이 죽는다
        cow.kill();
        assertTrue(cow.isDeadOrDying());
    }

    // ---------------------------------------------------------------- A11-14 step_height

    private static final class MoveLevel extends org.domi.furnace.server.block.BlockPlacementTest.TestLevel {}

    private static Player walker(double x) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, 0, 0, true);
        p.applyMove(x, -60, 0.5, 0, 0, true);
        PlayerMoveValidator.tick(p);
        return p;
    }

    /**
     * Entity.maxUpStep = generic.step_height. 땅 위에서 블록 높이 1 인 돌에 부딪히는 수평 이동(0.7): 기본 0.6 은 막혀 x = 1.7 에 서고,
     * 속성이 1 이면 걸어 올라 (1.9, -59) 에 선다 — 서버 move(handleMovePlayer)와 클라이언트의 판정이 같아야 "moved wrongly" 오탐이 없다.
     */
    @Test
    void playerMoveReadsStepHeightAttribute() {
        var l = new MoveLevel();
        l.setBlock(new Pos(2, -60, 0), Blocks.get().defaultState("minecraft:stone"), 3);   // 윗면 -59
        var def = walker(1.2);
        double[] r0 = PlayerMoveValidator.move(l, def, 0.7, 0.0, 0.0, false);
        assertEquals(1.7, r0[0], 1.0E-6, "기본 step_height 0.6: 돌에 막힌다");
        assertEquals(-60.0, r0[1], 1.0E-6);
        var high = walker(1.2);
        high.attributes().get(Attribute.STEP_HEIGHT).setBaseValue(1.0);
        double[] r1 = PlayerMoveValidator.move(l, high, 0.7, 0.0, 0.0, false);
        assertEquals(1.9, r1[0], 1.0E-6, "step_height 1: 걸어 올라간다");
        assertEquals(-59.0, r1[1], 1.0E-6);
    }

    // ---------------------------------------------------------------- NoAI 몹 중력

    /** btn.a(exc) 첫 줄 da()=isControlledByLocalInstance(→ Mob.db = !NoAI): NoAI 몹은 travel 에서 move 하지 않아 떨어지지 않는다. */
    @Test
    void noAiMobDoesNotFall() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var still = new Cow(level, level.nextEntityId());
        still.moveTo(0.5, -50, 0.5, 0, 0);
        still.setNoAi(true);
        level.addFreshEntity(still);
        var falling = new Cow(level, level.nextEntityId());
        falling.moveTo(3.5, -50, 0.5, 0, 0);
        level.addFreshEntity(falling);
        tick(level, 20);
        assertEquals(-50.0, still.y(), 0.0, "NoAI 몹은 제자리");
        assertTrue(falling.y() < -50.0, "AI 몹은 떨어진다 y=" + falling.y());
    }

    /** NoAI 몹은 맞아도 밀려나지 않는다(속도만 쌓이고 move 하지 않는다). */
    @Test
    void noAiMobIsNotMovedByKnockback() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        tick(level, 2);
        cow.knockback(0.4F, 1.0, 0.0);
        tick(level, 10);
        assertEquals(0.5, cow.x(), 0.0);
        assertEquals(0.5, cow.z(), 0.0);
    }

    // ---------------------------------------------------------------- #damages_helmet 투구 0.75 배

    /** btn.a(brk,F) 오프셋 180~214: #damages_helmet 피해는 머리 칸이 비어 있지 않으면 0.75 배(방어력 계산 앞). */
    @Test
    void helmetReducesFallingAnvilDamageForMobs() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var bare = zombieAt(level, 0.5, 0.5);
        var capped = zombieAt(level, 3.5, 0.5);
        capped.setItemSlot(EquipmentSlot.HEAD, Mob.stackOf("minecraft:carved_pumpkin"));   // 방어구가 아니라 방어력·내구도 없이 0.75 배만
        float b0 = bare.health(), c0 = capped.health();
        var anvil = new Entity.Damage("falling_anvil", false, false, false);
        assertTrue(bare.hurt(anvil, 10.0F));
        assertTrue(capped.hurt(anvil, 10.0F));
        float dBare = b0 - bare.health(), dCapped = c0 - capped.health();
        assertTrue(dBare > 0.0F);
        assertEquals(dBare * 0.75F, dCapped, 1.0E-3F, "투구 낀 몹은 0.75 배 (맨머리 " + dBare + ", 투구 " + dCapped + ")");
        // 다른 피해는 영향 없음
        var b2 = zombieAt(level, 6.5, 0.5);
        b2.setItemSlot(EquipmentSlot.HEAD, Mob.stackOf("minecraft:carved_pumpkin"));
        float h2 = b2.health();
        assertTrue(b2.hurt(new Entity.Damage("generic", false, false, false), 4.0F));
        assertTrue(h2 - b2.health() > 0.0F);
    }

    /** 플레이어: 투구 0.75 배와 투구 내구도(hurtHelmet max(1, 피해/4)) — 방어구 내구 감소(actuallyHurt)는 따로. */
    @Test
    void helmetReducesFallingAnvilDamageForPlayersAndWearsHelmet() {
        var bare = survivalPlayer();
        var pumpkin = survivalPlayer();
        pumpkin.inventory().set(5, new ItemStack(
                org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:carved_pumpkin"), 1));
        float bb = bare.vitals().health(), pb = pumpkin.vitals().health();
        var anvil = DamageTypes.FALLING_ANVIL;
        bare.vitals().hurt(anvil, 8.0F);
        pumpkin.vitals().hurt(anvil, 8.0F);
        float dBare = bb - bare.vitals().health(), dPumpkin = pb - pumpkin.vitals().health();
        assertEquals(8.0F, dBare, 1.0E-4F);
        assertEquals(6.0F, dPumpkin, 1.0E-4F, "투구를 쓰면 8 × 0.75 = 6");

        var iron = survivalPlayer();
        iron.inventory().set(5, new ItemStack(
                org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:iron_helmet"), 1));
        iron.vitals().hurt(anvil, 8.0F);
        // hurtHelmet(8): max(1, 8/4) = 2, 이어 actuallyHurt 의 방어구 내구 감소(6 → 6/4 = 1.5 → 1): 합 3
        assertEquals(3, ItemTools.damage(iron.inventory().get(5)), "투구 내구도 2(hurtHelmet) + 1(방어구 감소)");
    }

    private static final class DamageTypes {
        static final org.domi.furnace.server.entity.damage.DamageType FALLING_ANVIL = org.domi.furnace.server.entity.damage.DamageType.byKey("falling_anvil");
    }
}
