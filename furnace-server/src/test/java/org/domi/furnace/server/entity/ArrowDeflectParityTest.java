// 화살이 피해를 못 주면 deflect(REVERSE) → ×0.2, 멈추면 (줍기 가능 시) 아이템 드롭.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArrowDeflectParityTest {

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

    private static Arrow blockedArrow(ZombieCombatTest.ArenaLevel level, Zombie z, double speed) {
        assertTrue(z.hurtBy(MOB, 10.0F, null));   // 무적 시간 + lastHurt 10 → 화살 피해는 실패
        var a = new Arrow(level, level.nextEntityId(), 0.5, -59, 0.5);
        a.setDeltaMovement(speed, 0, 0);
        level.addFreshEntity(a);
        return a;
    }

    @Test
    void deflectRotatesByRandomAndSetsImpulse() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        var a = blockedArrow(level, z, 1.0);
        a.setYRot(10.0F);
        a.yRotO = 10.0F;
        ((LegacyRandomSource) a.random()).setSeed(1234L);
        var ref = new LegacyRandomSource(1234L);
        float rot = 170.0F + ref.nextFloat() * 20.0F;
        a.hasImpulse = false;
        a.onHitEntity(new Projectile.Hit(z, null, 0, 0.5, -59, 0.5));
        assertEquals(10.0F + rot, a.yRot());
        assertEquals(10.0F + rot, a.yRotO);
        assertTrue(rot >= 170.0F && rot < 190.0F);
        assertEquals(ref.rawState(), ((LegacyRandomSource) a.random()).rawState(), "난수는 nextFloat 1회만");
        assertTrue(a.hasImpulse);
        assertEquals(-0.1, a.vx(), 1e-9);
        assertFalse(a.isRemoved());
    }

    @Test
    void stoppedPickupArrowDropsItem() {
        var level = new ZombieCombatTest.ArenaLevel();
        var z = zombie(level, 0.5);
        var a = blockedArrow(level, z, 1.0E-3);
        a.pickup = AbstractArrow.Pickup.ALLOWED;
        a.onHitEntity(new Projectile.Hit(z, null, 0, 0.5, -59, 0.5));
        assertTrue(a.isRemoved());
        var items = level.getEntities(null, new AABB(-2, -62, -2, 3, -57, 3), e -> e instanceof ItemEntity);
        assertEquals(1, items.size());
        assertEquals(a.y() + 0.1F, items.get(0).y(), 1e-9);

        var z2 = zombie(level, 10.5);
        var b = blockedArrow(level, z2, 1.0E-3);
        b.moveTo(10.5, -59, 0.5, 0, 0);
        b.onHitEntity(new Projectile.Hit(z2, null, 0, 10.5, -59, 0.5));
        assertTrue(b.isRemoved());
        assertTrue(level.getEntities(null, new AABB(8, -62, -2, 13, -57, 3), e -> e instanceof ItemEntity).isEmpty(), "줍기 불가면 안 떨군다");
    }
}
