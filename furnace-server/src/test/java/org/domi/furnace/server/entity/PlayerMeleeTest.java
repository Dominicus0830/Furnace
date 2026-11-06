// 플레이어 근접 공격(PlayerMelee): 쿨다운 배율, pvp 설정, 방패 막기, 인챈트 피해, 무기 내구 비용, 쓸기.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerMeleeTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1, null); }

    private static Player player(int id, double x, float yaw, PlayerVitalsTest.TestEnv env) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        return p;
    }

    private static PlayerMelee.World world(ZombieCombatTest.ArenaLevel level, Player... ps) {
        return new PlayerMelee.World() {
            public org.domi.furnace.server.block.ChunkLevel level() { return level; }
            public Iterable<Player> players() { return List.of(ps); }
        };
    }

    @Test
    void fullCooldownSwordHitsPlayerForSeven() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);   // +x 를 본다
        var v = player(2, 1.5, 90, env);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        var r = PlayerMelee.attack(world(level, a, v), a, v);
        assertTrue(r.hurt());
        assertEquals(1, r.weaponDamage(), "검은 내구 1");
        assertEquals(13.0F, v.vitals().health(), 1e-4, "1 + 6, 플레이어가 때리면 난이도 배율 없음");
        assertEquals(0, a.attackStrengthTicker, "공격하면 쿨다운 초기화");
    }

    @Test
    void zeroCooldownScalesDamage() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 0;
        float h = a.attackStrengthScale(0.5F);
        assertEquals(0.5F / 12.5F, h, 1e-4, "검 공격 속도 1.6 → 12.5틱");
        PlayerMelee.attack(world(level, a, v), a, v);
        assertEquals(20.0F - 7.0F * (0.2F + h * h * 0.8F), v.vitals().health(), 1e-4);
    }

    @Test
    void pvpOffBlocksPlayerDamage() {
        var env = new PlayerVitalsTest.TestEnv();
        env.pvp = false;
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        a.attackStrengthTicker = 100;
        assertFalse(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        assertEquals(20.0F, v.vitals().health());
    }

    @Test
    void shieldBlocksAndAxeDisablesIt() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);   // -x(공격자 쪽)를 본다
        v.inventory().set(v.inventory().heldWindowSlot(), item("minecraft:shield"));
        assertTrue(Shields.tryStart(v, 0, v.inventory().get(v.inventory().heldWindowSlot())));
        v.usingTicks -= 5;
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:iron_axe"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        assertFalse(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        assertEquals(20.0F, v.vitals().health());
        assertEquals(-1, v.usingHand, "도끼에 방패가 풀린다");
        assertTrue(v.isOnCooldown(Shields.shieldId()));
    }

    @Test
    void enchantBonuses() {
        var level = new ZombieCombatTest.ArenaLevel();
        var zombie = new Zombie(level, level.nextEntityId());
        var sharp3 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:sharpness", 3));
        assertEquals(2.0F, PlayerMelee.enchantDamageBonus(sharp3, zombie), 1e-6, "날카로움 0.5 × 3 + 0.5");
        var smite2 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:smite", 2));
        assertEquals(5.0F, PlayerMelee.enchantDamageBonus(smite2, zombie), 1e-6, "좀비는 강타 대상");
        var p = player(3, 0.5, 0, new PlayerVitalsTest.TestEnv());
        assertEquals(0.0F, PlayerMelee.enchantDamageBonus(smite2, p), 1e-6);
        var kb2 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:knockback", 2));
        assertEquals(2.0F, PlayerMelee.knockback(kb2), 1e-6);
        var sweep3 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:sweeping_edge", 3));
        assertEquals(0.75F, PlayerMelee.sweepingRatio(sweep3), 1e-6);
        assertEquals(2, PlayerMelee.weaponDurabilityCost(item("minecraft:iron_pickaxe")));
        assertEquals(0, PlayerMelee.weaponDurabilityCost(item("minecraft:stick")));
    }

    @Test
    void attackSpeedResetsOnItemChange() {
        var a = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        a.tickAttackStrength();
        a.attackStrengthTicker = 50;
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        assertEquals(0, a.attackStrengthTicker);
        a.tickAttackStrength();
        assertEquals(1, a.attackStrengthTicker);
    }

    @Test
    void sweepHitsNearbyPlayer() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        var o = player(3, 2.3, 90, env);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        PlayerMelee.attack(world(level, a, v, o), a, v);
        assertEquals(19.0F, o.vitals().health(), 1e-4, "쓸기 피해 1 + 0 × 7");
    }

    /** 다른 사람에게 보이는 엔티티 데이터: 왼손 사용 중 = 1|2, 웅크림 자세 5, 회전 공격 자세 4·비트 4, 새로 보는 사람용 묶음. */
    @Test
    void entityDataForObservers() {
        var p = player(4, 0.5, 0, new PlayerVitalsTest.TestEnv());
        assertNull(p.pairingData());
        p.usingHand = 1;
        assertEquals(3, p.livingFlags());
        p.sneaking(true);
        assertEquals(5, p.pose());
        assertEquals(2, p.sharedFlags() & 2);
        p.syncLivingFlags();
        var d = p.pairingData();
        assertNotNull(d);
        assertEquals(List.of(0, 6, 8), d.items().stream().map(it -> it.index()).toList());
        p.usingHand = -1;
        assertEquals(2, p.livingFlags(), "손 비트는 사용이 끝나도 남는다");
        p.startAutoSpinAttack(20, 8.0F, ItemStack.EMPTY);
        assertEquals(4, p.pose());
        assertEquals(6, p.livingFlags());
    }

    // ------------------------------------------------------------------ 상태 효과 속성 수정자·살충·쓸기 속도 (기대값은 바닐라 공식 손계산)

    private static org.domi.furnace.server.effect.MobEffectInstance fx(org.domi.furnace.server.effect.MobEffect e, int amp) {
        return new org.domi.furnace.server.effect.MobEffectInstance(e, 6000, amp);
    }

    /** 칼(공격력 1 + 6)로 쿨다운이 찬 채 한 번 때려 입힌 피해. */
    private static float swordDamage(org.domi.furnace.server.effect.MobEffectInstance... effects) {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        for (var e : effects) a.vitals().addEffect(e);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        PlayerMelee.attack(world(level, a, v), a, v);
        return 20.0F - v.vitals().health();
    }

    @Test
    void strengthAndWeaknessChangeAttackDamageAttribute() {
        var strength = org.domi.furnace.server.effect.MobEffect.STRENGTH;
        var weakness = org.domi.furnace.server.effect.MobEffect.WEAKNESS;
        assertEquals(7.0F, swordDamage(), 1e-4);
        assertEquals(10.0F, swordDamage(fx(strength, 0)), 1e-4, "힘 I: +3 × 1");
        assertEquals(13.0F, swordDamage(fx(strength, 1)), 1e-4, "힘 II: +3 × 2");
        assertEquals(3.0F, swordDamage(fx(weakness, 0)), 1e-4, "나약함 I: −4 × 1");
        assertEquals(9.0F, swordDamage(fx(strength, 1), fx(weakness, 0)), 1e-4, "1 + 6 + 6 − 4");
    }

    @Test
    void weaknessBelowZeroMissesButStillResetsCooldown() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        a.vitals().addEffect(fx(org.domi.furnace.server.effect.MobEffect.WEAKNESS, 0));   // 맨손 1 − 4 → 0 으로 잘린다
        a.attackStrengthTicker = 100;
        assertFalse(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        assertEquals(20.0F, v.vitals().health());
        assertEquals(0, a.attackStrengthTicker, "resetAttackStrengthTicker 는 피해 판정보다 먼저");
    }

    @Test
    void hasteAndMiningFatigueScaleAttackSpeed() {
        var a = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        double base = 4.0 + -2.4;   // 칼의 attack_speed 수정자 −2.4
        assertEquals((float) (1.0 / base * 20.0), a.attackStrengthDelay(), 1e-6);
        a.vitals().addEffect(fx(org.domi.furnace.server.effect.MobEffect.HASTE, 1));   // add_multiplied_total +0.1F × 2
        assertEquals((float) (1.0 / (base * (1.0 + (double) 0.1F * 2)) * 20.0), a.attackStrengthDelay(), 1e-6);
        var b = player(2, 0.5, 0, new PlayerVitalsTest.TestEnv());
        b.inventory().set(b.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        b.vitals().addEffect(fx(org.domi.furnace.server.effect.MobEffect.MINING_FATIGUE, 0));   // −0.1F × 1
        assertEquals((float) (1.0 / (base * (1.0 + (double) -0.1F)) * 20.0), b.attackStrengthDelay(), 1e-6);
        // 맨손: 4 → 5 틱, 성급함 I 이면 4.4 → 4.545 틱
        var c = player(3, 0.5, 0, new PlayerVitalsTest.TestEnv());
        assertEquals(5.0F, c.attackStrengthDelay(), 1e-6);
        c.vitals().addEffect(fx(org.domi.furnace.server.effect.MobEffect.HASTE, 0));
        assertEquals((float) (1.0 / (4.0 * (1.0 + (double) 0.1F)) * 20.0), c.attackStrengthDelay(), 1e-6);
        // 쿨다운 강도: (ticker + 0.5) / delay
        c.attackStrengthTicker = 2;
        assertEquals(2.5F / c.attackStrengthDelay(), c.attackStrengthScale(0.5F), 1e-6);
    }

    @Test
    void baneOfArthropodsAppliesSlownessFourWithVictimRandom() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var spider = new Spider(level, level.nextEntityId());
        spider.moveTo(1.5, -60, 0.5, 0, 0);
        ((org.domi.furnace.server.block.LegacyRandomSource) spider.random()).setSeed(12345L);
        a.inventory().set(a.inventory().heldWindowSlot(), EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:bane_of_arthropods", 3)));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        // 같은 씨앗의 난수 순서: nextInt(1) → nextFloat(지속) → nextFloat(증폭). 레벨 3: 최대 지속 1.5 + 0.5 × 2 = 2.5초
        var ref = new org.domi.furnace.server.block.LegacyRandomSource(12345L);
        ref.nextInt(1);
        int duration = Math.round((ref.nextFloat() * (2.5F - 1.5F) + 1.5F) * 20.0F);
        int amplifier = Math.max(0, Math.round(ref.nextFloat() * (3.0F - 3.0F) + 3.0F));
        assertEquals(3, amplifier);
        // postAttack 만 따로 불러 난수 순서를 못 박는다(피해·넉백 처리가 같은 난수를 쓸 수 있어서)
        PlayerMelee.postAttack(level, a, a.inventory().get(a.inventory().heldWindowSlot()), spider);
        var slow = spider.getEffect(org.domi.furnace.server.effect.MobEffect.SLOWNESS);
        assertNotNull(slow, "거미는 살충 대상");
        assertEquals(3, slow.amplifier());
        assertEquals(duration, slow.duration());
        assertTrue(duration >= 30 && duration <= 50);
        // 실제 공격 경로에서도 걸린다
        var spider2 = new Spider(level, level.nextEntityId());
        spider2.moveTo(1.5, -60, 0.5, 0, 0);
        assertTrue(PlayerMelee.attack(world(level, a), a, spider2).hurt());
        var slow2 = spider2.getEffect(org.domi.furnace.server.effect.MobEffect.SLOWNESS);
        assertNotNull(slow2);
        assertEquals(3, slow2.amplifier());
        assertTrue(slow2.duration() >= 30 && slow2.duration() <= 50);
    }

    @Test
    void baneOfArthropodsIgnoresOtherMobs() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var zombie = new Zombie(level, level.nextEntityId());
        zombie.moveTo(1.5, -60, 0.5, 0, 0);
        a.inventory().set(a.inventory().heldWindowSlot(), EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:bane_of_arthropods", 5)));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        PlayerMelee.attack(world(level, a), a, zombie);
        assertNull(zombie.getEffect(org.domi.furnace.server.effect.MobEffect.SLOWNESS));
    }

    @Test
    void sweepNeedsSlowerWalkThanMovementSpeedAttribute() {
        // 마지막 이동 0.19 → 걸음 0.19 × 0.6 = 0.114. 기본 이동 속도 0.1 이면 쓸기 없음, 속도 I(0.1 × 1.2 = 0.12)면 쓸기
        for (boolean speed : new boolean[] {false, true}) {
            var env = new PlayerVitalsTest.TestEnv();
            var level = new ZombieCombatTest.ArenaLevel();
            var a = player(1, 0.5, -90, env);
            var v = player(2, 1.5, 90, env);
            var o = player(3, 2.3, 90, env);
            if (speed) a.vitals().addEffect(fx(org.domi.furnace.server.effect.MobEffect.SPEED, 0));
            a.applyMove(0.69, -60, 0.5, -90, 0, true);
            a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
            a.tickAttackStrength();
            a.attackStrengthTicker = 100;
            PlayerMelee.attack(world(level, a, v, o), a, v);
            assertEquals(speed ? 19.0F : 20.0F, o.vitals().health(), 1e-4, speed ? "속도 효과로 쓸기" : "너무 빨리 걸어 쓸기 없음");
        }
    }

    @Test
    void victimPlayerGetsVelocityPacketImmediately() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var vch = new ConcurrentEmbeddedChannel();
        var v = new Player(2, UUID.randomUUID(), "P2", vch);
        v.vitals().env(env);
        v.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        v.applyMove(1.5, -60, 0.5, 90, 0, true);
        v.applyMove(1.5, -60, 0.5, 90, 0, true);
        for (int i = 0; i < 100; i++) v.vitals().tick(DRY);
        vch.runPendingTasks();
        while (vch.readOutbound() != null) { }
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        assertTrue(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        vch.runPendingTasks();
        int motion = 0;
        Object o;
        while ((o = vch.readOutbound()) != null) {
            var bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == org.domi.furnace.server.net.protocol.play.SetEntityMotion.ID) motion++;
            bb.release();
        }
        assertEquals(1, motion, "공격 즉시 맞은 플레이어에게 set_entity_motion 한 번");
        v.sendPendingKnockback();
        vch.runPendingTasks();
        assertNull(vch.readOutbound(), "틱 끝에 다시 보내지 않는다");
    }
}
