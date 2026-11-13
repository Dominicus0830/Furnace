// T-055b: 바닐라 1.21.1 예약 틱의 세부 — 불러온 예약의 순번(LevelChunkTicks.unpack: -n..-1, 레벨 카운터 안 씀), 불러올 때 거르기
// (SavedTick.loadTickList: 그 청크 밖 칸 버림, TickPriority.byValue: -3..3 으로 가둠), /clone 복사(LevelTicks.copyAreaFrom: 이번 틱에
// 이미 돈 것·남은 것도 모음), 저장 예약이 같은 (칸, 종류) 새 예약보다 먼저 자리를 잡음.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScheduledTickVanillaDetailsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final int STONE = B.id("minecraft:stone");

    static NbtCompound tick(String id, int x, int y, int z, int t, int p) {
        NbtCompound e = new NbtCompound();
        e.putString("i", id);
        e.putInt("x", x);
        e.putInt("y", y);
        e.putInt("z", z);
        e.putInt("t", t);
        e.putInt("p", p);
        return e;
    }

    static NbtCompound root(NbtCompound... ticks) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (NbtCompound t : ticks) list.add(t);
        NbtCompound root = new NbtCompound();
        root.putList("block_ticks", list);
        return root;
    }

    @Test
    void loadedTicksGetNegativeOrderAndDoNotUseTheCounter() {
        var level = new BlockPlacementTest.TestLevel();
        Pos older = new Pos(3, -60, 3);   // 청크 (0,0): 먼저 걸린 예약
        level.scheduleTick(older, STONE, 4);
        long before = level.blockTicks().chunkTicks(0, 0).get(0).subTickOrder();
        level.loadChunkTicks(root(tick("minecraft:stone", 20, -60, 1, 4, 0), tick("minecraft:stone", 21, -60, 1, 4, 0),
                tick("minecraft:dirt", 22, -60, 1, 4, 0)), 1, 0);
        List<Long> orders = new ArrayList<>();
        for (var s : level.blockTicks().chunkTicks(1, 0)) orders.add(s.subTickOrder());
        orders.sort(null);
        assertEquals(List.of(-3L, -2L, -1L), orders, "목록 순서대로 -n..-1");
        Pos after = new Pos(5, -60, 3);
        level.scheduleTick(after, STONE, 4);
        long next = 0;
        for (var s : level.blockTicks().chunkTicks(0, 0)) if (s.pos() == after.asLong()) next = s.subTickOrder();
        assertEquals(before + 1, next, "불러오기는 레벨 순번 카운터를 쓰지 않는다");
        // 같은 (시각, 우선순위): 불러온 예약(음수 순번)이 다른 청크의 먼저 걸린 예약보다 먼저 돈다
        List<Pos> ran = new ArrayList<>();
        long now = level.levelTime();
        for (int t = 1; t <= 4; t++) level.blockTicks().tick(now + t, 65536, (p, ty) -> { if (ty == STONE) ran.add(p); });
        assertEquals(List.of(new Pos(20, -60, 1), new Pos(21, -60, 1), older, after), ran);
    }

    @Test
    void loadFiltersOtherChunksAndClampsPriority() {
        var level = new BlockPlacementTest.TestLevel();
        level.loadChunkTicks(root(tick("minecraft:stone", 40, -60, 1, 4, 0), tick("minecraft:stone", 17, -60, 1, 4, 9),
                tick("minecraft:stone", 18, -60, 1, 4, -7)), 1, 0);
        assertTrue(level.blockTicks().chunkTicks(2, 0).isEmpty(), "청크 (1,0) 목록의 (2,0) 칸 예약은 버린다");
        List<Integer> prios = new ArrayList<>();
        List<Long> orders = new ArrayList<>();
        for (var s : level.blockTicks().chunkTicks(1, 0)) {
            prios.add(s.priority());
            orders.add(s.subTickOrder());
        }
        prios.sort(null);
        orders.sort(null);
        assertEquals(List.of(-3, 3), prios);
        assertEquals(List.of(-2L, -1L), orders, "n 은 걸러진 뒤의 수");
    }

    @Test
    void savedTickWinsOverALaterScheduleOfTheSamePosAndType() {
        // 바닐라: 저장 예약(pendingTicks)은 컨테이너가 생길 때부터 ticksPerPosition 에 있어, 그 뒤 같은 (칸, 종류) 예약은 버려진다
        var level = new BlockPlacementTest.TestLevel();
        level.loadChunkTicks(root(tick("minecraft:stone", 20, -60, 1, 7, 2)), 1, 0);
        level.scheduleTick(new Pos(20, -60, 1), STONE, 1);
        var list = level.blockTicks().chunkTicks(1, 0);
        assertEquals(1, list.size());
        assertEquals(level.levelTime() + 7, list.get(0).triggerTick(), "저장 쪽 시각이 남는다");
        assertEquals(2, list.get(0).priority());
    }

    @Test
    void cloneDuringATickCopiesAlreadyRunAndPendingTicks() {
        BlockTicks ticks = new BlockTicks();
        Pos a = new Pos(1, 0, 1), b = new Pos(2, 0, 1), later = new Pos(3, 0, 1);
        ticks.schedule(a, 7, 5, 0, 0);
        ticks.schedule(b, 7, 5, 0, 1);
        ticks.schedule(later, 7, 9, 0, 2);
        List<String> copied = new ArrayList<>();
        ticks.tick(5, 65536, (p, t) -> {
            if (p.equals(a)) ticks.copyAreaFrom(ticks, 0, 0, 0, 5, 0, 5, 0, 0, 10);   // a 의 틱 안에서 /clone: a 는 이미 돎, b 는 남음
        });
        for (var s : ticks.chunkTicks(0, 0)) {
            Pos p = Pos.of(s.pos());
            if (p.z() >= 10) copied.add(p.x() + "@" + s.triggerTick() + "/" + s.subTickOrder());
        }
        copied.sort(null);
        // 모은 순번 0..2 → 0 - 0 + 2 + 1 = 3 부터
        assertEquals(List.of("1@5/3", "2@5/4", "3@9/5"), copied);
    }
}
