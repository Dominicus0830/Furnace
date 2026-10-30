// 생물끼리 밀기(LivingEntity.pushEntities)와 maxEntityCramming: 밀기 속도, 난수 호출 조건, cramming 피해, 팀 충돌 규칙, 예외 엔티티.
// 기대값은 바닐라 1.21.1 디컴파일(btn.r, bsr.h, bsw.a)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.scores.PlayerTeam.CollisionRule;
import org.domi.furnace.server.scores.Scoreboard;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PushEntitiesTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Arena extends BlockPlacementTest.TestLevel {
        int cramming;
        Scoreboard board;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public int gameRuleInt(String name) { return name.equals("maxEntityCramming") ? cramming : super.gameRuleInt(name); }
                @Override public Scoreboard scoreboard() { return board; }
            };
        }
    }

    private static Cow cow(Arena level, double x, double z) {
        Cow c = new Cow(level, level.nextEntityId());
        c.moveTo(x, -60, z, 0, 0);
        level.addFreshEntity(c);
        return c;
    }

    /** 첫 nextInt(4) 가 want 인 시드(java.util.Random 과 같은 선형합동법). */
    private static long seedWithFirstNextInt4(boolean zero) {
        for (long s = 1; ; s++) if ((new Random(s).nextInt(4) == 0) == zero) return s;
    }

    private static void seed(Entity e, long s) { ((LegacyRandomSource) e.random()).setSeed(s); }

    @Test
    void overlappingMobsPushApartBy005() {
        var level = new Arena();
        Cow a = cow(level, 0.5, 0.5), b = cow(level, 0.9, 0.5);
        a.pushEntities();
        // b.push(a): dx = a.x - b.x = -0.4, d = sqrt(0.4), dx/d = -sqrt(0.4), 1/d > 1 이라 1, 곱 0.05F. b 는 -dx 만큼(+), a 는 dx 만큼(-).
        double v = Math.sqrt(0.4) * (double) 0.05F;
        assertEquals(0.0316227766, v, 1e-9);
        assertEquals(v, b.vx(), 1e-12);
        assertEquals(-v, a.vx(), 1e-12);
        assertEquals(0.0, a.vz(), 0.0);
        assertEquals(0.0, b.vz(), 0.0);
    }

    @Test
    void closeMobsUseTheCappedFactor() {
        var level = new Arena();
        Cow a = cow(level, 0.5, 0.5), b = cow(level, 0.5, 0.69);
        a.pushEntities();
        // dz = -0.19: d = sqrt(0.19) = 0.43588989, dz/d = -0.43588989, 1/d = 2.29 > 1 → 1. 곱 0.05F.
        double v = Math.sqrt(0.19) * (double) 0.05F;
        assertEquals(v, b.vz(), 1e-12);
        assertEquals(-v, a.vz(), 1e-12);
    }

    @Test
    void sameXZBelowThresholdDoesNotPush() {
        var level = new Arena();
        Cow a = cow(level, 0.5, 0.5), b = cow(level, 0.505, 0.5);
        a.pushEntities();   // d = 0.005 < 0.01F
        assertEquals(0.0, a.vx(), 0.0);
        assertEquals(0.0, b.vx(), 0.0);
    }

    @Test
    void crammingRollsOnlyWhenListExceedsRule() {
        var level = new Arena();
        level.cramming = 3;
        Cow s = cow(level, 0.5, 0.5);
        cow(level, 0.55, 0.5);
        cow(level, 0.45, 0.5);
        long sd = seedWithFirstNextInt4(true);
        seed(s, sd);
        s.pushEntities();   // 후보 2 > 규칙-1(2) 아님 → 난수 안 부름
        assertEquals(new LegacyRandomSource(sd).nextInt(4), s.random().nextInt(4));
        assertEquals(s.maxHealth(), s.health(), 0.0F);
    }

    @Test
    void crammingHurtsWhenRolledZeroAndCrowded() {
        var level = new Arena();
        level.cramming = 3;
        Cow s = cow(level, 0.5, 0.5);
        cow(level, 0.55, 0.5);
        cow(level, 0.45, 0.5);
        cow(level, 0.5, 0.55);
        seed(s, seedWithFirstNextInt4(true));
        float before = s.health();
        s.pushEntities();   // 후보 3 > 2, nextInt(4)==0, 탈것 안 탄 3 > 2 → cramming 6 (소 방어 0)
        assertEquals(before - 6.0F, s.health(), 0.0F);
    }

    @Test
    void crammingSkippedWhenRolledNonZero() {
        var level = new Arena();
        level.cramming = 3;
        Cow s = cow(level, 0.5, 0.5);
        cow(level, 0.55, 0.5);
        cow(level, 0.45, 0.5);
        cow(level, 0.5, 0.55);
        long sd = seedWithFirstNextInt4(false);
        seed(s, sd);
        float before = s.health();
        s.pushEntities();
        assertEquals(before, s.health(), 0.0F);
        // 난수는 정확히 한 번 소비됐다.
        var ref = new LegacyRandomSource(sd);
        ref.nextInt(4);
        assertEquals(ref.nextInt(4), s.random().nextInt(4));
    }

    @Test
    void ruleZeroNeverRollsOrHurts() {
        var level = new Arena();
        level.cramming = 0;
        Cow s = cow(level, 0.5, 0.5);
        for (int i = 0; i < 30; i++) cow(level, 0.5 + i * 0.001, 0.5);
        long sd = seedWithFirstNextInt4(true);
        seed(s, sd);
        float before = s.health();
        s.pushEntities();
        assertEquals(before, s.health(), 0.0F);
        assertEquals(new LegacyRandomSource(sd).nextInt(4), s.random().nextInt(4));
    }

    @Test
    void passengersAreNotCountedForDamage() {
        var level = new Arena();
        level.cramming = 3;
        Cow s = cow(level, 0.5, 0.5);
        Boat boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        Cow p1 = cow(level, 0.5, 0.5), p2 = cow(level, 0.5, 0.5);
        assertTrue(p1.startRiding(boat));
        assertTrue(p2.startRiding(boat));
        seed(s, seedWithFirstNextInt4(true));
        float before = s.health();
        s.pushEntities();   // 후보 = 배 + 승객 2 = 3 > 2 → 굴림 0, 하지만 탈것에 안 탄 것은 배 하나 → 피해 없음
        assertEquals(before, s.health(), 0.0F);
    }

    @Test
    void spectatorsAndNonPushablesAreIgnored() {
        var level = new Arena();
        level.cramming = 2;
        Cow s = cow(level, 0.5, 0.5);
        ArmorStand stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(stand);
        Bat bat = new Bat(level, level.nextEntityId());
        bat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(bat);
        long sd = seedWithFirstNextInt4(true);
        seed(s, sd);
        s.pushEntities();   // 갑옷 거치대·박쥐는 isPushable=false → 후보 0
        assertEquals(new LegacyRandomSource(sd).nextInt(4), s.random().nextInt(4));
        assertEquals(0.0, s.vx(), 0.0);
    }

    @Test
    void teamNeverRuleBlocksPushing() {
        var level = new Arena();
        level.board = new Scoreboard();
        var team = level.board.addPlayerTeam("t");
        team.collisionRule(CollisionRule.NEVER);
        Cow a = cow(level, 0.5, 0.5), b = cow(level, 0.9, 0.5);
        level.board.addPlayerToTeam(b.uuid().toString(), team);   // 상대 규칙 never
        a.pushEntities();
        assertEquals(0.0, b.vx(), 0.0);
        assertEquals(0.0, a.vx(), 0.0);
        team.collisionRule(CollisionRule.ALWAYS);
        a.pushEntities();
        assertNotEquals(0.0, b.vx());
    }

    @Test
    void teamRulesFollowVanillaPredicate() {
        var level = new Arena();
        level.board = new Scoreboard();
        var red = level.board.addPlayerTeam("red");
        // 디컴파일 그대로: PUSH_OWN_TEAM 이고 같은 팀이면 거짓, 그 밖에 PUSH_OTHER_TEAMS 면 같은 팀일 때만 참.
        red.collisionRule(CollisionRule.PUSH_OWN_TEAM);
        Cow a = cow(level, 0.5, 0.5), b = cow(level, 0.9, 0.5);
        level.board.addPlayerToTeam(a.uuid().toString(), red);
        level.board.addPlayerToTeam(b.uuid().toString(), red);
        a.pushEntities();
        assertEquals(0.0, b.vx(), 0.0);   // 같은 팀 + OWN → 거짓
        red.collisionRule(CollisionRule.PUSH_OTHER_TEAMS);
        a.pushEntities();
        assertNotEquals(0.0, b.vx());     // 같은 팀 + OTHER → 참
        b.setDeltaMovement(0, 0, 0);
        a.setDeltaMovement(0, 0, 0);
        level.board.removePlayerFromTeam(b.uuid().toString());   // 다른 팀(없음) + 내 규칙 OTHER → 거짓
        a.pushEntities();
        assertEquals(0.0, b.vx(), 0.0);
    }

    private static Player player(GameMode mode) {
        var p = new Player(7, java.util.UUID.randomUUID(), "Steve", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    @Test
    void playerPathPushesMobAwayByHandComputedAmount() {
        var level = new Arena();
        Cow c = cow(level, 0.8, 0.5);
        level.pushScratch.pushFor(level, player(GameMode.SURVIVAL));
        // pushByPlayer: dx = 0.8 - 0.5 = 0.3, d = sqrt(0.3), dx/d = sqrt(0.3), 1/d > 1 → 1, 곱 0.05F.
        assertEquals(Math.sqrt(0.3) * (double) 0.05F, c.vx(), 1e-12);
        assertEquals(0.0, c.vz(), 0.0);
    }

    @Test
    void spectatorPlayerStillRollsButDoesNotPush() {
        var level = new Arena();
        level.cramming = 2;
        Cow c = cow(level, 0.8, 0.5);
        cow(level, 0.7, 0.5);
        Player p = player(GameMode.SPECTATOR);
        long sd = seedWithFirstNextInt4(false);
        ((LegacyRandomSource) p.random()).setSeed(sd);
        level.pushScratch.pushFor(level, p);   // 후보 2 > 1 → 관전자여도 굴린다
        var ref = new LegacyRandomSource(sd);
        ref.nextInt(4);
        assertEquals(ref.nextInt(4), p.random().nextInt(4));
        assertEquals(0.0, c.vx(), 0.0);   // 밀기는 막힌다
    }

    @Test
    void armorStandPushesRideableMinecartWithinSqrt02Only() {
        var level = new Arena();
        ArmorStand stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(stand);
        Minecart near = new Minecart(level, level.nextEntityId()), far = new Minecart(level, level.nextEntityId());
        near.moveTo(0.5, -60, 0.6, 0, 0);   // 거리² 0.01
        far.moveTo(0.5, -60, 1.0, 0, 0);    // 거리² 0.25 > 0.2
        level.addFreshEntity(near);
        level.addFreshEntity(far);
        stand.pushEntities();
        assertTrue(near.vx() != 0.0 || near.vz() != 0.0);
        assertEquals(0.0, far.vx(), 0.0);
        assertEquals(0.0, far.vz(), 0.0);
    }

    @Test
    void armorStandOnlyPushesCloseRideableMinecarts() {
        var level = new Arena();
        ArmorStand stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(stand);
        Cow c = cow(level, 0.5, 0.5);
        stand.pushEntities();
        assertEquals(0.0, c.vx(), 0.0);
        assertEquals(0.0, c.vz(), 0.0);
    }
}
