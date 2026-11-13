// 바닐라 LookAtPlayerGoal(플레이어 대상): forNonCombat().range(거리).selector(notRiding(mob)) 조건으로 눈높이에서 가장 가까운 플레이어를 고른다.
// 비전투 조건의 첫 검사는 canBeSeenByAnyone(관전자 아님·살아 있음)이라 크리에이티브도 바라본다. 이어 보기는 살아 있는지·거리·시간만 본다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ai.goal.LookAtPlayerGoal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LookAtPlayerConditionsTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final class PlayersLevel extends BlockPlacementTest.TestLevel {
        final List<Player> players = new ArrayList<>();

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public void forEachPlayer(Consumer<Player> c) { for (Player p : players) c.accept(p); }
            };
        }
    }

    private static Player player(PlayersLevel level, int id, GameMode mode, double x, double z) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(x, -60, z, 0, 0, true);
        level.players.add(p);
        return p;
    }

    private static Cow cow(PlayersLevel level) {
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(cow);
        return cow;
    }

    @Test
    void looksAtCreativeButNotSpectator() {
        var level = new PlayersLevel();
        var cow = cow(level);
        var goal = new LookAtPlayerGoal(cow, 8.0F, 1.0F);
        player(level, 100, GameMode.SPECTATOR, 2.5, 0.5);
        assertFalse(goal.canUse(), "관전자는 보지 않는다");
        var creative = player(level, 101, GameMode.CREATIVE, 4.5, 0.5);
        assertTrue(goal.canUse(), "크리에이티브는 본다(canBeSeenByAnyone)");
        goal.start();
        goal.tick();
        creative.vitals().loadGameTypes(null, GameMode.SPECTATOR, false);
        assertTrue(goal.canContinueToUse(), "이어 보기는 관전자 전환을 보지 않는다(바닐라)");
    }

    @Test
    void ignoresPlayerRidingIt() {
        var level = new PlayersLevel();
        var cow = cow(level);
        var goal = new LookAtPlayerGoal(cow, 8.0F, 1.0F);
        var rider = player(level, 100, GameMode.SURVIVAL, 0.5, 0.5);
        assertTrue(rider.startRiding(cow, true));
        assertFalse(goal.canUse(), "나를 탄 플레이어는 보지 않는다(notRiding)");
        player(level, 101, GameMode.SURVIVAL, 6.5, 0.5);
        assertTrue(goal.canUse());
    }

    @Test
    void needsLineOfSightAndRange() {
        var level = new PlayersLevel();
        var cow = cow(level);
        var goal = new LookAtPlayerGoal(cow, 8.0F, 1.0F);
        player(level, 100, GameMode.SURVIVAL, 9.5, 0.5);
        assertFalse(goal.canUse(), "거리 밖");
        level.players.clear();
        player(level, 101, GameMode.SURVIVAL, 5.5, 0.5);
        int stone = B.defaultState("minecraft:stone");
        for (int z = -3; z <= 3; z++) for (int y = -60; y <= -57; y++) level.setBlock(new Pos(3, y, z), stone, BlockLevel.UPDATE_ALL);
        assertFalse(goal.canUse(), "벽 뒤는 보지 않는다(시야 검사)");
    }

    @Test
    void nonCombatSeesCreativeCombatDoesNot() {
        var level = new PlayersLevel();
        var cow = cow(level);
        var creative = player(level, 100, GameMode.CREATIVE, 2.5, 0.5);
        assertTrue(TargetingConditions.forNonCombat().range(8.0).test(cow, creative));
        assertFalse(TargetingConditions.forCombat().range(8.0).test(cow, creative));
        var spectator = player(level, 101, GameMode.SPECTATOR, 2.5, 1.5);
        assertFalse(TargetingConditions.forNonCombat().range(8.0).test(cow, spectator));
    }

    @Test
    void threadSafeRandomKeepsStateAcrossThreads() throws Exception {
        // 여러 스레드가 함께 뽑아도 예외·손실 없이 고르게 나온다(Sensor 시작 틱 난수).
        var shared = RandomSource.createThreadSafeFor(org.domi.furnace.server.block.RandomSeeds.key("test"), 0);
        int threads = 4, each = 20000;
        var out = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) pool.submit(() -> { for (int i = 0; i < each; i++) out.add(shared.nextInt(1000)); });
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(threads * each, out.size());
        int[] hist = new int[1000];
        for (int v : out) hist[v]++;
        for (int h : hist) assertTrue(h > 30 && h < 130, "고르게 나온다: " + h);
    }
}
