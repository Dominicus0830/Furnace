// T-055: 바닐라 1.21.1 LevelAccessor.scheduleTick → createTick(level.nextSubTickCount()) → LevelTicks.schedule 는 칸의 청크 컨테이너
// (allContainers — FULL 로 로드된 청크)가 없으면 예약을 버린다(Util.pauseInIde 뒤 return). 순번은 버리기 전에 이미 하나 썼다.
// 고정하는 것: (a) 로드 안 된 청크에 건 예약은 나중에 그 청크가 올라와도 돌지 않는다 (b) 그 앞뒤 예약의 순번은 하나 건너뛴다
// (c) 로드된 이웃 청크는 지금처럼 받는다 (d) 적재 중인 청크의 저장된 예약(loadChunkTicks)은 버리지 않는다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScheduledTickUnloadedChunkTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 몇 청크를 "내려간" 것으로 볼 수 있는 시험 레벨. */
    static final class Level extends BlockPlacementTest.TestLevel {
        final Set<Long> unloaded = new HashSet<>();

        @Override
        protected ChunkColumn chunk(int x, int z) {
            return unloaded.contains(ChunkPredicateMemo.chunkKey(x, z)) ? null : super.chunk(x, z);
        }
    }

    static final int STONE = B.id("minecraft:stone");

    @Test
    void tickInUnloadedChunkIsDroppedButStillConsumesOrder() {
        Level level = new Level();
        level.unloaded.add(ChunkPredicateMemo.chunkKey(1, 0));
        Pos loaded = new Pos(3, -60, 3), missing = new Pos(20, -60, 3), neighbour = new Pos(-5, -60, 3);   // 청크 (0,0) / (1,0) / (-1,0)
        level.scheduleTick(loaded, STONE, 2);
        level.scheduleTick(missing, STONE, 2);
        level.scheduleTick(neighbour, STONE, 2);
        // (a) 버렸다
        assertFalse(level.hasScheduledTick(missing, STONE));
        assertEquals(2, level.blockTicks().size());
        // (b) 순번: loaded = n, (버린 것 = n+1), neighbour = n+2
        long a = level.blockTicks().chunkTicks(0, 0).get(0).subTickOrder();
        long c = level.blockTicks().chunkTicks(-1, 0).get(0).subTickOrder();
        assertEquals(a + 2, c, "버린 예약도 순번 하나를 쓴다(바닐라 createTick 이 schedule 보다 먼저)");
        // (c) 로드된 이웃은 받았다
        assertTrue(level.hasScheduledTick(neighbour, STONE));
        // 청크가 올라와도 버린 예약은 돌지 않는다
        level.unloaded.clear();
        assertTrue(level.blockTicks().chunkTicks(1, 0).isEmpty());
        List<Pos> ran = new ArrayList<>();
        for (int t = 0; t < 5; t++) level.blockTicks().tick(level.levelTime() + 1 + t, 65536, (p, ty) -> ran.add(p));
        assertEquals(List.of(loaded, neighbour), ran);
    }

    @Test
    void fluidTickInUnloadedChunkIsDroppedToo() {
        Level level = new Level();
        level.unloaded.add(ChunkPredicateMemo.chunkKey(0, 1));
        level.scheduleFluidTick(new Pos(2, -60, 20), Fluids.T_WATER, 5);
        NbtCompound root = new NbtCompound();
        level.unloaded.clear();
        level.saveChunkTicks(0, 1, root, false);
        assertTrue(((org.domi.furnace.server.net.io.nbt.NbtList) root.tags().get("fluid_ticks")).items().isEmpty());
        assertFalse(level.hasChunkTicks(0, 1));
    }

    @Test
    void savedTicksOfAChunkBeingLoadedAreKept() {
        Level level = new Level();
        Pos p = new Pos(20, -60, 3);
        level.scheduleTick(p, STONE, 7);
        NbtCompound root = new NbtCompound();
        level.saveChunkTicks(1, 0, root, true);   // 내릴 때 저장하며 꺼낸다
        assertFalse(level.hasScheduledTick(p, STONE));
        level.loadChunkTicks(root);   // 다시 올릴 때(Region 은 청크를 월드에 넣은 뒤 부른다)
        assertTrue(level.hasScheduledTick(p, STONE));
        assertTrue(level.hasChunkTicks(1, 0));
    }
}
