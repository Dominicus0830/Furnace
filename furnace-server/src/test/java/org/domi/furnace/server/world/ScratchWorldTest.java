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
