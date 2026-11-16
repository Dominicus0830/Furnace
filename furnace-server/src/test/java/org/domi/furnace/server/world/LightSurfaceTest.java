// T-205 빛 표면 묶음 B 의 서버 몫: 공개 스냅샷의 빛(다른 스레드 읽기), 빛 검사 걸기(Paper LevelLightEngine#checkBlock).
// 설계 docs/feature-parity/compat-nms-t205-light-surface-2026-11-16.md.
// - 스냅샷은 낸 순간의 빛을 든다(뒤의 빛 변경이 새지 않는다), 빛이 바뀌면 다음 공개가 새 판을 낸다.
// - 원시 쓰기(빛 없음 — Paper LevelChunkSection#setBlockState 와 같다) 뒤 checkBlock 을 걸면 빛·하늘빛 원천이 보통 쓰기 세계와 같아진다
//   (Paper(Starlight) 도 checkBlock 이 그 칸을 원천까지 다시 본다). 빛 다시 계산이 둘레를 계산하는 동안에는 끼운 뒤로 붙잡힌다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.config.LightSettings;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LightSurfaceTest {

    private static final int CX = 8, CZ = 8, MIN_Y = -64;

    @AfterEach
    void restore() { LightSettings.install(LightSettings.DEFAULT); }

    private static void rawWrite(ChunkColumn col, int x, int y, int z, int state) {
        int local = y - MIN_Y;
        col.setSectionStateRaw(local >> 4, (local & 15) << 8 | (z & 15) << 4 | (x & 15), state, true);
    }

    /** 원시 쓰기 장면: 공중 발광석, 하늘을 가리는 돌판(원천이 바뀐다), 땅속 발광석. 칸마다 검사를 건다. */
    private static int[][] scene() {
        int glow = B.defaultState("glowstone"), stone = B.defaultState("stone");
        int bx = CX * 16, bz = CZ * 16;
        java.util.List<int[]> l = new java.util.ArrayList<>();
        l.add(new int[] {bx + 8, -55, bz + 8, glow});
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) l.add(new int[] {bx + x, -50, bz + z, stone});
        l.add(new int[] {bx + 12, -62, bz + 3, glow});
        return l.toArray(new int[0][]);
    }

    // ------------------------------------------------------------------ 공개 스냅샷의 빛

    @Test
    void readViewHoldsTheLightOfItsMoment() throws Exception {
        try (Env env = new Env(false)) {
            Pos glow = new Pos(CX * 16 + 7, -58, CZ * 16 + 7);
            env.at(r -> {
                ChunkReadView v1 = r.publishReadViewNow(CX, CZ);
                assertTrue(v1.lightReady());
                assertEquals(0, v1.light(false, glow.x(), glow.y(), glow.z()));
                assertEquals(15, v1.light(true, glow.x(), glow.y(), glow.z()), "평지 위 공기는 하늘빛 15");
                assertSame(v1, r.publishReadViewNow(CX, CZ), "바뀐 것이 없으면 같은 판");

                r.level().setBlock(glow, B.defaultState("glowstone"), 2);
                r.level().drainLight();
                assertEquals(15, r.level().getBlockLight(glow));
                assertEquals(0, v1.light(false, glow.x(), glow.y(), glow.z()), "낸 판은 그때의 빛 그대로");

                ChunkReadView v2 = r.publishReadViewNow(CX, CZ);
                assertNotSame(v1, v2);
                assertEquals(15, v2.light(false, glow.x(), glow.y(), glow.z()));
                assertEquals(14, v2.light(false, glow.x() + 1, glow.y(), glow.z()));
                ChunkLight live = r.ownedChunk(CX, CZ).light();
                int sy = glow.y() >> 4;
                assertArrayEquals(live.block(sy - ChunkLight.MIN_SECTION), v2.lightLayer(false, sy), "층 사본 = 지금 층");
                byte[] copy = v2.lightLayer(false, sy);
                copy[0] = 0x77;
                assertEquals(ChunkLight.get(live.block(sy - ChunkLight.MIN_SECTION), 0, 0, 0), v2.light(false, CX * 16, sy << 4, CZ * 16), "준 사본을 고쳐도 판은 그대로");
                assertNull(v2.lightLayer(false, ChunkLight.MIN_SECTION - 1), "빛 섹션 범위 밖");
                assertEquals(-1, v2.light(false, glow.x(), ChunkLight.MIN_SECTION * 16 - 1, glow.z()), "서버 빛 배열 밖은 자료 없음(던지지 않음)");

                // 빛만 바뀌어도(블록 그대로) 새 판 — 가짜 광원은 블록을 바꾸지 않는다
                Pos far = new Pos(CX * 16 + 15, 20, CZ * 16 + 15);   // 발광석에서 15칸 넘게 떨어진 공중
                assertTrue(r.level().setVirtualLight("t", far.x(), far.y(), far.z(), 9));
                ChunkReadView v3 = r.publishReadViewNow(CX, CZ);
                assertNotSame(v2, v3);
                assertEquals(9, v3.light(false, far.x(), far.y(), far.z()));
                assertEquals(0, v2.light(false, far.x(), far.y(), far.z()));

                // v2·v3 가 나눠 쥔 층은 이미 이 청크 몫(공유 상수 아님) — 한 번 더 써도 낸 판은 그대로여야 한다(쓰기 시 복사의 표시 비트)
                r.level().setBlock(glow, B.defaultState("air"), 2);
                r.level().drainLight();
                assertEquals(0, r.level().getBlockLight(glow));
                assertEquals(15, v2.light(false, glow.x(), glow.y(), glow.z()), "낸 판은 다음 쓰기에도 그대로");
                assertEquals(15, v3.light(false, glow.x(), glow.y(), glow.z()), "낸 판은 다음 쓰기에도 그대로");
                assertEquals(0, r.publishReadViewNow(CX, CZ).light(false, glow.x(), glow.y(), glow.z()));
            });
        }
    }

    // ------------------------------------------------------------------ 검사 걸기

    /** 원시 쓰기 + 칸마다 checkLightAt = 보통 쓰기로 놓은 세계(빛·하늘빛 원천). 둘레 청크의 빛도 같다. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void checkAfterRawWritesMatchesNormalWrites(boolean asyncLight) throws Exception {
        try (Env oracle = new Env(asyncLight); Env env = new Env(asyncLight)) {
            for (int[] b : scene()) oracle.at(r -> r.level().setBlock(new Pos(b[0], b[1], b[2]), b[3], 2));
            oracle.at(r -> r.level().drainLight());
            env.at(r -> {
                ChunkColumn col = r.ownedChunk(CX, CZ);
                for (int[] b : scene()) rawWrite(col, b[0], b[1], b[2], b[3]);
                r.level().drainLight();
                assertEquals(0, r.level().getBlockLight(new Pos(CX * 16 + 8, -55, CZ * 16 + 8)), "원시 쓰기는 빛을 바꾸지 않는다");
                for (int[] b : scene()) assertTrue(r.level().checkLightAt(b[0], b[1], b[2]));
                r.level().drainLight();
            });
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = CX + dx, cz = CZ + dz;
                    assertArrayEquals(oracle.<int[]>call(r -> lightOf(r.ownedChunk(cx, cz).light())), env.<int[]>call(r -> lightOf(r.ownedChunk(cx, cz).light())),
                            "빛 = 보통 쓰기 세계 " + cx + "," + cz);
                }
            }
            assertArrayEquals(oracle.<int[]>call(r -> r.ownedChunk(CX, CZ).light().lowestSource.clone()),
                    env.<int[]>call(r -> r.ownedChunk(CX, CZ).light().lowestSource.clone()), "하늘빛 원천 = 보통 쓰기 세계");
            assertTrue(env.<Boolean>call(r -> r.ownedChunk(CX, CZ).dirty()), "바뀐 빛은 저장 대상");
        }
    }

    @Test
    void checkIsDroppedOutsideHeightOrUnloaded() throws Exception {
        try (Env env = new Env(false)) {
            env.at(r -> {
                assertFalse(r.level().checkLightAt(CX * 16, 320, CZ * 16), "높이 밖");
                assertFalse(r.level().checkLightAt(CX * 16, -65, CZ * 16), "높이 밖");
                assertFalse(r.level().checkLightAt(9000 * 16, 0, 9000 * 16), "로드되지 않은 청크");
                assertFalse(r.level().lightEngine().hasWork());
                assertTrue(r.level().checkLightAt(CX * 16, 0, CZ * 16));
            });
        }
    }

    /** 빛 다시 계산(T-197)이 둘레를 계산하는 동안 건 검사는 붙잡혔다가 끼운 뒤 계산된다(Paper 는 relight 동안 빛 대기열을 미룬다). */
    @Test
    void checkDuringRelightIsHeldUntilInstalled() throws Exception {
        try (Env env = new Env(false)) {
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            env.world.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            env.world.relight().relight(new long[] {Regionizer.chunkKey(CX, CZ)}, null, done::complete);
            long until = System.currentTimeMillis() + 10_000;
            while (jobs.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(5);
            assertEquals(1, jobs.size(), "계산 대기");
            Pos g = new Pos((CX + 1) * 16, -58, CZ * 16 + 7);   // 동쪽 이웃 경계
            env.at(r -> {
                rawWrite(r.ownedChunk(CX + 1, CZ), g.x(), g.y(), g.z(), B.defaultState("glowstone"));
                assertTrue(r.level().checkLightAt(g.x(), g.y(), g.z()));
                r.level().drainLight();
                assertEquals(0, r.level().getBlockLight(g), "붙잡혀 있다");
                assertTrue(r.level().lightEngine().heldCount() > 0);
            });
            jobs.poll().run();
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            env.at(r -> {
                r.level().drainLight();
                assertEquals(15, r.level().getBlockLight(g), "끼운 뒤 풀려 계산됐다");
                assertEquals(14, r.level().getBlockLight(new Pos(g.x() - 1, g.y(), g.z())), "다시 계산한 청크 쪽으로도 번졌다");
                assertEquals(0, r.level().lightEngine().heldCount());
            });
        }
    }

    // ------------------------------------------------------------------ 측정

    /**
     * -Dfurnace.bench=true 일 때만. 가장 나쁜 경우: 다른 스레드가 보는 청크(매 틱 공개)에서 매 틱 발광석 16개를 켜고 끈다.
     * 공개 스냅샷과 빛 층을 나눠 쓰므로 공개 뒤 첫 쓰기는 층을 복사한다 — 그 몫을 "매 틱 공개" 대 "공개 없음" 으로 잰다(교대 5라운드).
     */
    @Test
    void watchedChunkLampBench() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        try (Env env = new Env(false)) {
            int glow = B.defaultState("glowstone"), air = B.defaultState("air");
            Pos[] lamps = new Pos[16];
            for (int i = 0; i < 16; i++) lamps[i] = new Pos(CX * 16 + (i & 3) * 4 + 1, -60 + (i >> 2) * 4, CZ * 16 + (i >> 2) * 4 + 1);
            int warm = 300, ticks = 1000;
            for (int round = 0; round < 5; round++) {
                for (boolean publish : new boolean[] {false, true}) {
                    long[] ns = new long[1];
                    env.at(r -> {
                        for (int t = 0; t < warm + ticks; t++) {
                            long t0 = System.nanoTime();
                            int s = (t & 1) == 0 ? glow : air;
                            for (Pos p : lamps) r.level().setBlock(p, s, 2);
                            r.level().drainLight();
                            if (publish) r.publishReadViewNow(CX, CZ);
                            if (t >= warm) ns[0] += System.nanoTime() - t0;
                        }
                    });
                    System.out.printf("[T-205 bench] round %d publish=%s : %.1f us/tick%n", round, publish, ns[0] / 1000.0 / ticks);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 도구

    private static int[] lightOf(ChunkLight l) {
        assertNotNull(l);
        int[] out = new int[ChunkLight.SECTIONS * 2 * 4096];
        int o = 0;
        for (int s = 0; s < ChunkLight.SECTIONS; s++) {
            byte[] sky = l.sky(s), block = l.block(s);
            for (int i = 0; i < 4096; i++) {
                int x = i & 15, z = i >> 4 & 15, y = i >> 8;
                out[o++] = ChunkLight.get(sky, x, y, z);
                out[o++] = ChunkLight.get(block, x, y, z);
            }
        }
        return out;
    }

    /** 평지 세계 하나 — C 둘레 9×9 를 한 region 에 올린다. */
    private static final class Env implements AutoCloseable {
        final World world;
        private final FurnaceSchedulerImpl sched;
        private final ExecutorService ioPool = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();

        Env(boolean asyncLight) throws Exception {
            LightSettings.install(new LightSettings(asyncLight, 1));
            ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
            sched = new FurnaceSchedulerImpl(10_000_000L, 2);
            BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
            world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 2, 4, 1, 2, 2, reg.directBits(), 7, ioPool, gen);
            world.regionizer().want(CX, CZ, null);
            at(r -> {
                for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) r.chunkAtBlocking(new ChunkPos(CX + dx, CZ + dz));
                r.level().drainLight();
            });
        }

        void at(Consumer<Region> task) throws Exception {
            call(r -> {
                task.accept(r);
                return null;
            });
        }

        <T> T call(Function<Region, T> task) throws Exception {
            var f = new CompletableFuture<T>();
            world.runAtChunk(new ChunkPos(CX, CZ), r -> {
                try {
                    f.complete(task.apply(r));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            return f.get(30, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            ioPool.shutdown();
            gen.shutdown();
            sched.close();
        }
    }
}
