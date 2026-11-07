// 플러그인 구조물 찾기(W4/43번): /locate structure 와 같은 답, 태그·오류 처리, 구조물이 없는 월드.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.structure.StructureLocation;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructuresLocateApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private PluginHost host;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private NoiseWorldSource noise;

    private void boot(boolean noiseWorld) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = noiseWorld
                ? TestServers.core(tmp, sched, reg -> noise = NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/1.21.1.txt")))
                : TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    @Test
    void matchesLocateCommand() throws Exception {
        boot(true);
        var st = host.structures();
        assertTrue(st.structureIds().contains("minecraft:village_plains"));
        var expected = noise.structureLocator().findNearest(List.of("minecraft:buried_treasure"), 0, 64, 0, 100);
        assertNotNull(expected);
        StructureLocation got = st.locate(api(), "minecraft:buried_treasure", new BlockPos(0, 64, 0)).get(60, TimeUnit.SECONDS).orElseThrow();
        assertEquals(expected.structureId(), got.structureId());
        assertEquals(expected.x(), got.x());
        assertEquals(expected.z(), got.z());
        // 태그: 마을 여럿 중 가장 가까운 것, 찾은 실제 종류를 알려 준다
        var tagExpected = noise.structureLocator().findNearest(org.domi.furnace.server.world.gen.structure.StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100);
        StructureLocation tag = st.locate(api(), List.of("#minecraft:village"), new BlockPos(0, 70, 0), 100).get(60, TimeUnit.SECONDS).orElseThrow();
        assertEquals(tagExpected.structureId(), tag.structureId());
        assertTrue(tag.structureId().startsWith("minecraft:village_"));
        // 이 월드에서 못 만드는 것은 empty, 모르는 이름은 예외
        assertTrue(st.locate(api(), "minecraft:end_city", new BlockPos(0, 64, 0)).get(60, TimeUnit.SECONDS).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> st.locate(api(), "minecraft:nope", new BlockPos(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> st.locate(api(), List.of("#minecraft:nope"), new BlockPos(0, 0, 0), 10));
        assertThrows(IllegalArgumentException.class, () -> st.locate(api(), List.of(), new BlockPos(0, 0, 0), 10));
    }

    @Test
    void flatWorldHasNothing() throws Exception {
        boot(false);
        assertTrue(host.structures().locate(api(), "minecraft:village_plains", new BlockPos(0, 64, 0)).get(60, TimeUnit.SECONDS).isEmpty());
    }
}
