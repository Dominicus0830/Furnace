// A10 검토(PR #182) R1·R2·R3 회귀 — 독립 검토 재현 시험에서. 바닐라 1.21.1 바이트코드 근거:
// - aqu 의 엔티티 틱 람다 a(brc, bnf, bsr): bsr.dA()(checkDespawn) 를 먼저 부르고, 그다음에 aqk.c(J)(inEntityTickingRange, TickingTracker ≤ 31
//   = 체비셰프 ≤ sim)일 때만 tick. entityTickList 는 엔티티 섹션이 ENTITY_TICKING(시야 거리 안)인 엔티티 전부라서, 시뮬레이션 거리 밖·시야 안
//   몹도 사라짐 검사는 받는다.
// - TickingTracker: /forceload 티켓(레벨 31)이 이웃 청크에 32 로 퍼져 shouldTickBlocksAt(< 33) 이 참.
// PR #182 는 스폰 대상을 "시야 거리 안 ∩ 8 청크·128 블록"으로 넓혔지만(바닐라와 같음) Furnace 엔티티 틱(=사라짐 검사)은 여전히 체비셰프 ≤ sim − 1 이다.
// 시뮬레이션 거리 ≤ 8 이면 스폰된 몹이 사라짐 검사를 영영 못 받는 청크에 생긴다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityTickRangeTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().setRegistries(BlockRegistry.load("blocks/1.21.1.txt"), ItemRegistry.load("items/1.21.1.txt"));
        core.gameRules().set("doMobSpawning", 0);   // 시험 몹만 본다
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player player(Region r, double x, double z) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(x, -60, z, 0, 0, true);
        r.adopt(p);
        return p;
    }

    private <T> T onRegion(Function<Region, T> fn) throws Exception {
        return core.world().submitAtChunk(C00, fn::apply).get(120, TimeUnit.SECONDS);
    }

    private void load(Region r, Player p, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            ChunkPos cp = new ChunkPos(x, 0);
            r.chunkAtBlocking(cp);
            core.world().subscribeChunk(cp, p);   // 구독이 없으면 몇 틱 뒤 퇴출된다
        }
    }

    private Mob zombie(Region r, double x, double z) {
        Mob m = (Mob) Entities.create(r.level(), "minecraft:zombie");
        m.moveTo(x, -60, z, 0, 0);
        r.level().addFreshEntity(m);
        return m;
    }

    private static boolean spawnTarget(Region r, int cx, int cz) {
        try {
            var m = Region.class.getDeclaredMethod("collectTickingChunks");
            m.setAccessible(true);
            int n = (Integer) m.invoke(r);
            var f = Region.class.getDeclaredField("tickingChunks");
            f.setAccessible(true);
            long[] keys = (long[]) f.get(r);
            for (int i = 0; i < n; i++) if (keys[i] == Regionizer.chunkKey(cx, cz)) return true;
            return false;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * R1: sim 4, 시야 10. 청크 (6,0) 은 PR 이후 스폰 대상이다. 거기 선 좀비는 플레이어가 128 블록 밖으로 걸어가면 바닐라에서는
     * 다음 틱 checkDespawn 으로 사라진다(청크 (6,0) 은 플레이어 청크 (-2,0) 에서 체비셰프 8 ≤ 시야 10 이라 entityTickList 에 남는다).
     * Furnace 는 그 청크가 엔티티 틱(≤ sim − 1 = 3)이 아니라 checkDespawn 을 부르지 않아 좀비가 남고, 지역·전역 상한을 계속 차지한다.
     */
    @Test
    void mobSpawnedInFrozenRingDespawnsWhenPlayerWalksAway() throws Exception {
        assertWalkAwayDespawn(4);
    }

    /** 대조군: sim 10 이면 청크 (6,0) 이 (-2,0) 에서 체비셰프 8 ≤ sim − 1 이라 Furnace 도 사라지게 한다(PR #182 에서 통과). 시험 틀 자체는 맞다. */
    @Test
    void controlWithDefaultSimulationDistanceDespawns() throws Exception {
        assertWalkAwayDespawn(10);
    }

    private void assertWalkAwayDespawn(int sim) throws Exception {
        core.setSimulationDistance(sim);
        core.world().viewDistance(10);
        boolean[] out = onRegion(r -> {
            Player p = player(r, 8.5, 8.5);
            load(r, p, -3, 7);
            boolean target = spawnTarget(r, 6, 0);
            Mob z = zombie(r, 6 * 16 + 8.5, 8.5);   // 플레이어에서 96 블록: 즉시 사라짐 거리 128 안
            r.tick();
            boolean aliveNear = !z.isRemoved();
            p.applyMove(-2 * 16 + 1.5, -60, 8.5, 0, 0, true);   // 청크 (-2,0), 좀비와 135 블록
            for (int i = 0; i < 3; i++) r.tick();
            return new boolean[] {target, aliveNear, z.isRemoved(), r.chunkLoaded(new ChunkPos(6, 0))};
        });
        assertTrue(out[0], "전제: 청크 (6,0) 은 PR #182 기준 스폰 대상");
        assertTrue(out[1], "전제: 96 블록에서는 남는다");
        assertTrue(out[3], "전제: 청크는 아직 올라와 있다(퇴출로 지워진 것이 아님)");
        assertTrue(out[2], "바닐라: 128 블록 밖 + 시야 안 청크 → checkDespawn 으로 즉시 사라진다");
    }

    /**
     * R2: 바닐라 엔티티 틱 범위는 TickingTracker ≤ 31 = 체비셰프 ≤ sim(1차 점검 C1 확인). Furnace 는 ≤ sim − 1.
     * PR #183 은 이 판정(entityTicking)을 "몹 쪽 점검이 맞춘다"며 남겼고, PR #182 는 "A07 몫"이라며 남겼다 — 아무도 고치지 않는다.
     */
    @Test
    void entityAtSimulationDistanceTicks() throws Exception {
        core.setSimulationDistance(4);
        core.world().viewDistance(10);
        int ticks = onRegion(r -> {
            Player p = player(r, 8.5, 8.5);
            load(r, p, 0, 5);
            Mob z = zombie(r, 4 * 16 + 8.5, 8.5);   // 체비셰프 4 = sim
            int before = z.tickCount;
            for (int i = 0; i < 3; i++) r.tick();
            return z.tickCount - before;
        });
        assertEquals(3, ticks, "바닐라: 체비셰프 sim 청크의 엔티티는 틱한다");
    }

    /**
     * R3(낮음): /forceload 청크의 이웃은 TickingTracker 레벨 32 → shouldTickBlocksAt 참. sim 2 에서 청크 (5,0) 은 스폰 대상(시야 안, 88 블록)이고
     * 이웃 (6,0) 이 강제 로드면 바닐라는 무작위 틱을 준다. PR 의 randomTicking 은 강제 로드 청크 자신만 본다.
     */
    @Test
    void forcedChunkNeighbourGetsRandomTicks() throws Exception {
        core.setSimulationDistance(2);
        core.world().viewDistance(10);
        core.world().forcedChunks().set(6, 0, true);
        boolean[] out = onRegion(r -> {
            Player p = player(r, 8.5, 8.5);
            load(r, p, 0, 6);
            return new boolean[] {spawnTarget(r, 5, 0), r.randomTicking(Regionizer.chunkKey(5, 0))};
        });
        assertTrue(out[0], "전제: (5,0) 은 스폰 대상");
        assertTrue(out[1], "바닐라: 강제 로드 이웃은 블록 틱 범위(TickingTracker 32)라 무작위 틱");
    }
}
