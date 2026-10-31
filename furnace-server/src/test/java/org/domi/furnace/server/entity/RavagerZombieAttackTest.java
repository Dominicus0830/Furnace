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

    /** 기록용 파괴수: 소리 이름과 엔티티 이벤트를 모은다. */
    private static final class RecRavager extends Ravager {
        final java.util.List<String> sounds = new java.util.ArrayList<>();
        final java.util.List<Byte> events = new java.util.ArrayList<>();
        RecRavager(SkeletonCombatTest.ArenaLevel l) { super(l, l.nextEntityId()); }
        @Override public void playSound(String s, float v, float p) { sounds.add(s); }
        @Override public void broadcastEntityEvent(byte e) { events.add(e); }
    }

    private static long seedWhere(boolean low) {
        for (long s = 1; ; s++) if ((org.domi.furnace.server.block.RandomSource.create(s).nextDouble() < 0.5) == low) return s;
    }

    private static RecRavager seeded(SkeletonCombatTest.ArenaLevel level, long seed) {
        var r = new RecRavager(level);
        r.moveTo(0.5, -60, 0.5, 0, 0);
        r.setOnGround(true);
        ((org.domi.furnace.server.block.LegacyRandomSource) r.random).setSeed(seed);
        level.addFreshEntity(r);
        return r;
    }

    @Test
    void ravagerStunsOnLowRoll() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var r = seeded(level, seedWhere(true));
        var d = zombie(level, 2.5);
        d.setDeltaMovement(0, 0, 0);
        r.sounds.clear();
        r.events.clear();
        r.blockedByShield(d);
        assertEquals(40, r.getStunnedTick());
        assertTrue(r.isImmobile());
        assertTrue(r.sounds.contains("entity.ravager.stunned"), r.sounds.toString());
        assertEquals(java.util.List.of((byte) 39), r.events);
        assertTrue(d.vx() > 0.0 && d.vx() < 0.1, "push 로 조금만 밀림 vx=" + d.vx());
        assertTrue(d.hurtMarked);
    }

    @Test
    void ravagerKnocksBackOnHighRoll() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var r = seeded(level, seedWhere(false));
        var d = zombie(level, 2.5);
        d.setDeltaMovement(0, 0, 0);
        r.sounds.clear();
        r.events.clear();
        r.blockedByShield(d);
        assertEquals(0, r.getStunnedTick());
        assertEquals(2.0, d.vx(), 1e-9, "강한 밀치기: dx/(dx²)*4");
        assertEquals(0.2, d.vy(), 1e-9);
        assertFalse(r.sounds.contains("entity.ravager.stunned"));
        assertTrue(r.events.isEmpty());
        assertTrue(d.hurtMarked);
    }

    private static Player player(SkeletonCombatTest.ArenaLevel level, org.domi.furnace.server.world.GameMode mode, io.netty.channel.embedded.EmbeddedChannel ch) {
        var p = new Player(7, java.util.UUID.randomUUID(), "Steve", ch);
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(2.5, -60, 0.5, 90, 0, true);
        level.player = p;
        ch.runPendingTasks();
        while (ch.readOutbound() != null) { }
        return p;
    }

    private static int motionPackets(io.netty.channel.embedded.EmbeddedChannel ch) {
        ch.runPendingTasks();
        int n = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            io.netty.buffer.ByteBuf bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == org.domi.furnace.server.net.protocol.play.SetEntityMotion.ID) n++;
            bb.release();
        }
        return n;
    }

    @Test
    void ravagerStunPushesPlayerButNotSpectator() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var ch = new io.netty.channel.embedded.EmbeddedChannel();
        var p = player(level, org.domi.furnace.server.world.GameMode.SURVIVAL, ch);
        var r = seeded(level, seedWhere(true));
        r.blockedByShield(p);
        assertEquals(40, r.getStunnedTick());
        assertTrue(motionPackets(ch) >= 1, "플레이어가 밀렸다");
        var ch2 = new io.netty.channel.embedded.EmbeddedChannel();
        var sp = player(level, org.domi.furnace.server.world.GameMode.SPECTATOR, ch2);
        var r2 = seeded(level, seedWhere(true));
        r2.blockedByShield(sp);
        assertEquals(40, r2.getStunnedTick());
        assertEquals(0, motionPackets(ch2), "관전자는 밀리지 않는다");
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
        var r = seeded(level, seedWhere(true));
        r.blockedByShield(zombie(level, 2.5));
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
