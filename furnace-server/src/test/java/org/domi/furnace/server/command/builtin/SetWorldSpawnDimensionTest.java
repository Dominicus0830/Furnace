// /setworldspawn: 바닐라는 오버월드에서만(그 밖은 commands.setworldspawn.failure.not_overworld), 네더·엔드는 공유 스폰이라 오버월드 것을 따른다.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetWorldSpawnDimensionTest {

    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    World nether;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = TestServers.ioPool();
        nether = new World(TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        nether.defaultSpawn(core.world().defaultSpawn());   // FurnaceMain 과 같이 공유 스폰으로 시작
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    @Test
    void overworldSetsSpawnAndNetherFollows() {
        var console = CommandSource.console(core);
        assertEquals(1, core.commands().performPrefixedCommandAsync(console, "setworldspawn 40 70 -30").join().result());
        var sp = core.world().defaultSpawn();
        assertEquals(40, sp.x());
        assertEquals(70, sp.y());
        assertEquals(-30, sp.z());
        assertEquals(sp, nether.defaultSpawn(), "네더는 공유 스폰(DerivedLevelData)이라 오버월드 것을 따른다");
    }

    @Test
    void otherDimensionRefusesAndChangesNothing() {
        var before = core.world().defaultSpawn();
        var console = CommandSource.console(core);
        assertEquals(0, core.commands().performPrefixedCommandAsync(console, "execute in minecraft:the_nether run setworldspawn 40 70 -30").join().result());
        assertEquals(before, core.world().defaultSpawn());
        assertEquals(before, nether.defaultSpawn());
    }
}
