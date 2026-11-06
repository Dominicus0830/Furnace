// 마을 포위(VillageSiege): 자정 1/10 굴림, 플레이어 둘레 32칸 원 위의 점, 3틱 간격 20마리, 난수 순서를 손계산 미러와 대조.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.spawn.VillageSiege;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillageSiegeTest {

    @BeforeAll
    static void install() throws Exception { TestRecipes.manager(); }

    /** 빛 0(어둡다)·모든 칸이 마을인 밤 레벨 — 몹 스폰 규칙의 난수 소비를 고정한다(하늘빛 nextInt(32), 밝기 nextInt(8)). */
    static class SiegeLevel extends PatrolSpawnerTest.PatrolLevel {
        boolean village = true;
        final java.util.List<Pos> difficultyAt = new java.util.ArrayList<>();

        @Override
        public org.domi.furnace.server.entity.DifficultyInstance getCurrentDifficultyAt(Pos pos) {
            difficultyAt.add(pos);
            return super.getCurrentDifficultyAt(pos);
        }

        SiegeLevel(long seed) {
            super(seed);
            day = 18000L;
        }

        @Override public boolean isCloseToVillage(Pos pos, int i) { return village; }
        @Override public long gameTime() { return day; }
        @Override public int getSkyLight(Pos pos) { return 0; }
        @Override public int getBlockLight(Pos pos) { return 0; }
        @Override public int getRawBrightness(Pos pos, int skyDarken) { return 0; }
    }

    private static Player player(GameMode mode) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        return p;
    }

    private static long seed(boolean tonight) {
        for (long s = 1; ; s++) if ((RandomSource.create(s).nextInt(10) == 0) == tonight) return s;
    }

    private static List<Zombie> zombies(SiegeLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof Zombie).map(e -> (Zombie) e).toList();
    }

    @Test
    void timeOfDayIsExactlyHalfOnlyAtMidnight() {
        assertEquals(0.5F, VillageSiege.timeOfDay(18000L));
        assertEquals(0.5F, VillageSiege.timeOfDay(18000L + 24000L * 7));
        assertNotEquals(0.5F, VillageSiege.timeOfDay(17999L));
        assertNotEquals(0.5F, VillageSiege.timeOfDay(18001L));
        assertEquals(0.0F, VillageSiege.timeOfDay(6000L), "정오");
    }

    @Test
    void siegeSpawnsTwentyZombiesEveryThirdTickInVanillaRandomOrder() {
        long seed = seed(true);
        var level = new SiegeLevel(seed);
        var siege = new VillageSiege();
        var players = List.of(player(GameMode.SURVIVAL));

        assertEquals(1, siege.tick(level, players, true));
        assertEquals(VillageSiege.State.TONIGHT, siege.state());
        assertTrue(siege.hasSetupSiege());
        assertEquals(19, siege.zombiesToSpawn(), "첫 틱에 포위를 정하고 곧바로 한 마리");

        // 바닐라 순서: nextInt(10)==0 → 각도 nextFloat → 칸 굴림(x, z, 하늘빛 32, 밝기 8) → 첫 좀비 칸 굴림 x, z
        RandomSource m = RandomSource.create(seed);
        assertEquals(0, m.nextInt(10));
        float f = m.nextFloat() * (float) (Math.PI * 2);
        int sx = 8 + Mth.floor(Mth.cos(f) * 32.0F), sz = 8 + Mth.floor(Mth.sin(f) * 32.0F);
        m.nextInt(16);
        m.nextInt(16);
        m.nextInt(32);
        m.nextInt(8);
        int zx = sx + m.nextInt(16) - 8, zz = sz + m.nextInt(16) - 8;
        List<Zombie> first = zombies(level);
        assertEquals(1, first.size());
        String dbg = "player=" + players.get(0).blockPosition() + " siege=" + siege.spawnPos() + " s=(" + sx + "," + sz + ") z=(" + zx + "," + zz + ") actual=(" + first.get(0).x() + "," + first.get(0).z() + ")";
        assertEquals(zx + 0.5, first.get(0).x(), 1e-9, dbg);
        assertEquals(zz + 0.5, first.get(0).z(), 1e-9, dbg);
        assertEquals(level.getWorldSurfaceHeight(zx, zz), first.get(0).y(), 1e-9);

        level.day = 18001L;   // 자정 굴림(시각 == 0.5)은 그 한 틱뿐 — 이후 틱은 굴리지 않는다
        // 다음 두 틱은 쉬고(nextSpawnTime 2 → 0), 세 번째 틱마다 한 마리
        assertEquals(0, siege.tick(level, players, true));
        assertEquals(0, siege.tick(level, players, true));
        assertEquals(1, zombies(level).size());
        assertEquals(1, siege.tick(level, players, true));
        assertEquals(2, zombies(level).size());
        assertEquals(18, siege.zombiesToSpawn());
        for (int i = 0; i < 3 * 18; i++) siege.tick(level, players, true);
        assertEquals(20, zombies(level).size(), "포위 한 번에 20마리");
        assertEquals(0, siege.zombiesToSpawn());
        assertEquals(VillageSiege.State.TONIGHT, siege.state());
        siege.tick(level, players, true);
        siege.tick(level, players, true);
        assertEquals(1, siege.tick(level, players, true));
        assertEquals(VillageSiege.State.DONE, siege.state(), "다 내면 끝");
        assertEquals(20, zombies(level).size());
    }

    @Test
    void conditionsBlockSiege() {
        long yes = seed(true);
        var players = List.of(player(GameMode.SURVIVAL));

        var noRoll = new SiegeLevel(seed(false));
        var s0 = new VillageSiege();
        assertEquals(0, s0.tick(noRoll, players, true));
        assertEquals(VillageSiege.State.DONE, s0.state(), "1/10 굴림에 지면 오늘 밤은 없다");
        assertEquals(0, zombies(noRoll).size());

        var day = new SiegeLevel(yes);
        day.day = 6000L;
        var s1 = new VillageSiege();
        assertEquals(0, s1.tick(day, players, true));
        assertEquals(VillageSiege.State.DONE, s1.state());

        var peaceful = new SiegeLevel(yes);
        var s2 = new VillageSiege();
        assertEquals(0, s2.tick(peaceful, players, false), "적대 몹 스폰이 꺼지면 굴리지도 않는다");
        assertEquals(VillageSiege.State.DONE, s2.state());

        var wild = new SiegeLevel(yes);
        wild.village = false;
        var s3 = new VillageSiege();
        assertEquals(0, s3.tick(wild, players, true), "마을 밖 플레이어에겐 포위를 못 정한다");
        assertFalse(s3.hasSetupSiege());
        assertEquals(VillageSiege.State.TONIGHT, s3.state(), "굴림은 이미 했다");

        var spectator = new SiegeLevel(yes);
        var s4 = new VillageSiege();
        assertEquals(0, s4.tick(spectator, List.of(player(GameMode.SPECTATOR)), true));
        assertFalse(s4.hasSetupSiege());
    }

    @Test
    void siegeResetsWhenDayComes() {
        var level = new SiegeLevel(seed(true));
        var siege = new VillageSiege();
        var players = List.of(player(GameMode.SURVIVAL));
        siege.tick(level, players, true);
        assertTrue(siege.hasSetupSiege());
        level.day = 6000L;
        assertEquals(0, siege.tick(level, players, true));
        assertFalse(siege.hasSetupSiege());
        assertEquals(VillageSiege.State.DONE, siege.state());
    }

    @Test
    void zombieDifficultyIsTakenAtOriginBeforeMoveTo() {
        var level = new SiegeLevel(seed(true));
        var siege = new VillageSiege();
        siege.tick(level, List.of(player(GameMode.SURVIVAL)), true);
        // 바닐라: finalizeSpawn(getCurrentDifficultyAt(zombie.blockPosition())) 이 moveTo 보다 먼저 — 아직 원점
        assertEquals(List.of(new Pos(0, 0, 0)), level.difficultyAt);
        assertNotEquals(new Pos(0, 0, 0), zombies(level).get(0).blockPosition());
    }

    // ------------------------------------------------------------------ 서버 길: 차원 하나의 포위, region 보고, 넘긴 일은 늦게 돈다

    private static Player player(int eid, double x, double z) {
        var p = new Player(eid, java.util.UUID.randomUUID(), "P" + eid, new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, z, 0, 0, true);
        return p;
    }

    /** 넘긴 일을 모아 두었다가 run 에서 돌리는 Host(서버의 다음 region 틱 흉내). */
    static final class Deferred implements VillageSiege.Host {
        final SiegeLevel level;
        final List<Player> players;
        final RandomSource roll;
        final java.util.ArrayDeque<Runnable> work = new java.util.ArrayDeque<>();

        Deferred(SiegeLevel level, List<Player> players, RandomSource roll) {
            this.level = level;
            this.players = players;
            this.roll = roll;
        }

        @Override public boolean isDay() { return level.isDay(); }
        @Override public float timeOfDay() { return VillageSiege.timeOfDay(level.day); }
        @Override public long gameTime() { return level.day; }
        @Override public RandomSource random() { return roll; }
        @Override public Player candidate(VillageSiege siege) { return siege.reportedCandidate(players, level.day); }
        @Override public void onPlayer(Player p, java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> w) { work.add(() -> w.accept(level)); }
        @Override public void atBlock(int x, int z, java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> w) { work.add(() -> w.accept(level)); }

        void run() { while (!work.isEmpty()) work.poll().run(); }
    }

    @Test
    void serverSiegePicksFirstPlayerInLevelOrderFromRegionReports() {
        var level = new SiegeLevel(1L);
        Player first = player(5, 8.5, 8.5), second = player(7, 60.5, 60.5);
        var host = new Deferred(level, List.of(first, second), RandomSource.create(seed(true)));
        var siege = new VillageSiege();

        siege.report(level, List.of(first, second));   // 자정 전: 찾는 중이 아니라 보고를 받지 않는다
        assertNull(siege.reportedCandidate(host.players, level.day));

        assertEquals(0, siege.tick(host, true), "자정 굴림 — 아직 아무 region 도 알리지 않았다");
        assertEquals(VillageSiege.State.TONIGHT, siege.state());
        // 두 region 이 서로 다른 순서로 알린다: 나중 플레이어의 region 이 먼저
        siege.report(level, List.of(second));
        siege.report(level, List.of(first));
        assertSame(first, siege.reportedCandidate(host.players, level.day), "바닐라 level.players() 순서의 첫 사람");

        level.day = 18001L;
        assertEquals(0, siege.tick(host, true), "플레이어 region 에 맡기고 답을 기다린다");
        assertFalse(siege.hasSetupSiege());
        assertEquals(0, siege.tick(host, true), "답이 오기 전에는 다시 맡기지 않는다");
        assertEquals(1, host.work.size());
        host.run();
        assertTrue(siege.hasSetupSiege());
        assertEquals(20, siege.zombiesToSpawn());
        Pos c = siege.spawnPos();
        Pos fp = first.blockPosition();
        double d = Math.hypot(c.x() - fp.x(), c.z() - fp.z());
        assertTrue(d > 30 && d < 34, "첫 플레이어 둘레 32칸 원: " + d);

        assertEquals(1, siege.tick(host, true));
        assertEquals(19, siege.zombiesToSpawn());
        assertEquals(0, zombies(level).size(), "스폰은 그 칸 region 에서 — 아직 안 돌았다");
        host.run();
        assertEquals(1, zombies(level).size());
        for (int i = 0; i < 3 * 19 + 3; i++) {
            siege.tick(host, true);
            host.run();
        }
        assertEquals(20, zombies(level).size(), "밤 하나에 한 번, 20마리");
        assertEquals(VillageSiege.State.DONE, siege.state());
    }

    @Test
    void serverSiegeDropsLateSetupAnswerAfterDaybreak() {
        var level = new SiegeLevel(1L);
        Player p = player(5, 8.5, 8.5);
        var host = new Deferred(level, List.of(p), RandomSource.create(seed(true)));
        var siege = new VillageSiege();
        siege.tick(host, true);
        siege.report(level, List.of(p));
        level.day = 18001L;
        siege.tick(host, true);
        assertEquals(1, host.work.size());
        level.day = 24000L + 6000L;   // 답이 오기 전에 날이 밝았다
        siege.tick(host, true);
        host.run();
        assertFalse(siege.hasSetupSiege(), "늦은 답은 버린다");
        assertEquals(VillageSiege.State.DONE, siege.state());
        assertEquals(0, zombies(level).size());
    }

    @Test
    void serverSiegeRetriesWhenPlayerLeftVillageOrRegionNeverAnswered() {
        var level = new SiegeLevel(1L);
        Player p = player(5, 8.5, 8.5);
        var host = new Deferred(level, List.of(p), RandomSource.create(seed(true)));
        var siege = new VillageSiege();
        siege.tick(host, true);
        siege.report(level, List.of(p));
        level.day = 18001L;
        siege.tick(host, true);
        level.village = false;   // 맡긴 사이 마을을 떠났다
        host.run();
        assertFalse(siege.hasSetupSiege());
        assertNull(siege.reportedCandidate(host.players, level.day), "떠난 사람의 보고는 지운다");
        level.village = true;
        siege.report(level, List.of(p));
        level.day = 18002L;
        siege.tick(host, true);
        host.work.clear();   // 맡은 region 이 답하지 않는다(접속 종료)
        level.day = 18002L + 41L;
        siege.report(level, List.of(p));
        siege.tick(host, true);
        assertEquals(1, host.work.size(), "기다림이 지나면 다시 맡긴다");
        host.run();
        assertTrue(siege.hasSetupSiege());
    }
}
