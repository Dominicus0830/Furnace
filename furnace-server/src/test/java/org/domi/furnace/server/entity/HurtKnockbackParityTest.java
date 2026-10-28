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
}
