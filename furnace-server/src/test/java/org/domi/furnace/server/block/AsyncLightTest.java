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

    /** 새 청크: 빛 켜기가 묶음에 걸린 동안은 보내지 않고(lightPending), 게시되면 보낼 수 있다. 끈 동안은 곧바로. */
    @Test
    void loadedChunkWaitsForLightPublish() {
        Manual manual = new Manual();
        Map<Long, ChunkColumn> cols = new HashMap<>();
        Lvl l = new Lvl(cols, manual);
        l.owns = (x, z) -> !(x == 2 && z == 2);
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (!(x == 2 && z == 2)) l.lightNewChunk(x, z);
        l.runLightUpdates();
        l.owns = (x, z) -> true;   // (2,2) 이 올라왔다
        ChunkColumn col = l.chunk(2, 2);
        l.lightLoadedChunk(2, 2);
        assertTrue(col.lightPending());
        var settled = l.lightSettled();
        assertFalse(settled.isDone());
        l.lightTickEnd();
        assertTrue(col.lightPending(), "도는 중");
        manual.runAll();
        l.pollLight();
        assertFalse(col.lightPending());
        assertTrue(settled.isDone());
        assertTrue(l.lightSettled().isDone());

        // 끈 레벨과 빛이 같다
        Lvl ref = new Lvl(new HashMap<>(), null);
        ref.lightAll();
        assertSameLight(ref, l, "새 청크");

        Lvl off = new Lvl(new HashMap<>(), null);
        off.lightLoadedChunk(0, 0);
        assertFalse(off.chunk(0, 0).lightPending());
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

    /** 합치기: 도는 묶음·밀린 새 청크를 먼저 끝내고 합친다 — 한 레벨에서 같은 일을 한 것과 같다. */
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
