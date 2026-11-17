// T-203 임시 월드: 같은 시드의 처음 상태 생성기로 청크를 만들고(원래 월드에서 바꾼 것·이미 만든 청크와 섞이지 않는다), 틱·저장·목록 노출이 없고, 닫으면 작업을 받지 않는다.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScratchWorldTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Dimensions dims;
    WorldManager manager;
    BlockRegistry reg;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp.resolve("world"), sched);
        dims = new Dimensions();
        dims.register(core.world());
        core.dimensions(dims);
        reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        RegistrySet registries = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = TestServers.ioPool();
        manager = new WorldManager(new WorldManager.Parts(tmp, reg, ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"), registries,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, null,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        core.worldManager(manager);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllForShutdown();
        sched.close();
    }

    private World create(WorldCreator c) throws Exception { return manager.create(c).get(120, TimeUnit.SECONDS); }

    /** 청크 전체 블록(아래부터)과 블록 엔티티 자리의 해시 — 주인 region 스레드에서 읽는다. */
    private static long chunkHash(World w, int cx, int cz) {
        ChunkPos cp = new ChunkPos(cx, cz);
        w.awaitChunk(cp);
        return w.submitAtChunk(cp, r -> {
            ChunkColumn col = r.ownedChunk(cx, cz);
            long h = 1469598103934665603L;
            for (int y = col.minY(); y <= col.maxY(); y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) h = (h ^ col.getBlock(x, y, z)) * 1099511628211L;
            for (long k : new java.util.TreeSet<>(col.blockEntities().keySet())) h = (h ^ k) * 1099511628211L;
            return h;
        }).join();
    }

    private int stone() { return reg.defaultStateId("minecraft:stone"); }

    @Test
    void sameSeedScratchMatchesAFreshWorldNotTheEditedOriginal() throws Exception {
        long seed = 20261117L;
        World a = create(WorldCreator.named("orig").seed(seed).build());
        // 원래 월드: 청크를 먼저 만들고(공유 장식 저장소가 찬다) 블록 하나를 바꾼다
        long before = chunkHash(a, 0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        assertTrue(a.submitAtChunk(cp, r -> r.level().setBlock(new Pos(3, 200, 3), stone(), 3)).join());
        assertNotEquals(before, chunkHash(a, 0, 0));

        World s = manager.createScratch(a, seed, "orig");
        try {
            assertTrue(s.isScratch());
            assertEquals("orig", s.levelName());
            assertEquals(seed, s.seed());
            assertTrue(s.dimension().startsWith("furnace:scratch/"));
            assertEquals(a.dimensionType().typeKey(), s.dimensionType().typeKey());
            assertNotSame(a.worldSource(), s.worldSource(), "처음 상태의 새 생성기");
            assertEquals(before, chunkHash(s, 0, 0), "임시 월드 = 같은 시드로 처음 만든 청크(원래 월드의 편집이 없다)");

            // 같은 시드의 새 월드에서 같은 순서로 처음 만든 청크와 같다(이웃 장식 포함 — 겹치는 장식은 바닐라처럼 생성 순서에 따르므로 순서를 맞춘다)
            World b = create(WorldCreator.named("fresh").seed(seed).build());
            assertEquals(before, chunkHash(b, 0, 0));
            for (int[] c : new int[][] {{1, 0}, {0, 1}, {-1, -1}}) {
                assertEquals(chunkHash(b, c[0], c[1]), chunkHash(s, c[0], c[1]), "청크 " + c[0] + "," + c[1]);
            }
        } finally {
            manager.closeScratch(s);
        }
    }

    @Test
    void otherSeedGivesOtherTerrain() throws Exception {
        World a = create(WorldCreator.named("orig2").seed(5L).build());
        long orig = chunkHash(a, 0, 0);
        World s = manager.createScratch(a, 6L, "orig2");
        try {
            assertEquals(6L, s.seed());
            assertNotEquals(orig, chunkHash(s, 0, 0));
            World b = create(WorldCreator.named("fresh2").seed(6L).build());
            assertEquals(chunkHash(b, 0, 0), chunkHash(s, 0, 0), "시드 6 의 새 월드와 같다");
        } finally {
            manager.closeScratch(s);
        }
    }

    @Test
    void scratchIsInvisibleDoesNotTickOrSaveAndRejectsWorkAfterClose() throws Exception {
        World a = create(WorldCreator.named("flat1").generator(WorldGenerator.FLAT).generateStructures(false).build());
        var keys = dims.levelKeys();
        World s = manager.createScratch(a, a.seed(), "flat1");
        assertInstanceOf(SuperflatWorldSource.class, ((org.domi.furnace.server.world.gen.ScratchWorldSource) s.worldSource()).base(), "디스크 원천 없이 생성기로");
        assertTrue(s.isFlatLevel());
        assertFalse(s.generatesStructures());
        assertEquals(keys, dims.levelKeys(), "차원 목록에 없다");
        assertNull(core.world(s.dimension()));
        assertFalse(manager.extraWorlds().contains(s));
        assertFalse(s.regionizer().gameTicks(), "게임 틱이 돌지 않는다(Paper 임시 레벨과 같다)");

        assertEquals(chunkHash(a, 2, 2), chunkHash(s, 2, 2));
        long t0 = s.gameTime();
        Thread.sleep(300);
        assertEquals(t0, s.gameTime(), "틱이 없어 시간이 가지 않는다");

        manager.closeScratch(s);
        manager.closeScratch(s);   // 두 번 불러도 된다
        assertTrue(s.isUnloaded());
        assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> s.runAtChunk(new ChunkPos(0, 0), r -> { }));
        try (var files = Files.list(tmp)) {
            assertEquals(List.of(), files.filter(p -> p.getFileName().toString().startsWith("furnace")).toList(), "디스크에 쓰지 않는다");
        }
    }

    /**
     * 여러 스레드가 임시 월드를 동시에 만들고 청크를 불러 닫는다 — 운영 기본인 공유 region 풀 경로(등록·해제가 겹친다).
     * 등록 표와 선택기 배열이 어긋나면 어떤 임시 월드의 작업을 아무 워커도 고르지 않아 청크 불러오기가 멈춘다.
     */
    @Test
    void concurrentCreateAndCloseOnTheSharedRegionPool() throws Exception {
        SharedScheduler pool = SharedScheduler.startShared(4, "furnace-region-scratchtest-");
        RegistrySet registries = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = TestServers.ioPool();
        WorldManager shared = new WorldManager(new WorldManager.Parts(tmp.resolve("shared"), reg, ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"),
                registries, registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, pool,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        var exec = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            World a = shared.create(WorldCreator.named("flatshared").generator(WorldGenerator.FLAT).build()).get(120, TimeUnit.SECONDS);
            int base = pool.dimensionCount();
            assertEquals(base, pool.selectableCount());
            for (int round = 0; round < 4; round++) {
                var start = new java.util.concurrent.CountDownLatch(1);
                List<java.util.concurrent.Future<World>> made = new java.util.ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    long seed = round * 100L + i;
                    made.add(exec.submit(() -> {
                        start.await();
                        World s = shared.createScratch(a, seed, "flatshared");
                        s.submitAtChunk(new ChunkPos(0, 0), r -> r.chunkAtBlocking(new ChunkPos(0, 0))).get(30, TimeUnit.SECONDS);
                        return s;
                    }));
                }
                start.countDown();
                List<World> scratch = new java.util.ArrayList<>();
                for (var f : made) scratch.add(f.get(60, TimeUnit.SECONDS));
                assertEquals(base + 12, pool.dimensionCount());
                assertEquals(base + 12, pool.selectableCount(), "선택기가 모든 임시 월드를 본다");
                if (round == 0) {
                    // 같은 순간에 여러 임시 월드가 등록된다(동시에 만들 때의 틈을 좁혀 여러 번 본다) — 매번 모두 선택기에 보여야 한다
                    int lost = 0;
                    for (int k = 0; k < 500 && lost == 0; k++) {
                        for (World s : scratch) pool.unregister(s.regionizer());
                        var barrier = new java.util.concurrent.CyclicBarrier(scratch.size());
                        List<java.util.concurrent.Future<?>> regs = new java.util.ArrayList<>();
                        for (World s : scratch) regs.add(exec.submit(() -> { barrier.await(); pool.register(s.regionizer()); return null; }));
                        for (var f : regs) f.get(30, TimeUnit.SECONDS);
                        if (pool.selectableCount() != pool.dimensionCount()) lost = k + 1;
                    }
                    assertEquals(0, lost, "등록 표와 선택기 배열이 어긋났다(회차)");
                }
                var close = new java.util.concurrent.CountDownLatch(1);
                List<java.util.concurrent.Future<?>> closed = new java.util.ArrayList<>();
                for (World s : scratch) closed.add(exec.submit(() -> { close.await(); shared.closeScratch(s); return null; }));
                close.countDown();
                for (var f : closed) f.get(60, TimeUnit.SECONDS);
                assertEquals(base, pool.dimensionCount());
                assertEquals(base, pool.selectableCount(), "닫은 임시 월드가 선택기에 남지 않는다");
            }
            // 공유 풀은 그대로 돈다
            assertEquals(chunkHash(a, 1, 1), chunkHash(a, 1, 1));
        } finally {
            exec.shutdownNow();
            shared.closeAllForShutdown();
            pool.close();
        }
    }

    /** 닫을 때 진행 중이던 청크 불러오기는 기다리는 쪽이 멈추지 않게 끝난다(#486 검토 3). */
    @Test
    void closingWithLoadsInFlightReleasesTheWaiters() throws Exception {
        World a = create(WorldCreator.named("inflight").seed(31L).build());
        World s = manager.createScratch(a, 31L, "inflight");
        List<java.util.concurrent.CompletableFuture<ChunkColumn>> loads = new java.util.ArrayList<>();
        for (int x = 0; x < 6; x++)
            for (int z = 0; z < 6; z++) {
                ChunkPos cp = new ChunkPos(40 + x, 40 + z);
                loads.add(s.submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
            }
        Thread.sleep(50);   // 몇 개는 생성 중
        manager.closeScratch(s);
        int done = 0, cancelled = 0;
        for (var f : loads) {
            try {
                f.get(20, TimeUnit.SECONDS);
                done++;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException e) {
                cancelled++;
            }
        }
        assertEquals(36, done + cancelled, "모두 끝난다(제한 시간 안)");
        assertEquals(0, s.pendingChunkLoads());
    }

    /** 바이옴 공급자가 늘 plains 면 같은 시드의 바닐라 단일 바이옴(plains) 월드와 같은 청크(지형지물·구조물 포함). */
    @Test
    void singleBiomeProviderMatchesTheVanillaSingleBiomeWorld() throws Exception {
        long seed = 4242L;
        World a = create(WorldCreator.named("pb").seed(seed).build());
        var plains = new org.domi.furnace.api.worldgen.BiomeProvider() {
            @Override public String biome(int x, int y, int z, org.domi.furnace.api.worldgen.Climate c) { return "minecraft:plains"; }
            @Override public List<String> biomes() { return List.of("minecraft:plains"); }
        };
        World s = manager.createScratch(a, seed, "pb", plains);
        try {
            World b = create(WorldCreator.named("sb").seed(seed).generator(WorldGenerator.SINGLE_BIOME_SURFACE).build());
            for (int[] c : new int[][] {{0, 0}, {1, 0}, {0, 1}, {-3, 2}}) {
                assertEquals(chunkHash(b, c[0], c[1]), chunkHash(s, c[0], c[1]), "청크 " + c[0] + "," + c[1]);
            }
            assertNotEquals(chunkHash(a, 0, 0), chunkHash(s, 0, 0), "원래(여러 바이옴) 월드와는 다르다");
            ChunkPos cp = new ChunkPos(0, 0);
            int plainsId = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt").indexOf("minecraft:worldgen/biome", "minecraft:plains");
            assertEquals(plainsId, (int) s.submitAtChunk(cp, r -> r.ownedChunk(0, 0).sectionForRead(5).getBiome(1, 2, 3)).join(), "저장된 바이옴");
        } finally {
            manager.closeScratch(s);
        }
    }

    @Test
    void freshCopyKeepsStructureSettingAndSharesTheGenerationPool() throws Exception {
        World a = create(WorldCreator.named("nostruct").seed(9L).generateStructures(false).build());
        NoiseWorldSource base = (NoiseWorldSource) ((org.domi.furnace.server.world.gen.AnvilWorldSource) a.worldSource()).base();
        NoiseWorldSource copy = base.freshCopy(10L);
        assertEquals(10L, copy.seed());
        assertFalse(copy.structureEngine().structuresEnabled(), "구조물 끔을 옮긴다");
        assertNotSame(base.pipeline(), copy.pipeline(), "장식 저장소는 새것");
        assertSame(base.pipeline().generationPool(), copy.pipeline().generationPool(), "생성 풀은 같이 쓴다(코어 몫이 늘지 않는다)");
    }
}
