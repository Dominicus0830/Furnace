// T-224: BlockDestroyEvent 가 서버 Level#destroyBlock(선인장·엔티티·명령·플러그인 등 모든 길) 자리에서 플러그인 버스로 가고,
// 취소·효과 블록·떨굼·경험치 바꾸기가 서버에 돌아오는지(Paper 1.21.1 Level#destroyBlock 과 같게). 순서·난수는 DestroyBlockEventOrderTest.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.block.BlockDestroyEvent;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ExperienceOrb;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginApiBlockDestroyTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final Blocks BL = Blocks.get();
    private static final int STONE = B.defaultState("minecraft:stone"), SPAWNER = B.defaultState("minecraft:spawner");

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt"), org.domi.furnace.server.world.ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"));
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

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private void on(java.util.function.Consumer<BlockDestroyEvent> h) { host.eventsImpl().subscribe(plugin, BlockDestroyEvent.class, h); }

    @Test
    void cancelKeepsBlockAndDropsNothing() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(e -> {
            log.add(e.oldData().type() + "->" + e.newData().type() + "@" + e.pos() + " drop=" + e.willDrop() + " xp=" + e.expToDrop());
            e.setCancelled(true);
        });
        Pos p = new Pos(4, -45, 4);
        Object[] r = at(rg -> {
            var l = rg.level();
            l.setBlock(p, STONE, 3);
            boolean ok = l.destroyBlock(p, true);
            return new Object[] {ok, BL.describe(l.getBlockState(p)), l.entities().tickSnapshot().stream().filter(x -> x instanceof ExperienceOrb).count()};
        });
        assertEquals(false, r[0], "취소하면 destroyBlock 은 거짓(Paper)");
        assertEquals("minecraft:stone", r[1], "블록은 그대로");
        assertEquals(0L, r[2]);
        assertEquals(List.of("minecraft:stone->minecraft:air@" + new BlockPos(4, -45, 4) + " drop=true xp=0"), log);
    }

    @Test
    void handlerEditsEffectDropsAndExperience() throws Exception {
        List<String> saw = new CopyOnWriteArrayList<>();
        on(e -> {
            saw.add(e.effectData().type() + " " + e.playEffect() + " " + e.willDrop() + " " + (e.expToDrop() >= 15 && e.expToDrop() <= 43));
            e.setEffectData(BlockData.of("minecraft:dirt"));
            e.setWillDrop(true);
            e.setExpToDrop(5);
        });
        Pos p = new Pos(6, -45, 6);
        Object[] r = at(rg -> {
            var l = rg.level();
            l.setBlock(p, SPAWNER, 3);
            boolean ok = l.destroyBlock(p, false);
            int xp = l.entities().tickSnapshot().stream().filter(x -> x instanceof ExperienceOrb).mapToInt(x -> ((ExperienceOrb) x).value()).sum();
            return new Object[] {ok, BL.describe(l.getBlockState(p)), xp};
        });
        assertEquals(List.of("minecraft:spawner true false true"), saw, "효과 블록은 부서지는 블록, 떨굼은 부른 쪽 값, 경험치는 스포너 15..43");
        assertEquals(true, r[0]);
        assertEquals("minecraft:air", r[1]);
        assertEquals(5, r[2], "떨굼을 켜고 바꾼 경험치만큼 구슬");
    }

    @Test
    void unknownEffectBlockKeepsTheOriginalAndStillDestroys() throws Exception {
        on(e -> e.setEffectData(BlockData.of("minecraft:no_such_block")));
        Pos p = new Pos(7, -45, 7);
        String r = at(rg -> {
            var l = rg.level();
            l.setBlock(p, STONE, 3);
            l.destroyBlock(p, false);
            return BL.describe(l.getBlockState(p));
        });
        assertEquals("minecraft:air", r);
    }

    @Test
    void withoutSubscriberNoFlag() throws Exception {
        boolean before = at(rg -> rg.level().gameEvents().wantsBlockDestroy());
        assertFalse(before);
        on(e -> {});
        boolean after = at(rg -> rg.level().gameEvents().wantsBlockDestroy());
        assertTrue(after);
    }
}
