// A07-01·04 회귀: 레드스톤 횃불 과열 기록(바닐라 RedstoneTorchBlock.RECENT_TOGGLES).
// 바닐라는 레벨(차원) 하나에 기록 하나다. Furnace 는 region 레벨마다 기록을 두고(region 스레드끼리 겹치지 않게) 합치기·나누기 때 칸을 따라 옮긴다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RedstoneTorchBurnoutTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks BL = Blocks.get();
    static final int TORCH = BL.block("minecraft:redstone_torch");
    static final int RB = BL.defaultState(BL.block("minecraft:redstone_block"));
    static final int LIT = B.prop("lit").with(BL.defaultState(TORCH), true);
    static final int STONE = BL.defaultState(BL.block("minecraft:stone"));

    /** 청크 (0,0) 하나를 바깥에서 받는 가벼운 레벨(region 이 생기고 합쳐지고 나뉠 때 새 RegionLevel 이 생기는 것과 같다). */
    static final class OneChunkLevel extends ChunkLevel {
        final ChunkColumn col;
        OneChunkLevel(ChunkColumn col) { super(1000000); this.col = col; }
        @Override protected ChunkColumn chunk(int x, int z) { return x == 0 && z == 0 ? col : null; }
        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) {}
        @Override protected LevelServices services() { return LevelServices.DEFAULT; }
    }

    /** 레드스톤 블록 위 켜진 횃불을 한 번 꺼지게(예약 틱 실행과 같은 길) 한다 — 과열 기록에 한 줄 더한다. */
    private static void toggleOff(ChunkLevel level, Pos torch) {
        level.setBlock(torch.below(), RB, 2);
        level.setBlock(torch, LIT, 2);
        BlockBehaviors.of(LIT).tick(LIT, level, torch, RandomSource.create(1L));
        assertEquals("minecraft:redstone_torch[lit=false]", BL.describe(level.getBlockState(torch)));
    }

    /** 받침 신호를 끊고 예약 틱을 몇 번 돌린 뒤 횃불 상태. 타 버렸으면 꺼진 채, 아니면 다시 켜진다. */
    private static String afterSignalRemoved(BlockPlacementTest.TestLevel level, Pos torch) {
        level.setBlock(torch.below(), STONE, 3);
        for (int t = 0; t < 4; t++) level.runScheduledTicks();
        return BL.describe(level.getBlockState(torch));
    }

    /** 대조: 레벨이 하나면 8번째 꺼짐에서 타 버려 신호가 끊겨도 꺼진 채로 있다. 7번이면 다시 켜진다. */
    @Test
    void burnoutOnEighthToggleInOneLevel() {
        var a = new BlockPlacementTest.TestLevel();
        Pos torch = new Pos(3, -60, 3);
        for (int i = 0; i < 8; i++) toggleOff(a, torch);
        assertEquals("minecraft:redstone_torch[lit=false]", afterSignalRemoved(a, torch));

        var b = new BlockPlacementTest.TestLevel();
        for (int i = 0; i < 7; i++) toggleOff(b, torch);
        assertEquals("minecraft:redstone_torch[lit=true]", afterSignalRemoved(b, torch));
    }

    /** A07-04: region 이 합쳐져도(흡수된 레벨은 버려진다) 그 횃불의 기록이 남아 8번째에서 타 버린다. */
    @Test
    void burnoutHistorySurvivesRegionMerge() {
        var a = new BlockPlacementTest.TestLevel();
        var b = new BlockPlacementTest.TestLevel();
        Pos torch = new Pos(3, -60, 3);
        for (int i = 0; i < 7; i++) toggleOff(b, torch);   // region B 에서 7번
        a.absorb(b);
        assertEquals(7, a.redstoneTorchToggles().count(torch));
        assertEquals(0, b.redstoneTorchToggles().size());
        toggleOff(a, torch);                                // 같은 칸, 8번째 → 타 버린다
        assertEquals("minecraft:redstone_torch[lit=false]", afterSignalRemoved(a, torch),
                "바닐라: 60틱 안에 8번 꺼져 타 버린 횃불은 신호가 끊겨도 다시 켜지지 않는다(같은 차원이면 region 이 합쳐져도)");
    }

    /** region 이 나뉘면 기록이 칸이 속한 쪽으로 간다 — 옮겨 간 쪽에서 8번째에 타 버리고, 남은 쪽 칸의 기록은 그대로다. */
    @Test
    void burnoutHistoryFollowsChunkOnSplit() {
        var a = new BlockPlacementTest.TestLevel();
        var b = new BlockPlacementTest.TestLevel();
        Pos moved = new Pos(3, -60, 3), stays = new Pos(-13, -60, 3);   // 청크 (0,0) 은 나가고 (-1,0) 은 남는다
        for (int i = 0; i < 7; i++) {
            toggleOff(a, moved);
            toggleOff(a, stays);
        }
        a.splitTo(b, (cx, cz) -> cx == 0 && cz == 0);
        assertEquals(7, b.redstoneTorchToggles().count(moved));
        assertEquals(0, b.redstoneTorchToggles().count(stays));
        assertEquals(0, a.redstoneTorchToggles().count(moved));
        assertEquals(7, a.redstoneTorchToggles().count(stays));
        toggleOff(b, moved);
        assertEquals("minecraft:redstone_torch[lit=false]", afterSignalRemoved(b, moved));
    }

    /** 합칠 때 두 기록을 시각 순으로 섞는다 — 앞에서부터 오래된 것을 지우는 바닐라 규칙이 합친 뒤에도 오래된 것을 모두 지운다. */
    @Test
    void mergedRecordStaysInTimeOrder() {
        var a = new RedstoneTorchToggles();
        var b = new RedstoneTorchToggles();
        Pos p = new Pos(1, 2, 3), q = new Pos(4, 5, 6);
        a.add(p, 10);
        a.add(p, 100);
        b.add(q, 5);
        b.add(q, 50);
        a.absorb(b);
        a.expire(115, 60);   // 115 − 50 > 60 → 5, 10, 50 이 빠지고 100 만 남아야 한다
        assertEquals(1, a.size());
        assertEquals(1, a.count(p));
        assertEquals(0, a.count(q));
    }

    /**
     * A07-01: 예전 정적 WeakHashMap 은 region 스레드 여럿이 잠금 없이 써서, 레벨을 많이 만들며 동시에 횃불을 끄면 WeakHashMap.get 이
     * 무한 반복에 빠졌다(8 스레드 모두 30초 안에 끝나지 않음). 이제 기록은 레벨마다라 스레드끼리 겹치지 않는다.
     */
    @Test
    void concurrentRegionsDoNotShareTheRecord() throws Exception {
        var gen = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);
        int threads = 8, perThread = 3000;
        List<List<ChunkLevel>> made = new ArrayList<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        Pos torch = new Pos(3, -60, 3);
        for (int t = 0; t < threads; t++) {
            List<ChunkLevel> mine = new ArrayList<>();
            made.add(mine);
            ChunkColumn col = gen.generate(0, 0);   // 스레드마다 자기 청크(청크 쓰기 경합은 빼고 기록만 겹치게)
            Thread th = new Thread(() -> {
                try {
                    go.await();
                    for (int i = 0; i < perThread; i++) {
                        var l = new OneChunkLevel(col);
                        mine.add(l);
                        toggleOff(l, torch);
                    }
                } catch (Throwable e) {
                    err.compareAndSet(null, e);
                }
            });
            th.setDaemon(true);   // 혹시 다시 무한 반복에 빠져도 시험 JVM 이 끝날 수 있게
            ts.add(th);
            th.start();
        }
        go.countDown();
        int stuck = 0;
        for (Thread th : ts) {
            th.join(60_000);
            if (th.isAlive()) stuck++;
        }
        assertEquals(0, stuck, "60초 안에 끝나지 않은 스레드 수");
        assertNull(err.get(), "동시 접근 중 예외: " + err.get());
        int wrong = 0;
        for (var list : made) for (ChunkLevel l : list) if (l.redstoneTorchToggles().count(torch) != 1) wrong++;
        assertEquals(0, wrong, "레벨 " + threads * perThread + "개 중 과열 기록이 1개가 아닌 레벨 수");
    }
}
