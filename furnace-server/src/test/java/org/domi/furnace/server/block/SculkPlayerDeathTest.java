// M2: 플레이어 사망도 entity_die 를 낸다(ServerPlayer.die 첫 줄). 스컬크 촉매가 레벨×7(최대 100) 경험치를 가져가고, 없으면 경험치 구슬이 떨어진다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.entity.SculkCatalystBlockEntity;
import org.domi.furnace.server.block.types.SculkSpreader;
import org.domi.furnace.server.entity.ExperienceOrb;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerVitals;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.RegionLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SculkPlayerDeathTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;

    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                var cp = new org.domi.furnace.server.world.ChunkPos(cx, cz);
                var loaded = new CompletableFuture<CompletableFuture<?>>();
                core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
                loaded.get(30, TimeUnit.SECONDS).get(120, TimeUnit.SECONDS);
            }
        }
    }

    @AfterAll
    static void tearDown() { sched.close(); }

    /** 게임룰만 바꿀 수 있는 최소 환경(keepInventory 기본 꺼짐). */
    static final class TestEnv implements PlayerVitals.Env {
        final java.util.Map<String, Boolean> rules = new java.util.HashMap<>();
        public org.domi.furnace.server.world.Difficulty difficulty() { return org.domi.furnace.server.world.Difficulty.EASY; }
        public boolean gameRule(String name) { return rules.getOrDefault(name, !name.equals("keepInventory") && !name.equals("doImmediateRespawn")); }
        public boolean pvp() { return true; }
        public void broadcastSystemMessage(org.domi.furnace.api.text.Text message) {}
        public void broadcastAll(org.domi.furnace.server.net.protocol.Packet packet) {}
        public void broadcastToTrackers(int entityId, org.domi.furnace.server.net.protocol.Packet packet) {}
    }

    private static <T> T onRegion(Function<RegionLevel, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(0.5, 0.5, region -> {
            try {
                fut.complete(f.apply(region.level()));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(60, TimeUnit.SECONDS);
    }

    private static Player player(RegionLevel l, TestEnv env, double x, double z, int xpLevel, GameMode mode) {
        var p = new Player(l.nextEntityId(), java.util.UUID.randomUUID(), "A" + l.nextEntityId(), new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, mode, false);
        p.setPosRaw(x, -60.0, z);
        p.currentLevel = l;
        p.vitals().setExperienceLevels(xpLevel);
        return p;
    }

    private static int orbXp(RegionLevel l) {
        int sum = 0;
        for (var e : l.entities().tickSnapshot()) if (e instanceof ExperienceOrb o && !o.isRemoved()) sum += o.value() * o.count();
        return sum;
    }

    private static int charge(SculkCatalystBlockEntity be) {
        return be.getSculkSpreader().getCursors().stream().mapToInt(SculkSpreader.ChargeCursor::getCharge).sum();
    }

    private static SculkCatalystBlockEntity catalyst(RegionLevel l, Pos pos) {
        l.setBlock(pos, B.defaultState(B.id("sculk_catalyst")), 3);
        return (SculkCatalystBlockEntity) l.getBlockEntity(pos);
    }

    @Test
    void playerDeathNearCatalystFeedsLevelTimesSevenAndDropsNoOrbs() throws Exception {
        onRegion(l -> {
            var be = catalyst(l, new Pos(-12, -61, -12));
            int before = orbXp(l);
            var p = player(l, new TestEnv(), -10.5, -11.5, 10, GameMode.SURVIVAL);
            p.vitals().setHealth(0.0F);
            assertTrue(p.vitals().wasExperienceConsumed(), "촉매가 경험치를 가져갔다");
            assertEquals(70, charge(be), "레벨 10 → 70");
            assertEquals(before, orbXp(l), "구슬은 떨어지지 않는다");
            assertTrue(B.prop("bloom").is(l.getBlockState(new Pos(-12, -61, -12))), "bloom 이 켜졌다");
            return null;
        });
    }

    @Test
    void chargeIsCappedAtHundred() throws Exception {
        onRegion(l -> {
            var be = catalyst(l, new Pos(-12, -61, 4));
            var p = player(l, new TestEnv(), -10.5, 4.5, 30, GameMode.SURVIVAL);
            p.vitals().setHealth(0.0F);
            assertEquals(100, charge(be));
            return null;
        });
    }

    @Test
    void withoutCatalystTheXpDropsAsOrbs() throws Exception {
        onRegion(l -> {
            int before = orbXp(l);
            var p = player(l, new TestEnv(), 12.5, 12.5, 5, GameMode.SURVIVAL);
            p.vitals().setHealth(0.0F);
            assertFalse(p.vitals().wasExperienceConsumed());
            assertEquals(before + 35, orbXp(l), "레벨 5 → 35 경험치 구슬");
            return null;
        });
    }

    @Test
    void keepInventoryRuleMeansNoXpAtAll() throws Exception {
        onRegion(l -> {
            var env = new TestEnv();
            env.rules.put("keepInventory", true);
            var be = catalyst(l, new Pos(-12, -61, 14));
            int before = orbXp(l);
            var p = player(l, env, -10.5, 14.5, 10, GameMode.SURVIVAL);
            p.vitals().setHealth(0.0F);
            assertEquals(0, charge(be), "getBaseExperienceReward = 0 이라 전하 없음");
            assertTrue(p.vitals().wasExperienceConsumed(), "그래도 촉매는 entity_die 를 받아 소비 처리한다");
            assertTrue(B.prop("bloom").is(l.getBlockState(new Pos(-12, -61, 14))), "bloom 은 켜진다");
            assertEquals(before, orbXp(l));
            return null;
        });
    }

    @Test
    void spectatorDropsNothing() throws Exception {
        onRegion(l -> {
            int before = orbXp(l);
            var p = player(l, new TestEnv(), 12.5, -11.5, 10, GameMode.SPECTATOR);
            assertEquals(0, p.vitals().baseExperienceReward());
            p.vitals().setHealth(0.0F);
            assertEquals(before, orbXp(l));
            return null;
        });
    }

    @Test
    void killedByAnotherPlayerAlsoWorksThroughTheAdvancementPath() throws Exception {
        onRegion(l -> {
            var be = catalyst(l, new Pos(4, -61, 14));
            var killer = player(l, new TestEnv(), 6.5, 14.5, 0, GameMode.SURVIVAL);
            var dying = player(l, new TestEnv(), 5.5, 14.5, 4, GameMode.SURVIVAL);
            dying.setLastHurtByMob(killer);   // getKillCredit() = 킬 크레딧이 플레이어 → kill_mob_near_sculk_catalyst 경로(발전 과제 없는 시험 플레이어는 조용히 넘어간다)
            dying.vitals().setHealth(0.0F);
            assertEquals("minecraft:generic", dying.vitals().deathType());
            assertEquals(28, charge(be));
            return null;
        });
    }

    @Test
    void consumedFlagResetsOnRespawn() throws Exception {
        onRegion(l -> {
            catalyst(l, new Pos(4, -61, -12));
            var p = player(l, new TestEnv(), 5.5, -11.5, 3, GameMode.SURVIVAL);
            p.vitals().setHealth(0.0F);
            assertTrue(p.vitals().wasExperienceConsumed());
            p.vitals().respawn();
            assertFalse(p.vitals().wasExperienceConsumed());
            return null;
        });
    }
}
