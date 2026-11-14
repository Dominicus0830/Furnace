package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** T-141 B: 추가 월드의 World 상태 API — 폴더(네더 환경 = <이름>/DIM-1), 시계 주인 아님(쓰기 false), 스폰 저장 왕복(내리기·다시 불러오기, 자동 저장·종료). PR #369 검토 재현에서 옮김. */
class ExtraWorldStateApiTest {
    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    WorldManager manager;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp.resolve("world"), sched);
        var dims = new Dimensions();
        dims.register(core.world());
        core.dimensions(dims);
        var reg = BlockRegistry.load("blocks/1.21.1.txt");
        var registries = RegistrySet.load("registries/1.21.1.txt");
        var io = TestServers.ioPool();
        manager = new WorldManager(new WorldManager.Parts(tmp, reg, ItemRegistry.load("items/1.21.1.txt"), registries,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, null,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        core.worldManager(manager);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllForShutdown();
        sched.close();
    }

    @Test
    void netherExtraWorldFolderHoldsRegion() throws Exception {
        var host = core.plugins();
        var h = host.createWorld(WorldCreator.named("rn").environment(WorldEnvironment.NETHER).generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertEquals(tmp.resolve("rn/DIM-1"), h.folder(), "Paper getDimensionPath: NETHER 환경 추가 월드는 <이름>/DIM-1 (region 이 있는 곳)");
        var e = host.createWorld(WorldCreator.named("re").environment(WorldEnvironment.THE_END).generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertEquals(tmp.resolve("re/DIM1"), e.folder());
        var o = host.createWorld(WorldCreator.named("ro").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertEquals(tmp.resolve("ro"), o.folder());
        assertEquals(core.world().levelRoot(), core.plugins().world("minecraft:overworld").folder());

        // 하늘이 없는 차원은 하늘빛이 늘 0(바닐라 DUMMY 층, Paper getLightFromSky 와 같다) — 높이 위쪽 끝 너머도
        World nether = core.world("minecraft:rn");
        var cp = new ChunkPos(0, 0);
        assertEquals(0, (int) nether.submitAtChunk(cp, r -> r.level().getSkyLight(new org.domi.furnace.server.block.Pos(0, 100, 0))).join());
        assertEquals(0, (int) nether.submitAtChunk(cp, r -> r.level().getSkyLight(new org.domi.furnace.server.block.Pos(0, 400, 0))).join());
        World plain = core.world("minecraft:ro");
        assertEquals(15, (int) plain.submitAtChunk(cp, r -> r.level().getSkyLight(new org.domi.furnace.server.block.Pos(0, 400, 0))).join());
    }

    @Test
    void extraWorldSpawnSurvivesUnloadReload() throws Exception {
        var host = core.plugins();
        var h = host.createWorld(WorldCreator.named("rs").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertFalse(h.ownsClock());
        assertFalse(h.setDayTime(5));
        assertFalse(h.setWeather(true, null, 0, null, 0));
        var mainSpawn = core.world().defaultSpawn();
        assertTrue(h.setSpawnLocation(123, 77, -45, 30f));
        assertEquals(mainSpawn, core.world().defaultSpawn(), "추가 월드 스폰은 주 월드를 건드리지 않는다");
        World w = core.world("minecraft:rs");
        assertTrue(manager.unload(w, true).get(60, TimeUnit.SECONDS));
        World again = manager.load("rs").get(60, TimeUnit.SECONDS);
        assertEquals(new SpawnPoint(123, 77, -45, 30f), again.defaultSpawn());
    }

    @Test
    void extraWorldSpawnSurvivesAutosaveAndShutdown() throws Exception {
        var host = core.plugins();
        var h = host.createWorld(WorldCreator.named("ra").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertTrue(h.setSpawnLocation(9, 66, 9, 0f));
        manager.saveLevelData();
        manager.closeAllForShutdown();
        var ld = org.domi.furnace.server.world.storage.LevelStorage.open(tmp.resolve("ra"));
        try {
            var d = ld.loadLevelData();
            assertEquals(List.of(9, 66, 9), List.of(d.spawnX, d.spawnY, d.spawnZ));
        } finally {
            ld.close();
        }
    }
}
