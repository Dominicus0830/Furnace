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

        SiegeLevel(long seed) {
            super(seed);
            day = 18000L;
        }

        @Override public boolean isCloseToVillage(Pos pos, int i) { return village; }
        @Override public int getSkyLight(Pos pos) { return 0; }
        @Override public int getBlockLight(Pos pos) { return 0; }
        @Override public int getRawBrightness(Pos pos, int skyDarken) { return 0; }
    }

    private static Player player(GameMode mode) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
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
}
