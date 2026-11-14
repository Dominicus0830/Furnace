package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-141 E3: World#loadChunkAsync(Paper getChunkAtAsync) — 생성하지 않음은 디스크에 저장된 청크만 올리고(없으면 false·만들지 않음),
 * 결과는 그 청크의 region 스레드에서 완료된다. refreshChunk 는 올라와 있지 않으면 false. 디스크가 있는 추가 월드에서 잰다.
 */
class ChunkAsyncLoadApiTest {
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
    void generateFalseLoadsOnlySavedChunks() throws Exception {
        var host = core.plugins();
        var h = host.createWorld(WorldCreator.named("ca").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        World w = core.world("minecraft:ca");

        assertFalse(h.loadChunkAsync(40, 40, false).get(30, TimeUnit.SECONDS), "저장된 적 없는 청크는 생성하지 않음이면 false");
        assertFalse(h.isChunkLoaded(40, 40), "만들지 않는다");
        assertFalse(h.refreshChunk(40, 40), "올라와 있지 않으면 false");

        AtomicBoolean onOwner = new AtomicBoolean();
        assertTrue(h.loadChunkAsync(40, 40, true).thenApply(ok -> {
            onOwner.set(((ThreadOwner) w.chunkScheduler(40, 40)).isOwnerThread() && w.isChunkLoaded(40, 40));
            return ok;
        }).get(30, TimeUnit.SECONDS));
        assertTrue(onOwner.get(), "완료는 그 청크의 region 스레드에서, 그때 청크가 올라와 있다");
        assertTrue(h.refreshChunk(40, 40));
        assertTrue(h.loadChunkAsync(40, 40, false).get(30, TimeUnit.SECONDS), "올라와 있으면 생성하지 않음이어도 true");

        // 소유 스레드에서 올라와 있으면 곧바로 완료된 Future
        assertTrue(w.submitAtChunk(new ChunkPos(40, 40), r -> h.loadChunkAsync(40, 40, true).isDone()).get(30, TimeUnit.SECONDS));

        assertTrue(manager.unload(w, true).get(60, TimeUnit.SECONDS));
        manager.load("ca").get(60, TimeUnit.SECONDS);
        var again = host.world("minecraft:ca");
        assertFalse(again.isChunkLoaded(40, 40));
        assertTrue(again.loadChunkAsync(40, 40, false).get(30, TimeUnit.SECONDS), "디스크에 저장된 청크는 생성하지 않음이어도 올린다");
        assertTrue(again.isChunkLoaded(40, 40));
        assertFalse(again.loadChunkAsync(41, 40, false).get(30, TimeUnit.SECONDS), "옆 청크는 저장된 적 없다");
        assertFalse(again.isChunkLoaded(41, 40));
    }
}
