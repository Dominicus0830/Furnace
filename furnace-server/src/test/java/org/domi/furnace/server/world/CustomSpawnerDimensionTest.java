// 특수 스포너(팬텀·순찰·고양이·포위·상인)는 바닐라 MinecraftServer.createLevels 처럼 오버월드에만 있다.
// 차원에 하나뿐인 스포너(DimensionSpawners)는 오버월드에만 있고, 네더에는 없다(= 레벨 난수도 쓰지 않는다).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomSpawnerDimensionTest {

    /** 시험용 네더: 슈퍼플랫 높이를 쓰되 레벨 키는 네더(DimensionChangeTest 와 같은 정의). */
    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;

    @AfterEach
    void close() {
        if (sched != null) sched.close();
    }

    /** 전역 틱 한 번(DimensionSpawners.tick) 뒤 [팬텀, 순찰, 고양이] 타이머. 플레이어가 없어도 셋 다 첫 호출에 타이머를 다시 건다(팬텀·순찰은 난수로). */
    private static int[] tickOnce(World world) {
        DimensionSpawners d = world.dimensionSpawners();
        if (d == null) return null;
        d.tick();
        return new int[] {d.phantom().nextTick(), d.patrol().nextTick(), d.cat().nextTick()};
    }

    @Test
    void customSpawnersRunOnlyInOverworld() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = org.domi.furnace.server.TestServers.ioPool();
        var pool = new ChunkWorkerPool(1);
        World over = new World(new SuperflatWorldSource(reg, 0), pool, sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        var src = new AnvilWorldSource(tmp, new RegionFileStore(TEST_NETHER.storageDir(tmp)),
                new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        World nether = new World(TEST_NETHER, src, pool, sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        assertEquals("minecraft:the_nether", nether.dimension());

        assertNull(tickOnce(nether), "네더에는 특수 스포너가 없다(바닐라 createLevels 는 빈 목록)");
        // region 틱 쪽은 포위 후보 알리기뿐이고 네더에서는 아무 일도 하지 않는다
        nether.submitAtChunk(new ChunkPos(0, 0), r -> {
            r.level().tickCustomSpawners(List.of());
            return null;
        }).get(10, TimeUnit.SECONDS);

        int[] o = tickOnce(over);
        assertTrue(o[0] >= 1199 && o[0] <= 2399, "오버월드 팬텀 타이머: 0 - 1 + (60 + r60) * 20 — " + o[0]);
        assertTrue(o[1] >= 11999 && o[1] <= 13198, "오버월드 순찰 타이머: 0 - 1 + 12000 + r1200 — " + o[1]);
        assertEquals(1200, o[2], "오버월드 고양이 타이머");
    }
}
