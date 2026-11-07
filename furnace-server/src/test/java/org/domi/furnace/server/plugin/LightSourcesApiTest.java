// 플러그인 빛 API(W6): 가짜 광원 두기·읽기·지우기, 주인별 몫, 로드 안 된 청크, 플러그인 정리, 저장 때 빛 빼기.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.light.LightLevels;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LightSourcesApiTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma a = new TestPlugins.Gamma();
    private final TestPlugins.Gamma b = new TestPlugins.Gamma();

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
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    /** 하늘이 막힌 깊은 칸(블록빛만 보이게) — 청크 (0,0) 안쪽 돌 속을 비운 방. */
    private BlockPos cave() throws Exception {
        var w = api();
        BlockPos p = new BlockPos(8, -20, 8);
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -2; dz <= 2; dz++) {
            w.setBlock(p.offset(dx, dy, dz), org.domi.furnace.api.world.BlockData.of("air")).get(10, TimeUnit.SECONDS);
        }
        return p;
    }

    @Test
    void setReadRemove() throws Exception {
        var lights = host.lightSources();
        var w = api();
        BlockPos p = cave();
        int before = lights.lightAt(w, p).get(10, TimeUnit.SECONDS).orElseThrow().block();
        assertTrue(lights.set(a, w, p, 13).get(10, TimeUnit.SECONDS));
        assertEquals(13, lights.level(w, p));
        assertEquals(13, lights.levelOf(a, w, p));
        assertEquals(0, lights.levelOf(b, w, p));
        LightLevels at = lights.lightAt(w, p).get(10, TimeUnit.SECONDS).orElseThrow();
        assertEquals(Math.max(13, before), at.block());
        assertEquals(Math.max(12, before), lights.lightAt(w, p.offset(1, 0, 0)).get(10, TimeUnit.SECONDS).orElseThrow().block());
        assertEquals(List.of(p), lights.sources(a, w));

        assertFalse(lights.set(b, w, p, 5).get(10, TimeUnit.SECONDS), "더 어두운 다른 주인은 실제 발광을 안 바꾼다");
        assertTrue(lights.remove(a, w, p).get(10, TimeUnit.SECONDS));
        assertEquals(5, lights.level(w, p));
        assertEquals(Math.max(5, before), lights.lightAt(w, p).get(10, TimeUnit.SECONDS).orElseThrow().block());
        assertTrue(lights.remove(b, w, p).get(10, TimeUnit.SECONDS));
        assertEquals(before, lights.lightAt(w, p).get(10, TimeUnit.SECONDS).orElseThrow().block());
    }

    @Test
    void invalidAndUnloaded() throws Exception {
        var lights = host.lightSources();
        var w = api();
        assertThrows(IllegalArgumentException.class, () -> lights.set(a, w, new BlockPos(0, 0, 0), 16));
        assertThrows(IllegalArgumentException.class, () -> lights.set(a, w, new BlockPos(0, 0, 0), -1));
        BlockPos far = new BlockPos(16 * 500, 10, 16 * 500);
        assertFalse(lights.set(a, w, far, 10).get(10, TimeUnit.SECONDS), "로드 안 된 청크는 거절");
        assertTrue(lights.lightAt(w, far).get(10, TimeUnit.SECONDS).isEmpty());
        assertFalse(lights.set(a, w, new BlockPos(1, 9999, 1), 10).get(10, TimeUnit.SECONDS), "높이 밖은 거절");
        assertTrue(lights.sources(a, w).isEmpty());
    }

    @Test
    void removeAllClearsOnlyThatOwner_andSaveDropsLight() throws Exception {
        var lights = host.lightSources();
        var w = api();
        BlockPos p = cave();
        lights.set(a, w, p, 15).get(10, TimeUnit.SECONDS);
        lights.set(b, w, p.offset(1, 0, 0), 4).get(10, TimeUnit.SECONDS);
        var vl = core.world().virtualLights();
        assertTrue(vl.affects(0, 0));
        assertTrue(vl.affects(1, 1), "맞닿은 청크도 영향");
        lights.removeAll(a).get(10, TimeUnit.SECONDS);
        assertTrue(lights.sources(a, w).isEmpty());
        assertEquals(List.of(p.offset(1, 0, 0)), lights.sources(b, w));
        assertEquals(4, lights.level(w, p.offset(1, 0, 0)));
        assertEquals(0, lights.level(w, p));
        lights.removeAll(b).get(10, TimeUnit.SECONDS);
        assertTrue(vl.isEmpty());
        assertFalse(vl.affects(0, 0));
    }
}
