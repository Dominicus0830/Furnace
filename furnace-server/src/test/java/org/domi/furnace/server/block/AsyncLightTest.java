// T-064 빛 작업 모드(light.async): 빛을 빛 스레드에서 계산해도, 밀린 빛을 끝까지 기다린 뒤의 빛 값(층·원천 높이)이 그 자리 계산(끔)과 같아야 한다.
// 빛 작업이 도는 동안 region 이 블록을 바꿔도(얼린 구역 → 복사해서 씀) 그 묶음은 넘길 때의 블록만 본다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncLightTest {
    private static final int R = 3;   // 청크 -3..3
    private final SuperflatWorldSource gen = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);
    private ExecutorService pool;

    /** 넣은 작업을 시험이 부를 때 그 자리에서 돌린다(빛 작업이 "도는 중"인 상태를 마음대로 만든다). */
    private static final class Manual implements Executor {
        final ArrayDeque<Runnable> q = new ArrayDeque<>();

        @Override public void execute(Runnable r) { q.add(r); }

        void runAll() {
            Runnable r;
            while ((r = q.poll()) != null) r.run();
        }
    }

    private final class Lvl extends ChunkLevel {
        final Map<Long, ChunkColumn> cols;
        final Executor exec;
        final List<Packet> light = new ArrayList<>();
        BiPredicate<Integer, Integer> owns = (x, z) -> true;
        int failNext;   // >0 이면 그만큼 빛 묶음 계산이 실패한다(R4)

        Lvl(Map<Long, ChunkColumn> cols, Executor exec) {
            super(1000000);
            this.cols = cols;
            this.exec = exec;
        }

        @Override protected ChunkColumn chunk(int x, int z) {
            if (Math.abs(x) > R || Math.abs(z) > R || !owns.test(x, z)) return null;
            return cols.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> gen.generate(x, z));
        }

        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) {}
        @Override protected void broadcastLightToChunk(int x, int z, Packet p) { light.add(p); }

        @Override protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public Executor lightExecutor() { return exec; }
            };
        }

        @Override void runLightBatch(org.domi.furnace.server.light.LightBatch batch) {
            if (failNext > 0) {
                failNext--;
                throw new IllegalStateException("시험: 빛 작업 실패");
            }
            super.runLightBatch(batch);
        }

        /** Region.finishLoad 와 같은 청크 설치 경로. */
        void load(int cx, int cz) {
            lightNewChunk(cx, cz);
            runLightUpdates();
        }

        void lightAll() {
            for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) lightNewChunk(x, z);
            runLightUpdates();
        }
    }

    private int glow, stone, glass, air, lantern;

    @BeforeEach
    void setUp() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        glow = B.defaultState("glowstone");
        stone = B.defaultState("stone");
        glass = B.defaultState("glass");
        air = B.defaultState("air");
        lantern = B.defaultState("sea_lantern");
        pool = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() { pool.shutdownNow(); }

    private static void assertSameLight(Lvl want, Lvl got, String where) {
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                ChunkLight a = want.chunk(x, z).light(), b = got.chunk(x, z).light();
                for (int i = 0; i < ChunkLight.SECTIONS; i++) {
                    assertArrayEquals(a.sky[i], b.sky[i], where + " 하늘빛 청크 " + x + "," + z + " 층 " + i);
                    assertArrayEquals(a.block[i], b.block[i], where + " 블록빛 청크 " + x + "," + z + " 층 " + i);
                }
                assertArrayEquals(a.lowestSource, b.lowestSource, where + " 원천 높이 청크 " + x + "," + z);
                assertEquals(a.lightEnabled, b.lightEnabled);
            }
        }
    }

    private int randomState(Random r) {
        return switch (r.nextInt(6)) {
            case 0 -> glow;
            case 1, 2 -> stone;
            case 3 -> glass;
            case 4 -> lantern;
            default -> air;
        };
    }

    /** 무작위 편집을 여러 틱에 걸쳐: 끔(틱 끝 그 자리 계산) vs 켬(묶음을 넘기고, 도는 동안에도 편집). 다 기다린 뒤 빛이 같아야 한다. */
    @Test
    void randomEditsMatchInPlaceLightAfterDrain() {
        for (long seed = 1; seed <= 4; seed++) {
            Manual manual = new Manual();
            Lvl sync = new Lvl(new HashMap<>(), null), async = new Lvl(new HashMap<>(), manual);
            sync.lightAll();
            async.lightAll();
            assertTrue(async.asyncLight());
            assertFalse(sync.asyncLight());
            assertSameLight(sync, async, "처음");
            Random r = new Random(seed);
            for (int tick = 0; tick < 40; tick++) {
                int edits = 1 + r.nextInt(30);
                for (int i = 0; i < edits; i++) {
                    Pos p = new Pos(r.nextInt(40) - 20, -63 + r.nextInt(40), r.nextInt(40) - 20);
                    int st = randomState(r);
                    sync.setBlock(p, st, 2);
                    async.setBlock(p, st, 2);
                }
                sync.lightTickEnd();
                async.lightTickEnd();   // 묶음을 넘긴다(아직 안 돈다)
                if (r.nextInt(3) == 0) manual.runAll();   // 가끔만 돌려 밀린 상태에서도 편집이 들어가게
                async.pollLight();
                if (tick % 10 == 9) {
                    manual.runAll();
                    async.drainLight();
                    assertSameLight(sync, async, "시드 " + seed + " 틱 " + tick);
                }
            }
        }
    }

    /** 실제 스레드에서 돌려도 같다(도는 동안 region 이 블록을 바꾼다). */
    @Test
    void realThreadMatchesInPlaceLight() {
        Lvl sync = new Lvl(new HashMap<>(), null), async = new Lvl(new HashMap<>(), pool);
        sync.lightAll();
        async.lightAll();
        Random r = new Random(99);
        for (int tick = 0; tick < 60; tick++) {
            for (int i = 0; i < 20; i++) {
                Pos p = new Pos(r.nextInt(40) - 20, -63 + r.nextInt(30), r.nextInt(40) - 20);
                int st = randomState(r);
                sync.setBlock(p, st, 2);
                async.setBlock(p, st, 2);
            }
            sync.lightTickEnd();
            async.pollLight();
            async.lightTickEnd();
        }
        async.drainLight();
        assertSameLight(sync, async, "끝");
    }

    /** 묶음을 넘긴 뒤의 블록 쓰기는 얼린 구역을 복사해서 한다 — 묶음이 보는 객체는 그대로. */
    @Test
    void writesDuringBatchCopyFrozenSection() {
        Manual manual = new Manual();
        Lvl l = new Lvl(new HashMap<>(), manual);
        l.lightAll();
        Pos p = new Pos(3, -55, 3);
        l.setBlock(p, glow, 2);
        l.lightTickEnd();
        ChunkColumn col = l.chunk(0, 0);
        var before = col.sections()[(p.y() - col.minY()) >> 4];
        l.setBlock(p, stone, 2);   // 묶음이 도는 동안(아직 안 돌았다) 바꾼다
        var after = col.sections()[(p.y() - col.minY()) >> 4];
        assertNotSame(before, after, "얼린 구역은 복사해서 쓴다");
        assertEquals(glow, before.getBlock(p.x() & 15, (p.y() - col.minY()) & 15, p.z() & 15), "묶음이 보는 구역은 그대로");
        assertEquals(0, l.getBlockLight(p), "게시 전에는 옛 빛이 보인다");
        manual.runAll();
        l.pollLight();
        assertEquals(15, l.getBlockLight(p), "넘길 때의 블록(발광석)으로 계산한 빛이 게시된다");
        l.lightTickEnd();   // 돌의 검사를 다음 묶음에
        manual.runAll();
        l.pollLight();
        assertEquals(0, l.getBlockLight(p));
        // 게시 뒤에는 녹아 그 자리에서 쓴다
        var now = col.sections()[(p.y() - col.minY()) >> 4];
        l.setBlock(p, air, 2);
        assertSame(now, col.sections()[(p.y() - col.minY()) >> 4]);
    }

    private static long key(int x, int z) { return (long) x << 32 | (z & 0xFFFFFFFFL); }

    /**
     * 검토 R1·R2: 새 청크 빛은 묶음이 도는 중이어도 올라오는 자리에서 끝난다 — 틱·스폰·밝기 조회·전송이 첫 빛 전의 청크를 보지 않는다
     * (바닐라: 빛이 끝나야 FULL). 이웃 경계 발광석의 빛이 새 청크(미리 계산한 생성 경로) 가장자리에 곧바로 보인다.
     */
    @Test
    void loadedChunkIsLitImmediately() {
        Manual manual = new Manual();
        Lvl l = new Lvl(new HashMap<>(), manual);
        l.owns = (x, z) -> x != 1;
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (x != 1) l.lightNewChunk(x, z);
        l.runLightUpdates();
        Pos g = new Pos(15, -60, 8);
        l.setBlock(g, glow, 2);
        l.lightTickEnd();   // 발광석 빛이 묶음에(아직 안 돌았다)
        l.setBlock(new Pos(-20, -55, -20), lantern, 2);   // 넘긴 뒤 밀린 검사
        l.owns = (x, z) -> true;
        for (int z = -R; z <= R; z++) {
            ChunkColumn col = l.chunk(1, z);
            ChunkLevel.precomputeLight(col, 1, z, l.lightBedrockState(), true);   // 생성 풀에서 미리 계산
            l.load(1, z);
        }
        assertEquals(14, l.getBlockLight(new Pos(16, -60, 8)), "새 청크 가장자리에 이웃 빛이 곧바로");
        assertTrue(manual.q.size() == 1, "넘긴 작업은 큐에 남았지만 region 이 빼앗아 이미 돌렸다");
        manual.runAll();   // 빛 스레드가 늦게 집어도 아무것도 하지 않는다
        l.pollLight();
        Lvl ref = new Lvl(new HashMap<>(), null);
        ref.lightAll();
        ref.setBlock(g, glow, 2);
        ref.setBlock(new Pos(-20, -55, -20), lantern, 2);
        ref.runLightUpdates();
        assertSameLight(ref, l, "새 청크");
    }

    /** 검토 R3: 묶음이 도는 동안 청크가 내려갔다 다시 올라와도 새 컬럼의 빛은 올라오는 자리에서 맞다(좌표로 옛 일을 잘못 풀지 않는다). */
    @Test
    void reloadedColumnDuringBatchIsLit() {
        Manual manual = new Manual();
        Map<Long, ChunkColumn> cols = new HashMap<>();
        Lvl l = new Lvl(cols, manual);
        l.lightAll();
        Pos g = new Pos(29, -58, 29);   // 청크 (1,1) — 빛이 (2,2) 로 번진다
        l.setBlock(g, glow, 2);
        l.lightTickEnd();   // 묶음에 (2,2) 옛 컬럼이 얼려 들어갔다
        ChunkColumn old = cols.remove(key(2, 2));   // 내려갔다가
        ChunkColumn again = l.chunk(2, 2);   // 새 컬럼으로 다시
        assertNotSame(old, again);
        l.load(2, 2);
        assertEquals(15, l.getSkyLight(new Pos(40, -50, 40)), "다시 올라온 컬럼 지표 하늘빛");
        manual.runAll();
        l.pollLight();
        assertEquals(9, l.getBlockLight(new Pos(32, -58, 32)), "이웃 발광석 빛이 새 컬럼에 곧바로");
        Lvl ref = new Lvl(new HashMap<>(), null);
        ref.lightAll();
        ref.setBlock(g, glow, 2);
        ref.runLightUpdates();
        assertSameLight(ref, l, "다시 올라온 컬럼");
    }

    /** 검토 R5: 공유 풀에서 아직 시작하지 않은 묶음을 기다릴 때는 빼앗아 이 스레드에서 돌린다(남의 묶음 뒤에 줄 서지 않는다). */
    @Test
    void awaitStealsUnstartedBatch() {
        Manual never = new Manual();   // 빛 스레드가 영영 집지 않는다
        Lvl l = new Lvl(new HashMap<>(), never);
        l.lightAll();
        Pos p = new Pos(3, -55, 3);
        l.setBlock(p, glow, 2);
        l.lightTickEnd();
        l.awaitLightIdle();
        assertEquals(15, l.getBlockLight(p));
        never.runAll();   // 늦게 집으면 아무것도 안 한다
        l.lightTickEnd();
        assertEquals(15, l.getBlockLight(p));

        Lvl rejected = new Lvl(new HashMap<>(), r -> { throw new java.util.concurrent.RejectedExecutionException("닫힘"); });
        rejected.lightAll();
        rejected.setBlock(p, glow, 2);
        rejected.lightTickEnd();   // 넘기지 못해도 묶음은 남고
        rejected.runLightUpdates();   // 기다리는 쪽이 돌린다
        assertEquals(15, rejected.getBlockLight(p));
    }

    /** 검토 R4: 빛 작업이 실패하면(빛 스레드든 빼앗은 쪽이든) 넘긴 칸·미룬 원천을 그 자리에서 다시 계산해 끈 것과 같은 빛이 된다. */
    @Test
    void failedBatchIsRecomputedInPlace() {
        for (boolean steal : new boolean[] {false, true}) {
            Manual manual = new Manual();
            Lvl l = new Lvl(new HashMap<>(), manual);
            l.lightAll();
            Random rnd = new Random(7);
            Lvl ref = new Lvl(new HashMap<>(), null);
            ref.lightAll();
            for (int i = 0; i < 60; i++) {
                Pos p = new Pos(rnd.nextInt(80) - 40, -62 + rnd.nextInt(12), rnd.nextInt(80) - 40);
                int st = randomState(rnd);
                l.setBlock(p, st, 2);
                ref.setBlock(p, st, 2);
            }
            ref.runLightUpdates();
            l.failNext = 1;
            l.lightTickEnd();
            if (!steal) manual.runAll();   // 빛 스레드에서 실패
            l.awaitLightIdle();             // steal 이면 여기서 빼앗아 돌리다 실패
            l.runLightUpdates();
            assertSameLight(ref, l, "실패 뒤 다시 계산(steal=" + steal + ")");
        }
    }

    /** 저장 전: 그 청크 근처에 밀린 빛이 있으면 끝까지 계산한다(실제 스레드 기다림 포함). 먼 청크는 기다리지 않는다. */
    @Test
    void saveSettlesNearbyLight() {
        Manual manual = new Manual();
        Lvl l = new Lvl(new HashMap<>(), manual);
        l.lightAll();
        Pos p = new Pos(-40, -55, -40);   // 청크 (-3,-3)
        l.setBlock(p, glow, 2);
        l.settleLightForSave(3, 3);   // 멀다 — 그대로
        assertEquals(0, l.getBlockLight(p));
        l.settleLightForSave(-3, -2);
        assertEquals(15, l.getBlockLight(p), "밀린 검사를 끝까지 계산");

        Lvl t = new Lvl(new HashMap<>(), pool);
        t.lightAll();
        t.setBlock(p, glow, 2);
        t.lightTickEnd();   // 실제 스레드에서 돈다
        t.settleLightForSave(-3, -3);
        assertEquals(15, t.getBlockLight(p));
    }

    /** 합치기: 도는 묶음을 먼저 끝내고 합친다 — 한 레벨에서 같은 일을 한 것과 같다. */
    @Test
    void absorbDrainsBothLevels() {
        Manual ma = new Manual(), mb = new Manual();
        Map<Long, ChunkColumn> cols = new HashMap<>();
        Lvl a = new Lvl(cols, ma), b = new Lvl(cols, mb);
        a.lightAll();
        a.owns = (x, z) -> x <= 0;
        b.owns = (x, z) -> x > 0;
        Pos pa = new Pos(-40, -58, 0), pb = new Pos(40, -58, 0);
        a.setBlock(pa, glow, 2);
        b.setBlock(pb, glow, 2);
        a.lightTickEnd();
        b.lightTickEnd();   // 둘 다 도는 중(안 돌았다)
        b.setBlock(pb.above(), lantern, 2);   // 밀린 검사
        ma.runAll();
        mb.runAll();
        a.absorb(b);
        a.owns = (x, z) -> true;
        a.runLightUpdates();
        Lvl ref = new Lvl(new HashMap<>(), null);
        ref.lightAll();
        ref.setBlock(pa, glow, 2);
        ref.setBlock(pb, glow, 2);
        ref.setBlock(pb.above(), lantern, 2);
        ref.runLightUpdates();
        assertSameLight(ref, a, "합친 뒤");
    }

    /** 게시된 빛 변경은 다음 방송에 실린다(블록빛이 바뀐 청크마다 빛 갱신 패킷). */
    @Test
    void publishedChangesAreBroadcast() {
        Manual manual = new Manual();
        Lvl l = new Lvl(new HashMap<>(), manual);
        l.lightAll();
        l.broadcastChanges();
        l.light.clear();
        l.setBlock(new Pos(0, -55, 0), glow, 2);
        l.lightTickEnd();
        l.broadcastChanges();
        assertEquals(0, l.light.size(), "게시 전에는 보낼 빛 변경이 없다");
        manual.runAll();
        l.pollLight();
        l.broadcastChanges();
        assertTrue(l.light.size() >= 4, "발광석 둘레 청크들의 빛 갱신: " + l.light.size());

        Lvl ref = new Lvl(new HashMap<>(), null);
        ref.lightAll();
        ref.broadcastChanges();
        ref.light.clear();
        ref.setBlock(new Pos(0, -55, 0), glow, 2);
        ref.lightTickEnd();
        ref.broadcastChanges();
        assertEquals(ref.light.size(), l.light.size(), "그 자리 계산과 같은 청크들");
    }
}
