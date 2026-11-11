// A16-01 검토(PR #230): 스폰 이벤트 안에서 EntityState.remove() 로 지운 엔티티는 추적을 시작하지 않고 넣었다고 답하지 않는다(유령 엔티티 방지).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntitySpawnRemoveTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    @Test
    void removeInsideSpawnEventStillStartsTracking() throws Exception {
        host.eventsImpl().subscribe(plugin, EntitySpawnEvent.class, e -> e.entity().stateNow().ifPresent(s -> s.remove()));
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            boolean added = level.addFreshEntity(cow);
            return new Object[] {added, cow.isRemoved(), cow.tracker != null, level.entities().get(cow.uuid()) != null};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out[1], "remove() 로 지워졌다");
        assertEquals(false, out[3], "레벨 목록에는 없다");
        assertEquals(false, out[2], "지워진(목록에 없는) 엔티티에 추적기가 붙었다 — 보는 플레이어에게 스폰 패킷이 가고 지움 패킷은 영영 안 간다");
        assertEquals(false, out[0], "지워진 엔티티를 넣었다고 답했다(Paper: isRemoved 면 넣지 않음)");
    }
}
