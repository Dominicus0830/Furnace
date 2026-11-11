// A11 높음 항목 회귀 시험(점검 보고서 docs/audit/findings/A11-entity-physics-combat.md): 가루눈 냉각(A11-01)·낙하 면역(A11-03)·전도체의 힘 호흡(A11-04).
// 바닐라 1.21.1 바이트코드 근거: btn.m_ 오프셋 678~807(냉각), btn.e(FF)I·bsr.b(낙하 면역), bsa.c·btn.aw 오프셋 270~541(호흡).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A11HazardTest {

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
    private static final PlayerVitals.Surroundings UNDERWATER = new PlayerVitals.Surroundings(true, true, true, false, null, false, -64);
    /** 가루눈 속(서버 move 가 isInPowderSnow 를 켰다), 발밑 칸이 공기 아님. */
    private static final PlayerVitals.Surroundings IN_SNOW = new PlayerVitals.Surroundings(true, false, false, false, null, true, -64, false, true, true);

    private static Player survivalPlayer() {
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        return p;
    }

    /** 가루눈 한 칸을 돌로 둘러싼 구덩이에 소를 넣는다(AI 는 켜 둔다 — 바닐라 NoAI 몹은 move 하지 않아 가루눈 표시가 서지 않는다). */
    private static Cow cowInPowderSnowPit(SkeletonCombatTest.ArenaLevel level) {
        int stone = B.defaultState(B.id("stone"));
        level.setBlock(new Pos(0, -60, 0), B.defaultState(B.id("powder_snow")), 3);
        level.setBlock(new Pos(0, -59, 0), B.defaultState(B.id("powder_snow")), 3);
        for (int y = -60; y <= -57; y++) {
            level.setBlock(new Pos(1, y, 0), stone, 3);
            level.setBlock(new Pos(-1, y, 0), stone, 3);
            level.setBlock(new Pos(0, y, 1), stone, 3);
            level.setBlock(new Pos(0, y, -1), stone, 3);
        }
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(cow);
        return cow;
    }

    /** A11-01: 가루눈 속 몹은 틱마다 ticksFrozen +1(최대 140), 이동 속도에 −0.05 × 언 비율 수정자(minecraft:powder_snow)가 붙고, 저장 NBT 에는 수정자가 없다. */
    @Test
    void mobInPowderSnowAccumulatesTicksFrozen() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var cow = cowInPowderSnowPit(level);
        tick(level, 100);
        assertTrue(cow.isInPowderSnow(), "가루눈 속에 있다 y=" + cow.y());
        assertEquals(100, cow.getTicksFrozen());
        var tag = cow.save();
        assertEquals(100, tag.getIntOr0("TicksFrozen"), "바닐라: 100틱 동안 매 틱 +1");
        var mod = cow.attributes().get(Attribute.MOVEMENT_SPEED).getModifier(LivingEntity.POWDER_SNOW_SPEED_MODIFIER);
        assertNotNull(mod, "tryAddFrost: 얼음 속도 수정자");
        assertEquals((double) (-0.05F * (100.0F / 140.0F)), mod.amount(), 0.0);
        assertFalse(tag.toString().contains(LivingEntity.POWDER_SNOW_SPEED_MODIFIER), "얼음 수정자는 transient — 저장하지 않는다");
    }

    /** A11-01: 완전히 언(140) 몹은 40틱마다 freeze 1 피해. #freeze_hurts_extra_types(블레이즈 등)는 5배. 가루눈을 나오면 틱마다 −2. */
    @Test
    void fullyFrozenMobTakesFreezeDamageAndThawsOutside() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var cow = cowInPowderSnowPit(level);
        tick(level, 200);
        assertEquals(140, cow.getTicksFrozen(), "140 에서 멈춘다");
        assertTrue(cow.health() < cow.maxHealth(), "완전히 언 뒤 40틱마다 1 피해: 체력 " + cow.health());
        float frozenAt = cow.health();
        level.setBlock(new Pos(0, -60, 0), B.defaultState(B.id("air")), 3);
        level.setBlock(new Pos(0, -59, 0), B.defaultState(B.id("air")), 3);
        tick(level, 10);
        assertEquals(120, cow.getTicksFrozen(), "가루눈 밖: 틱마다 −2");
        assertEquals(frozenAt, cow.health(), 0.0F);

        var blaze = new Blaze(level, level.nextEntityId());
        blaze.moveTo(10.5, -60, 10.5, 0, 0);
        level.addFreshEntity(blaze);
        float b0 = blaze.health();
        assertTrue(blaze.hurt(Entity.Damage.of(org.domi.furnace.server.entity.damage.DamageType.byKey("freeze")), 1.0F));
        assertEquals(b0 - 5.0F, blaze.health(), 0.0F, "freeze_hurts_extra_types 는 5배");
    }

    /** A11-01: canFreeze — 가죽(#freeze_immune_wearables) 하나만 입어도, #freeze_immune_entity_types(스트레이 등)도 얼지 않는다. */
    @Test
    void freezeImmuneWearablesAndTypes() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(z);
        assertTrue(z.canFreeze());
        z.setItemSlot(EquipmentSlot.HEAD, Mob.stackOf("minecraft:leather_helmet"));
        assertFalse(z.canFreeze(), "가죽 모자");
        var stray = Entities.create(level, "minecraft:stray");
        assertFalse(stray.canFreeze(), "freeze_immune_entity_types");
    }

    /** A11-01: 플레이어도 가루눈 속에서 ticksFrozen 이 오르고, 얼음 속도 수정자를 이동 속도 속성에 맞추며, 완전히 얼면 40틱마다 1 피해. 가죽 장화면 얼지 않는다. */
    @Test
    void playerFreezesInPowderSnow() {
        var p = survivalPlayer();
        for (int i = 0; i < 100; i++) p.vitals().tick(IN_SNOW);
        assertEquals(100, p.vitals().ticksFrozen());
        p.syncAttributes();
        var mod = p.attributes().get(Attribute.MOVEMENT_SPEED).getModifier(LivingEntity.POWDER_SNOW_SPEED_MODIFIER);
        assertNotNull(mod);
        assertEquals((double) (-0.05F * (100.0F / 140.0F)), mod.amount(), 0.0);
        for (int i = 0; i < 200; i++) p.vitals().tick(IN_SNOW);
        assertEquals(140, p.vitals().ticksFrozen());
        assertTrue(p.vitals().health() < 20.0F, "완전히 얼면 freeze 피해: " + p.vitals().health());
        var saved = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        p.vitals().save(saved);
        assertEquals(140, saved.getIntOr0("TicksFrozen"));
        for (int i = 0; i < 70; i++) p.vitals().tick(DRY);
        assertEquals(0, p.vitals().ticksFrozen(), "밖에서는 틱마다 −2");
        p.syncAttributes();
        assertNull(p.attributes().get(Attribute.MOVEMENT_SPEED).getModifier(LivingEntity.POWDER_SNOW_SPEED_MODIFIER), "얼음이 다 풀리면 수정자를 뗀다");

        var q = survivalPlayer();
        q.inventory().set(8, Mob.stackOf("minecraft:leather_boots"));
        for (int i = 0; i < 20; i++) q.vitals().tick(IN_SNOW);
        assertEquals(0, q.vitals().ticksFrozen(), "가죽 장화는 얼지 않는다");
    }

    /** A11-01: 불이 붙으면(Entity.baseTick) 얼음이 0 으로 녹는다. */
    @Test
    void fireThawsPlayer() {
        var p = survivalPlayer();
        for (int i = 0; i < 30; i++) p.vitals().tick(IN_SNOW);
        assertEquals(30, p.vitals().ticksFrozen());
        p.igniteForSeconds(5.0F);
        PlayerFire.tick(p, null, false);
        assertEquals(0, p.vitals().ticksFrozen());
    }

    /**
     * A11-03: LivingEntity.calculateFallDamage 는 맨 앞에서 #fall_damage_immune 이면 0, Entity.isInvulnerableTo 는 #is_fall 피해에 그 종류면 참.
     * 태그의 모든 생물 종류가 10칸 낙하·직접 낙하 피해에 다치지 않는다.
     */
    @Test
    void fallDamageImmuneTypesTakeNoFallDamage() {
        var level = new SkeletonCombatTest.ArenaLevel();
        StringBuilder bad = new StringBuilder();
        int checked = 0;
        for (int id : EntityTypeTags.ids("minecraft:fall_damage_immune")) {
            String name = org.domi.furnace.server.item.ItemContext.name("minecraft:entity_type", id);
            Entity e = Entities.create(level, name);
            if (!(e instanceof LivingEntity le)) continue;
            checked++;
            le.moveTo(0.5, -60, 0.5, 0, 0);
            level.addFreshEntity(le);
            float h0 = le.health();
            le.causeFallDamage(10.0F, 1.0F, Entity.Damage.FALL);
            if (le.hurt(Entity.Damage.FALL, 5.0F)) bad.append(name).append(" hurt(fall)=true; ");
            if (le.health() != h0) bad.append(name).append(' ').append(h0).append("->").append(le.health()).append("; ");
        }
        assertTrue(checked >= 12, "검사한 생물 수 " + checked);
        assertEquals("", bad.toString(), "fall_damage_immune 인데 낙하 피해를 받은 종류");

        var cow = new Cow(level, level.nextEntityId());   // 대조: 면역이 아닌 종류는 그대로 다친다
        cow.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(cow);
        cow.causeFallDamage(10.0F, 1.0F, Entity.Damage.FALL);
        assertEquals(cow.maxHealth() - 7.0F, cow.health(), 0.0F);
    }

    /** A11-04: MobEffectUtil.hasWaterBreathing = 물숨쉬기 또는 전도체의 힘 — 플레이어. */
    @Test
    void conduitPowerStopsPlayerDrowning() {
        var p = survivalPlayer();
        p.vitals().addEffect(new MobEffectInstance(MobEffect.CONDUIT_POWER, 2000, 0, true, true));
        for (int i = 0; i < 400; i++) p.vitals().tick(UNDERWATER);
        assertEquals(20.0F, p.vitals().health(), 0.0F, "전도체의 힘 아래에서는 익사하지 않는다");
        assertEquals(300, p.vitals().air());
    }

    /** A11-04: 몹 쪽(LivingEntity.baseTick)도 전도체의 힘이면 숨이 줄지 않는다. */
    @Test
    void conduitPowerStopsMobDrowning() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        for (int y = -60; y <= -57; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.setBlock(new Pos(x, y, z), B.defaultState(B.id("water")), 2);
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        cow.addEffect(new MobEffectInstance(MobEffect.CONDUIT_POWER, 5000, 0));
        tick(level, 5);
        assertTrue(cow.isEyeInWater());
        tick(level, 395);
        assertEquals(cow.getMaxAirSupply(), cow.getAirSupply());
        assertEquals(cow.maxHealth(), cow.health(), 0.0F);
    }

    /**
     * 보고서 A11-09 바로잡기: 바닐라 btn.aw 는 눈이 물속이면 숨을 쉴 수 있어도(물숨쉬기·무적) 공기를 줄이지 않을 뿐 차오르게 하지 않는다
     * (오프셋 308 ifeq → 477 → goto 541, 채움 가지 518 은 물 밖일 때만). 플레이어는 원래 맞았고, 몹이 물속에서 채우던 것을 고쳤다.
     */
    @Test
    void waterBreathingDoesNotRefillAirUnderwater() {
        var p = survivalPlayer();
        for (int i = 0; i < 100; i++) p.vitals().tick(UNDERWATER);   // 공기 소모
        int low = p.vitals().air();
        assertTrue(low < 300);
        p.vitals().addEffect(new MobEffectInstance(MobEffect.WATER_BREATHING, 2000, 0, true, true));
        for (int i = 0; i < 20; i++) p.vitals().tick(UNDERWATER);
        assertEquals(low, p.vitals().air(), "플레이어: 물속에서는 그대로");

        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        for (int y = -60; y <= -57; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.setBlock(new Pos(x, y, z), B.defaultState(B.id("water")), 2);
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        cow.setNoAi(true);
        level.addFreshEntity(cow);
        tick(level, 50);
        int mobLow = cow.getAirSupply();
        assertTrue(mobLow < cow.getMaxAirSupply());
        cow.addEffect(new MobEffectInstance(MobEffect.WATER_BREATHING, 5000, 0));
        tick(level, 20);
        assertEquals(mobLow, cow.getAirSupply(), "몹: 물속에서는 그대로");
    }

    /**
     * PR #200 검토 R1: load 는 TicksFrozen 을 자르지 않는다(bsr 오프셋 496). 가루눈 밖에서 −2 씩 줄어도 140 이상인 동안 40틱마다 canFreeze 를 보므로
     * 가죽 장화를 신었으면 피해가 없다(가죽 검사를 가루눈 안에서만 하면 안 된다).
     */
    @Test
    void leatherWearerOutsideSnowWithHighTicksFrozenTakesNoFreezeDamage() {
        var p = survivalPlayer();
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        p.vitals().save(tag);
        tag.putInt("TicksFrozen", 1000);
        p.vitals().load(tag);
        p.vitals().setFoodLevel(17);
        p.inventory().set(8, Mob.stackOf("minecraft:leather_boots"));
        for (int i = 0; i < 120; i++) p.vitals().tick(DRY);
        assertTrue(p.vitals().ticksFrozen() >= 140, "아직 완전히 언 상태: " + p.vitals().ticksFrozen());
        assertEquals(20.0F, p.vitals().health(), 0.0F, "가죽 장화면 canFreeze 거짓 — freeze 피해 없음");
    }
}
