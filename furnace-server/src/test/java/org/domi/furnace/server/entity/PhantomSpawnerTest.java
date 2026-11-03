// 팬텀 스포너(PhantomSpawner): 밤에 오래 못 잔 플레이어 위로만 난다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.spawn.PhantomSpawner;
import org.domi.furnace.server.stats.PlayerStats;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhantomSpawnerTest {

    @TempDir Path tmp;

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final class Night extends BlockPlacementTest.TestLevel {
        long day = 18000;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long dayTime() { return day; }
                @Override public int difficulty() { return 2; }
            };
        }
    }

    private Player player(int sinceRest) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(8.5, 70, 8.5, 0, 0, true);
        var stats = new PlayerStats(tmp, p.uuid());
        stats.set("minecraft:time_since_rest", sinceRest);
        p.stats(stats);
        return p;
    }

    private int run(Night level, Player p, boolean enemies) {
        var spawner = new PhantomSpawner();
        int n = 0;
        for (int i = 0; i < 120000; i++) n += spawner.tick(level, List.of(p), enemies);
        return n;
    }

    @Test
    void spawnsAboveSleeplessPlayerAtNight() {
        var level = new Night();
        int n = run(level, player(2_000_000_000), true);
        assertTrue(n > 0, "밤에 오래 못 잔 플레이어 위로 팬텀이 난다");
        for (Entity e : level.entities().tickSnapshot()) {
            assertEquals("minecraft:phantom", e.type().name());
            assertTrue(e.y() >= 90 && e.y() <= 105, "머리 위 20..34칸: " + e.y());
            assertTrue(Math.abs(e.x() - 9.0) <= 11 && Math.abs(e.z() - 9.0) <= 11);
        }
    }

    @Test
    void noSpawnWhenRestedByDayOrMonstersOff() {
        assertEquals(0, run(new Night(), player(100), true), "막 잔 플레이어(휴식 시간이 짧음)에게는 안 난다");
        var day = new Night();
        day.day = 1000;
        assertEquals(0, run(day, player(2_000_000_000), true), "낮에는 안 난다");
        assertEquals(0, run(new Night(), player(2_000_000_000), false), "적대 몹 스폰이 꺼지면 안 난다");
    }
}
