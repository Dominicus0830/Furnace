package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.WorldManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// T-071: handleFor 빠른 길의 `cur.handle() == w` 검사가 실제로 필요한 순간 — 내린 뒤(worldDetached 뒤) 죽은 월드의 손잡이가 맵에 다시 들어간 상태에서 같은 키로 새 월드를 올리면 새 손잡이를 돌려줘야 한다.
// (독립 검토 R1: 이 검사를 지워도 MultiWorldTest 의 handleFor* 는 통과해서 따로 지킨다. 검사를 지운 돌연변이에서 이 시험이 실패한다.)
class HandleForStaleEntryTest {
    @TempDir Path tmp;
    FurnaceSchedulerImpl sched; ServerCore core; WorldManager manager;

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
    void tearDown() { manager.closeAllForShutdown(); sched.close(); }

    @Test
    void staleEntryReinsertedAfterDetachIsReplacedOnReload() throws Exception {
        PluginHost host = core.plugins();
        World w1 = manager.create(WorldCreator.named("r313").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        WorldHandle h1 = host.worldHandle("minecraft:r313");
        assertTrue(manager.unload(w1, false).get(60, TimeUnit.SECONDS));   // worldDetached 로 항목이 걷힌다
        // 다른 스레드가 unregister 전에 core.world(dim)=w1 을 읽고, detached 뒤에 handleFor(w1) 에 닿은 경우를 흉내 낸다(옛 코드도 같음)
        WorldHandle h1b = host.handleFor(w1);
        assertNotSame(h1, h1b, "떼기 뒤 조회는 새 (죽은) 손잡이를 만들어 맵에 다시 넣는다 — 기존 동작");
        World w2 = manager.create(WorldCreator.named("r313").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        WorldHandle h2 = host.worldHandle("minecraft:r313");
        assertSame(w2, h2.handle(), "빠른 길이 맵에 남은 죽은 w1 손잡이를 돌려주면 안 된다");
        assertTrue(h2.isLoaded());
    }
}
