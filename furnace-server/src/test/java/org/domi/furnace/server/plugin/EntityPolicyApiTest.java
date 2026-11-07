// E3 API 를 실제 region 스레드로: 스폰 정책 손잡이, 청크·월드 엔티티 수, 자세·noPhysics, 주민 평판, 마을·POI, 습격.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.entity.GossipKind;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.entity.ReputationEvent;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.EntityCounts;
import org.domi.furnace.api.world.PoiOccupancy;
import org.domi.furnace.api.world.RaidStatus;
import org.domi.furnace.api.world.SpawnPolicy;
import org.domi.furnace.api.worldgen.SpawnCategory;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.raid.Raid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityPolicyApiTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private WorldHandle world;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
        world = host.worldHandle("minecraft:overworld");
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private void tickRegion() throws Exception {
        core.world().submitAtChunk(C00, r -> {
            for (int i = 0; i < 2; i++) r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    /** 자동 틱이 없으니 region 을 직접 틱해 엔티티 작업 대기열을 돌리고 결과를 기다린다. */
    private <T> T run(CompletableFuture<T> f) throws Exception {
        tickRegion();
        return f.get(10, TimeUnit.SECONDS);
    }

    private <T> T onRegion(java.util.function.Function<org.domi.furnace.server.world.Region, T> task) throws Exception {
        return core.world().submitAtChunk(C00, task::apply).get(30, TimeUnit.SECONDS);
    }

    private org.domi.furnace.api.entity.Entity spawnCow(double x, double z) throws Exception {
        return onRegion(r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(x, -60, z, 0, 0);
            level.addFreshEntity(cow);
            return EntityHandle.of(host, cow);
        });
    }

    // ------------------------------------------------------------------ 스폰 정책

    @Test
    void spawnPolicyHandleReadsAndWritesTheWorldPolicy() throws Exception {
        SpawnPolicy sp = world.spawnPolicy();
        assertSame(sp, world.spawnPolicy() instanceof SpawnPolicy ? sp : null);
        assertEquals(70, sp.vanillaCap(SpawnCategory.MONSTER));
        assertEquals(70, sp.categoryCap(SpawnCategory.MONSTER), "기본은 바닐라 상수");
        assertEquals(70, sp.playerCap(SpawnCategory.MONSTER));
        sp.setCategoryCap(SpawnCategory.MONSTER, 30);
        sp.setPlayerCap(SpawnCategory.MONSTER, 20);
        UUID u = UUID.randomUUID();
        sp.setPlayerCap(u, SpawnCategory.MONSTER, 5);
        assertEquals(30, sp.categoryCap(SpawnCategory.MONSTER));
        assertEquals(20, sp.playerCap(SpawnCategory.MONSTER));
        assertEquals(5, sp.playerCap(u, SpawnCategory.MONSTER));
        assertEquals(20, sp.playerCap(UUID.randomUUID(), SpawnCategory.MONSTER));
        assertEquals(30, core.world().spawnPolicy().state().cap(org.domi.furnace.server.entity.spawn.MobCategory.MONSTER), "서버 스폰 루프가 읽는 같은 값");
        assertEquals(70, sp.vanillaCap(SpawnCategory.MONSTER), "바닐라 상수는 그대로");
        sp.resetCategoryCap(SpawnCategory.MONSTER);
        sp.resetPlayerCap(SpawnCategory.MONSTER);
        sp.resetPlayerCap(u, SpawnCategory.MONSTER);
        assertEquals(70, sp.categoryCap(SpawnCategory.MONSTER));
        assertSame(org.domi.furnace.server.entity.spawn.SpawnPolicyState.VANILLA, core.world().spawnPolicy().state());
        assertThrows(IllegalArgumentException.class, () -> sp.setCategoryCap(SpawnCategory.MONSTER, -1));
        assertThrows(IllegalArgumentException.class, () -> sp.setCategoryCap(SpawnCategory.MISC, 1));
        assertThrows(IllegalArgumentException.class, () -> sp.vanillaCap(SpawnCategory.MISC));
        assertThrows(NullPointerException.class, () -> sp.addFilter(null, c -> true));
    }

    @Test
    void filterHandleUnsubscribes() throws Exception {
        SpawnPolicy sp = world.spawnPolicy();
        var sub = sp.addFilter(plugin, c -> true);
        var sub2 = sp.addFilter(plugin, c -> false);
        assertEquals(2, sp.filterCount());
        assertEquals(2, core.world().spawnPolicy().state().filters().size(), "서버 스폰 루프가 읽는 같은 목록");
        assertTrue(core.world().spawnPolicy().state().filters().get(0).ownerEnabled().getAsBoolean(), "주인 플러그인은 켜져 있다");
        sub.unsubscribe();
        assertEquals(1, sp.filterCount());
        sub2.unsubscribe();
        assertEquals(0, sp.filterCount());
        assertSame(org.domi.furnace.server.entity.spawn.SpawnPolicyState.VANILLA, core.world().spawnPolicy().state());
        assertThrows(NullPointerException.class, () -> sp.addFilter(plugin, null));
    }

    // ------------------------------------------------------------------ 엔티티 수

    @Test
    void chunkAndWorldEntityCounts() throws Exception {
        spawnCow(8, 8);
        spawnCow(9, 9);
        onRegion(r -> {
            var level = r.level();
            Zombie z = new Zombie(level, level.nextEntityId());
            z.moveTo(4, -60, 4, 0, 0);
            level.addFreshEntity(z);
            return null;
        });
        EntityCounts c = world.chunkAt(0, 0).entityCounts().get(10, TimeUnit.SECONDS);
        assertEquals(3, c.total());
        assertEquals(2, c.count("minecraft:cow"));
        assertEquals(1, c.count("minecraft:zombie"));
        assertEquals(2, c.count(SpawnCategory.CREATURE));
        assertEquals(1, c.count(SpawnCategory.MONSTER));
        assertEquals(0, c.count("minecraft:pig"));
        assertThrows(UnsupportedOperationException.class, () -> c.byType().put("x", 1), "불변");
        // 다른 청크의 엔티티는 세지 않는다
        spawnCowAtChunk(1, 0);
        assertEquals(3, world.chunkAt(0, 0).entityCounts().get(10, TimeUnit.SECONDS).total());
        assertEquals(1, world.chunkAt(1, 0).entityCounts().get(10, TimeUnit.SECONDS).count("minecraft:cow"));
        // 로드되지 않은 청크는 빈 값
        assertEquals(EntityCounts.EMPTY, world.chunkAt(40, 40).entityCounts().get(10, TimeUnit.SECONDS));
        // 월드 전체
        EntityCounts all = world.entityCounts().get(10, TimeUnit.SECONDS);
        assertEquals(3, all.count("minecraft:cow"));
        assertEquals(1, all.count("minecraft:zombie"));
        assertEquals(4, all.total());
        // 지워진 엔티티는 세지 않는다
        var cows = world.chunkAt(0, 0).entities().get(10, TimeUnit.SECONDS).stream().filter(e -> e.type().equals("minecraft:cow")).toList();
        assertTrue(cows.get(0).remove().get(10, TimeUnit.SECONDS));
        assertEquals(1, world.chunkAt(0, 0).entityCounts().get(10, TimeUnit.SECONDS).count("minecraft:cow"));
        assertEquals(2, world.entityCounts().get(10, TimeUnit.SECONDS).count("minecraft:cow"));
    }

    private void spawnCowAtChunk(int cx, int cz) throws Exception {
        ChunkPos cp = new ChunkPos(cx, cz);
        core.world().submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(30, TimeUnit.SECONDS);
        core.world().submitAtChunk(cp, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(cx * 16 + 8, -60, cz * 16 + 8, 0, 0);
            level.addFreshEntity(cow);
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ 자세·물리

    @Test
    void poseAndNoPhysicsOnMobs() throws Exception {
        var cow = spawnCow(8, 8);
        assertEquals(Pose.STANDING, cow.pose());
        assertFalse(cow.noPhysics());
        assertTrue(run(cow.setPose(Pose.SITTING)));
        assertTrue(run(cow.setNoPhysics(true)));
        var fresh = run(cow.refresh());
        assertEquals(Pose.SITTING, fresh.pose());
        assertTrue(fresh.noPhysics());
        // 서버 엔티티에 실제로 반영됐다
        var server = onRegion(r -> r.level().entities().get(cow.uniqueId()));
        assertEquals(Pose.SITTING.id(), server.getPose());
        assertTrue(server.noPhysics);
        // null 이면 서 있는 자세로
        assertTrue(run(fresh.setPose(null)));
        assertEquals(Pose.STANDING, run(fresh.refresh()).pose());
        assertTrue(run(fresh.setNoPhysics(false)));
        assertFalse(run(fresh.refresh()).noPhysics());
        // 사라진 엔티티는 false
        assertTrue(cow.remove().get(10, TimeUnit.SECONDS));
        assertFalse(run(cow.setPose(Pose.SITTING)));
        assertFalse(run(cow.setNoPhysics(true)));
    }

    @Test
    void playerPoseIsPinnedForOthers() throws Exception {
        var p = new org.domi.furnace.server.entity.Player(5, UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        var h = new PlayerHandle(p, host);
        assertEquals(Pose.STANDING, h.pose());
        var f = h.setPose(Pose.SLEEPING);
        p.drainOwnerTasks();
        assertTrue(f.get(5, TimeUnit.SECONDS));
        assertEquals(Pose.SLEEPING, h.pose());
        assertEquals(Pose.SLEEPING.id(), p.pose());
        assertEquals(0, p.computedPose(), "서버 계산값은 그대로");
        var g = h.setPose(null);
        p.drainOwnerTasks();
        assertTrue(g.get(5, TimeUnit.SECONDS));
        assertEquals(Pose.STANDING, h.pose());
        var ex = assertThrows(java.util.concurrent.ExecutionException.class, () -> h.setNoPhysics(true).get(5, TimeUnit.SECONDS));
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause(), "플레이어는 noPhysics 를 못 바꾼다");
    }

    // ------------------------------------------------------------------ 주민

    @Test
    void villagerReputationAndGossip() throws Exception {
        var v = onRegion(r -> {
            var level = r.level();
            var villager = new org.domi.furnace.server.entity.Villager(level, level.nextEntityId());
            villager.moveTo(8, -60, 8, 0, 0);
            level.addFreshEntity(villager);
            return EntityHandle.of(host, villager);
        });
        var vh = assertInstanceOf(org.domi.furnace.api.entity.Villager.class, v);
        assertEquals("minecraft:none", vh.profession());
        assertEquals(1, vh.professionLevel());
        UUID player = UUID.randomUUID();
        assertEquals(0, run(vh.reputation(player)));
        assertTrue(run(vh.addGossip(player, GossipKind.MAJOR_POSITIVE, 20)));
        assertEquals(100, run(vh.reputation(player)), "20 × 가중치 5");
        assertTrue(run(vh.addGossip(player, GossipKind.MINOR_NEGATIVE, 30)));
        assertEquals(70, run(vh.reputation(player)), "100 - 30");
        assertEquals(Map.of(GossipKind.MAJOR_POSITIVE, 20, GossipKind.MINOR_NEGATIVE, 30), run(vh.gossip(player)));
        // 최댓값에서 잘린다(MAJOR_POSITIVE max 20)
        run(vh.addGossip(player, GossipKind.MAJOR_POSITIVE, 50));
        assertEquals(20, run(vh.gossip(player)).get(GossipKind.MAJOR_POSITIVE));
        // 사건: 거래 +2
        assertTrue(run(vh.applyReputationEvent(ReputationEvent.TRADE, player)));
        assertEquals(2, run(vh.gossip(player)).get(GossipKind.TRADING));
        assertTrue(run(vh.applyReputationEvent(ReputationEvent.VILLAGER_KILLED, player)));
        assertEquals(25, run(vh.gossip(player)).get(GossipKind.MAJOR_NEGATIVE));
        // 다른 대상은 영향이 없다
        assertEquals(0, run(vh.reputation(UUID.randomUUID())));
        assertTrue(run(vh.clearGossip(player)));
        assertEquals(0, run(vh.reputation(player)));
        assertTrue(run(vh.gossip(player)).isEmpty());
        // 바닐라 getPlayerReputation 과 같은 값(서버 쪽에서 직접 읽는다)
        run(vh.addGossip(player, GossipKind.MINOR_POSITIVE, 10));
        int server = onRegion(r -> ((org.domi.furnace.server.entity.Villager) r.level().entities().get(vh.uniqueId())).getGossips().getReputation(player, t -> true));
        assertEquals(10, server);
        // 사라진 주민
        assertTrue(vh.remove().get(10, TimeUnit.SECONDS));
        assertFalse(run(vh.addGossip(player, GossipKind.TRADING, 2)));
        assertNull(run(vh.reputation(player)));
    }

    // ------------------------------------------------------------------ 마을·POI·습격

    @Test
    void pointsOfInterestAndVillage() throws Exception {
        // 종: 마을 POI(meeting) — 티켓을 잡혀야 마을 중심이 된다
        onRegion(r -> {
            r.level().setBlock(new Pos(8, -60, 8), B.defaultState(B.id("minecraft:bell")), 3);
            return null;
        });
        BlockPos bell = new BlockPos(8, -60, 8);
        var all = world.pointsOfInterest(bell, 16).get(10, TimeUnit.SECONDS);
        assertEquals(1, all.size(), all.toString());
        var poi = all.get(0);
        assertEquals("minecraft:meeting", poi.type());
        assertEquals(bell, poi.pos());
        assertEquals(poi.maxTickets(), poi.freeTickets());
        assertFalse(poi.occupied());
        assertTrue(poi.hasSpace());
        assertEquals(1, world.pointsOfInterest(bell, 16, Set.of("meeting"), PoiOccupancy.HAS_SPACE).get(10, TimeUnit.SECONDS).size(), "네임스페이스 없이도 찾는다");
        assertTrue(world.pointsOfInterest(bell, 16, Set.of("minecraft:home"), PoiOccupancy.ANY).get(10, TimeUnit.SECONDS).isEmpty());
        assertTrue(world.pointsOfInterest(bell, 16, Set.of(), PoiOccupancy.IS_OCCUPIED).get(10, TimeUnit.SECONDS).isEmpty());
        assertTrue(world.pointsOfInterest(new BlockPos(2, -60, 2), 3).get(10, TimeUnit.SECONDS).isEmpty(), "반경 밖(8,8 은 2,2 에서 가로 6)");
        assertFalse(world.isVillage(bell).get(10, TimeUnit.SECONDS), "점유된 마을 POI 가 없다");
        assertEquals(PoiManager.MAX_VILLAGE_DISTANCE + 1, world.sectionsToVillage(bell).get(10, TimeUnit.SECONDS));
        // 티켓을 잡으면 마을 중심
        onRegion(r -> r.level().poiManager().take(t -> true, (t, p) -> true, new Pos(8, -60, 8), 4));
        var occupied = world.pointsOfInterest(bell, 16, Set.of(), PoiOccupancy.IS_OCCUPIED).get(10, TimeUnit.SECONDS);
        assertEquals(1, occupied.size());
        assertTrue(occupied.get(0).occupied());
        assertEquals(occupied.get(0).maxTickets() - 1, occupied.get(0).freeTickets());
        assertTrue(world.isVillage(bell).get(10, TimeUnit.SECONDS));
        assertEquals(0, world.sectionsToVillage(bell).get(10, TimeUnit.SECONDS));
        // 로드되지 않은 청크
        assertFalse(world.isVillage(new BlockPos(640, 0, 640)).get(10, TimeUnit.SECONDS));
        assertEquals(PoiManager.MAX_VILLAGE_DISTANCE + 1, world.sectionsToVillage(new BlockPos(640, 0, 640)).get(10, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> world.pointsOfInterest(bell, 129).get(10, TimeUnit.SECONDS));
    }

    @Test
    void raidsAreReadableAndStoppable() throws Exception {
        assertTrue(world.raids().get(10, TimeUnit.SECONDS).isEmpty());
        assertTrue(world.raidAt(new BlockPos(8, -60, 8)).get(10, TimeUnit.SECONDS).isEmpty());
        onRegion(r -> {
            var level = r.level();
            Raid raid = new Raid(77, level, new Pos(8, -60, 8));
            raid.setRaidOmenLevel(2);
            level.raids().all().put(77, raid);
            return null;
        });
        var list = world.raids().get(10, TimeUnit.SECONDS);
        assertEquals(1, list.size());
        var info = list.get(0);
        assertEquals(77, info.id());
        assertEquals("minecraft:overworld", info.dimension());
        assertEquals(new BlockPos(8, -60, 8), info.center());
        assertEquals(RaidStatus.ONGOING, info.status());
        assertFalse(info.started());
        assertTrue(info.active());
        assertEquals(2, info.omenLevel());
        assertEquals(0, info.groupsSpawned());
        assertTrue(info.totalWaves() >= 1, "보너스 파도 포함 전체 파도 수");
        assertEquals(onRegion(r -> r.level().raids().get(77).numGroups()) + 1, info.totalWaves(), "징조 2 단계는 보너스 파도 하나");
        assertEquals(0, info.raidersAlive());
        assertEquals(Raid.PRE_RAID_TICKS, info.preRaidTicks());
        assertFalse(info.betweenWaves());
        assertTrue(info.heroes().isEmpty());
        Optional<org.domi.furnace.api.world.RaidInfo> at = world.raidAt(new BlockPos(12, -60, 12)).get(10, TimeUnit.SECONDS);
        assertEquals(77, at.orElseThrow().id());
        assertTrue(world.raidAt(new BlockPos(200, -60, 200)).get(10, TimeUnit.SECONDS).isEmpty(), "로드되지 않은 청크");
        // 멈추기
        assertFalse(world.stopRaid(999).get(10, TimeUnit.SECONDS), "모르는 번호");
        assertTrue(world.stopRaid(77).get(10, TimeUnit.SECONDS));
        assertTrue(world.raids().get(10, TimeUnit.SECONDS).isEmpty(), "멈춘 습격은 목록에서 빠진다");
        assertFalse(world.stopRaid(77).get(10, TimeUnit.SECONDS), "이미 멈췄다");
    }
}
