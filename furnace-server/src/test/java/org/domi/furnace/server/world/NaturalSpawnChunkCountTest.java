// A10-01 회귀(재현 시험 docs/audit/findings/A10-repro/A10SpawnChunkCountTest 에서): 자연 스폰 전역 상한의 "스폰 가능 청크 수".
// 바닐라 1.21.1 ServerChunkCache.tickChunks(aqs) 는 createState 에 DistanceManager.getNaturalSpawnChunkCount()(aqk.b) 를 넘긴다.
// 그 값은 FixedPlayerDistanceChunkTracker(aqk$b, 범위 8) 의 칸 수 — 플레이어 청크에서 체비셰프 8 안의 17×17=289 칸이고,
// 청크가 올라와 있는지·시뮬레이션 거리·128 블록 원과 무관하다. 그래서 혼자 있는 플레이어의 몬스터 상한은 70×289/289 = 70.
// 예전 Furnace 는 "무작위 틱 받을 청크"(체비셰프 min(sim-1,8), 128 블록 원) 수를 써서 195 칸 → 몬스터 상한 47 이었다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
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

class NaturalSpawnChunkCountTest {

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
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player player(Region r, String name, double x, double z, GameMode mode) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        if (mode != GameMode.SURVIVAL) p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(x, -60, z, 0, 0, true);
        r.adopt(p);
        return p;
    }

    private <T> T onRegion(Function<Region, T> fn) throws Exception {
        return core.world().submitAtChunk(C00, fn::apply).get(120, TimeUnit.SECONDS);
    }

    @Test
    void naturalSpawnChunkCountForOnePlayerIs289() throws Exception {
        int count = onRegion(r -> {
            // 플레이어 청크(0,0) 둘레 체비셰프 8 을 모두 올린다 — 바닐라 기준 289 칸 전부가 로드된 가장 유리한 경우
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) r.chunkAtBlocking(new ChunkPos(x, z));
            player(r, "A", 8.5, 8.5, GameMode.SURVIVAL);
            return r.naturalSpawnChunkCount();
        });
        assertEquals(289, count, "바닐라 getNaturalSpawnChunkCount = 17×17 = 289 (몬스터 상한 70)");
    }

    /** 청크가 하나만 올라와 있고 시뮬레이션 거리가 짧아도 289 — 바닐라는 로드·시뮬레이션 거리와 무관하다. */
    @Test
    void countIgnoresLoadedChunksAndSimulationDistance() throws Exception {
        core.setSimulationDistance(5);
        int count = onRegion(r -> {
            r.chunkAtBlocking(C00);
            player(r, "A", 8.5, 8.5, GameMode.SURVIVAL);
            return r.naturalSpawnChunkCount();
        });
        assertEquals(289, count, "시뮬레이션 거리 5 에서도 바닐라 상한 청크 수는 289");
    }

    /** 두 플레이어 정사각형이 겹치면 합집합(바닐라 추적기는 칸마다 한 번 센다). 같은 청크 두 명은 한 정사각형. */
    @Test
    void overlappingPlayersCountTheUnion() throws Exception {
        int[] counts = onRegion(r -> {
            r.chunkAtBlocking(C00);
            player(r, "A", 8.5, 8.5, GameMode.SURVIVAL);
            Player b = player(r, "B", 9.5, 9.5, GameMode.SURVIVAL);
            int same = r.naturalSpawnChunkCount();
            b.applyMove(4 * 16 + 8.5, -60, 8.5, 0, 0, true);   // 청크 (4, 0)
            int apart = r.naturalSpawnChunkCount();
            return new int[] {same, apart};
        });
        assertEquals(289, counts[0], "같은 청크 두 명");
        assertEquals(21 * 17, counts[1], "x 로 4 청크 떨어진 두 명: 21×17");
    }

    /** 관전자는 spectatorsGenerateChunks 가 켜져 있으면 센다(기본), 꺼져 있으면 빼다(ChunkMap.skipPlayer). */
    @Test
    void spectatorsCountOnlyWhenTheyGenerateChunks() throws Exception {
        int on = onRegion(r -> {
            r.chunkAtBlocking(C00);
            player(r, "S", 8.5, 8.5, GameMode.SPECTATOR);
            return r.naturalSpawnChunkCount();
        });
        assertEquals(289, on, "spectatorsGenerateChunks 참(기본)");
        core.gameRules().set("spectatorsGenerateChunks", 0);
        int off = onRegion(Region::naturalSpawnChunkCount);
        assertEquals(0, off, "spectatorsGenerateChunks 거짓이면 관전자는 세지 않는다");
    }

    /**
     * 점검 추가 지적(A10-01 옆): 스폰 대상 청크는 엔티티 틱(서버 시야 거리 안) ∩ 8 청크·128 블록이고 시뮬레이션 거리와 무관하다.
     * 무작위 틱만 시뮬레이션 거리 + 1(shouldTickBlocksAt). 예전엔 둘 다 min(sim − 1, 8) 이었다.
     */
    @Test
    void spawnTargetsIgnoreSimulationDistanceButRandomTicksDoNot() throws Exception {
        core.setSimulationDistance(4);
        core.world().viewDistance(10);   // 시험 서버 기본 시야 거리는 2 — 엔티티 틱 범위가 그만큼 좁다
        Object[] out = onRegion(r -> {
            for (int x = 0; x <= 7; x++) r.chunkAtBlocking(new ChunkPos(x, 0));
            player(r, "A", 8.5, 8.5, GameMode.SURVIVAL);
            try {
                var m = Region.class.getDeclaredMethod("collectTickingChunks");
                m.setAccessible(true);
                int n = (Integer) m.invoke(r);
                var f = Region.class.getDeclaredField("tickingChunks");
                f.setAccessible(true);
                long[] keys = java.util.Arrays.copyOf((long[]) f.get(r), n);
                return new Object[] {keys, r.randomTicking(Regionizer.chunkKey(5, 0)), r.randomTicking(Regionizer.chunkKey(6, 0)),
                        r.naturalSpawningAllowed(7, 0)};
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        long[] keys = (long[]) out[0];
        java.util.Set<Long> set = new java.util.HashSet<>();
        for (long k : keys) set.add(k);
        assertTrue(set.contains(Regionizer.chunkKey(7, 0)), "체비셰프 7(가운데 112 블록) — 시뮬레이션 거리 4 여도 스폰 대상");
        assertEquals(8, set.size(), "올린 청크 (0..7, 0) 모두");
        assertEquals(true, out[1], "무작위 틱: 체비셰프 5 = sim + 1");
        assertEquals(false, out[2], "무작위 틱: 체비셰프 6 > sim + 1");
        assertEquals(true, out[3], "엔티티 틱(시야 거리 안)");
        core.world().viewDistance(6);
        boolean beyondView = onRegion(r -> r.naturalSpawningAllowed(7, 0));
        assertFalse(beyondView, "시야 거리 6 밖(체비셰프 7)은 엔티티 틱이 아니라 스폰 대상이 아니다");
    }

    @Test
    void unionOfSquaresMatchesBruteForce() {
        java.util.Random rnd = new java.util.Random(20261108L);
        for (int round = 0; round < 200; round++) {
            int n = 1 + rnd.nextInt(8);
            int[] xs = new int[n], zs = new int[n];
            java.util.Set<Long> cells = new java.util.HashSet<>();
            for (int i = 0; i < n; i++) {
                xs[i] = rnd.nextInt(60) - 30;
                zs[i] = rnd.nextInt(60) - 30;
                for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) cells.add(Regionizer.chunkKey(xs[i] + dx, zs[i] + dz));
            }
            assertEquals(cells.size(), Region.unionOfSquares(xs, zs, n, 8), "round " + round);
        }
    }
}
