// 파괴수의 방패 막힘(기절·밀치기)과 좀비 근접 공격 쿨다운(바닐라 MeleeAttackGoal) 결정적 시험.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.entity.ai.goal.ZombieAttackGoal;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RavagerZombieAttackTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Ravager ravager(SkeletonCombatTest.ArenaLevel level, double x) {
        var r = new Ravager(level, level.nextEntityId());
        r.moveTo(x, -60, 0.5, 0, 0);
        r.setOnGround(true);
        level.addFreshEntity(r);
        return r;
    }

    private static Zombie zombie(SkeletonCombatTest.ArenaLevel level, double x) {
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(x, -60, 0.5, 0, 0);
        z.setOnGround(true);
        level.addFreshEntity(z);
        return z;
    }

    @Test
    void ravagerStunsOrKnocksBackOnShieldBlock() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        boolean stunned = false, knocked = false;
        for (int i = 0; i < 60 && !(stunned && knocked); i++) {
            var r = ravager(level, 0.5);
            var d = zombie(level, 2.5);
            d.setDeltaMovement(0, 0, 0);
            r.blockedByShield(d);
            if (r.getStunnedTick() == 40) {
                stunned = true;
                assertTrue(r.isImmobile(), "기절 중에는 움직이지 못한다");
            } else {
                knocked = true;
                assertEquals(0, r.getStunnedTick());
                assertEquals(2.0, d.vx(), 1e-9, "강한 밀치기: dx/(dx²)*4");
                assertEquals(0.2, d.vy(), 1e-9);
            }
            assertTrue(d.hurtMarked);
        }
        assertTrue(stunned && knocked, "두 갈래 모두 나온다");
    }

    @Test
    void ravagerIgnoresShieldWhileRoaring() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var r = ravager(level, 0.5);
        var tag = r.save();
        tag.putInt("RoarTick", 15);
        var loaded = (Ravager) Entities.load(level, tag);
        var d = zombie(level, 2.5);
        loaded.blockedByShield(d);
        assertEquals(0, loaded.getStunnedTick());
        assertEquals(0.0, d.vx(), 1e-12);
    }

    @Test
    void stunEndsWithRoar() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        Ravager r = null;
        for (int i = 0; i < 60 && (r == null || r.getStunnedTick() == 0); i++) {
            r = ravager(level, 0.5);
            r.blockedByShield(zombie(level, 2.5));
        }
        assertEquals(40, r.getStunnedTick());
        for (int t = 0; t < 40; t++) r.tick();
        assertEquals(0, r.getStunnedTick());
        assertTrue(r.getRoarTick() > 0 && r.getRoarTick() <= 20, "기절이 끝나면 포효: " + r.getRoarTick());
    }

    /** 바닐라 MeleeAttackGoal.start 는 공격 대기를 0 으로 되돌린다: 목표가 끊겼다 다시 시작하면 20틱 안이어도 곧바로 한 번 더 때린다(무적 시간은 별개). */
    @Test
    void zombieAttackGoalRestartSwingsImmediately() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        int[] swings = {0};
        var z = new Zombie(level, level.nextEntityId()) {
            @Override public boolean doHurtTarget(Targetable t) { swings[0]++; return true; }
        };
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.setOnGround(true);
        level.addFreshEntity(z);
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(1.2, -60, 0.5, 0, 0);
        level.addFreshEntity(v);
        z.setTarget(v);
        var g = new ZombieAttackGoal(z, 1.0, false);
        g.start();
        g.tick();
        assertEquals(1, swings[0]);
        for (int i = 0; i < 18; i++) g.tick();
        assertEquals(1, swings[0], "쿨다운 20틱 안에는 안 때린다");
        g.stop();
        g.start();
        g.tick();
        assertEquals(2, swings[0], "재시작 직후 곧바로 한 번 더");
        for (int i = 0; i < 19; i++) g.tick();
        assertEquals(2, swings[0]);
        g.tick();
        assertEquals(3, swings[0], "20틱 뒤 다음 공격");
    }
}
