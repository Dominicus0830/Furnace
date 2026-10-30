// LivingEntity.hurt 넉백 방향(투사체 속도/원인 위치/원인 없음)과 knockback 의 Math.random 보정.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HurtKnockbackParityTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Entity.Damage MOB = new Entity.Damage("mob_attack", false, false, false);
    private static final Entity.Damage ARROW = new Entity.Damage("arrow", false, false, false);

    private static Zombie zombie(ZombieCombatTest.ArenaLevel level, double x) {
        level.dayTime = 18000;
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(x, -60, 0.5, 0, 0);
        return z;
    }

    @Test
    void projectilePushesAlongItsVelocity() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        var a = new Arrow(level, level.nextEntityId(), 5.5, -59, 0.5);   // +x 쪽에 있지만 +z 로 날아가는 화살
        a.setDeltaMovement(0, 0, 1);
        assertTrue(z.hurtByVia(ARROW, 1.0F, null, a));
        assertEquals(0.0, z.vx(), 1e-9, "위치가 아니라 속도 기준");
        assertEquals(0.4, z.vz(), 1e-6);
    }

    @Test
    void meleePushesAwayFromAttacker() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        var att = zombie(level, 3.5);
        assertTrue(z.hurtBy(MOB, 1.0F, att));
        assertEquals(-0.4, z.vx(), 1e-6);
        assertEquals(0.0, z.vz(), 1e-9);
    }

    @Test
    void noSourceStillKnocksBack() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        // generic 은 no_knockback 태그라 넉백 없음 — 태그 밖 종류로 원인만 비운다
        assertTrue(z.hurtBy(MOB, 1.0F, null));
        assertEquals(0.4, Math.hypot(z.vx(), z.vz()), 1e-6, "(0,0) 은 Math.random 방향으로 0.4");
    }

    @Test
    void nearZeroDirectionUsesMathRandomNotEntityRandom() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        var rnd = (LegacyRandomSource) z.random();
        long before = rnd.rawState();
        z.knockback(0.4, 1.0E-3, 0.0);   // 1e-6 < 1e-5F → 다시 뽑기
        assertEquals(before, rnd.rawState(), "엔티티 난수를 쓰지 않는다");
        assertEquals(0.4, Math.hypot(z.vx(), z.vz()), 1e-6);
        var z2 = zombie(level, 0.5);
        z2.knockback(0.4, 4.0E-3, 0.0);   // 1.6e-5 ≥ 1e-5F → 방향 그대로
        assertEquals(-0.4, z2.vx(), 1e-6);
        assertEquals(0.0, z2.vz(), 1e-12);
    }

    @Test
    void fireworkAndPotionUseTargetMinusSelf() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        var f = new FireworkRocketEntity(level, level.nextEntityId());
        f.moveTo(5.5, -59, 0.5, 0, 0);
        f.setDeltaMovement(0, 0, 1);   // 속도는 무시
        assertTrue(z.hurtByVia(MOB, 1.0F, null, f));
        assertEquals(-0.4, z.vx(), 1e-6, "폭죽 반대쪽(-x)으로");
        assertEquals(0.0, z.vz(), 1e-9);

        var z2 = zombie(level, 20.5);
        var pot = new ThrownPotion(level, level.nextEntityId());
        pot.moveTo(20.5, -59, 5.5, 0, 0);
        pot.setDeltaMovement(1, 0, 0);
        assertTrue(z2.hurtByVia(MOB, 1.0F, null, pot));
        assertEquals(0.0, z2.vx(), 1e-9);
        assertEquals(-0.4, z2.vz(), 1e-6, "물약 반대쪽(-z)으로");
    }

    /** 플레이어 DamageSource 경로: 직접 원인이 화살이면 위치가 아니라 화살 속도 쪽으로 밀린다(속도 패킷으로 확인). */
    @Test
    void playerDamageSourceProjectileUsesVelocity() {
        var level = new ZombieCombatTest.ArenaLevel();
        var ch = new io.netty.channel.embedded.EmbeddedChannel();
        var p = new Player(7, java.util.UUID.randomUUID(), "Steve", ch);
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(7.5, -60, 0.5, 90, 0, true);
        PlayerVitals.Surroundings dry = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
        for (int i = 0; i < 100; i++) p.vitals().tick(dry);
        level.player = p;
        p.applyMove(7.5, -60, 0.5, 90, 0, true);   // 기준 이동량을 0 으로
        var a = new Arrow(level, level.nextEntityId(), 12.5, -59, 0.5);   // +x 쪽, +z 로 날아감
        a.setDeltaMovement(0, 0, 1);
        drain(ch);
        assertTrue(p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageSource.by(
                org.domi.furnace.server.entity.damage.DamageType.ARROW, a, null), 1.0F));
        p.sendPendingKnockback();
        int[] m = lastMotion(ch);
        assertNotNull(m, "속도 패킷");
        assertEquals(0, m[0]);
        assertEquals(SetEntityMotionEncode.z(0.4), m[2]);
    }

    private static final class SetEntityMotionEncode {
        static int z(double v) { return (short) org.domi.furnace.server.net.protocol.play.SetEntityMotion.encode(v); }
    }

    private static void drain(io.netty.channel.embedded.EmbeddedChannel ch) {
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) ((io.netty.buffer.ByteBuf) o).release();
    }

    private static int[] lastMotion(io.netty.channel.embedded.EmbeddedChannel ch) {
        ch.runPendingTasks();
        int[] r = null;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            io.netty.buffer.ByteBuf bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == org.domi.furnace.server.net.protocol.play.SetEntityMotion.ID) {
                org.domi.furnace.server.net.io.Types.readVarInt(bb);
                r = new int[] {bb.readShort(), bb.readShort(), bb.readShort()};
            }
            bb.release();
        }
        return r;
    }
}
