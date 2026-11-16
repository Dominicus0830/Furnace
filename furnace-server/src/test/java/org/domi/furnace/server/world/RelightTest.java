// T-197 빛 다시 계산(Paper starlight$serverRelightChunks 의 서버 몫)을 실제 World·Region 으로. 설계 docs/feature-parity/compat-nms-n2b5-relight-2026-11-16.md 7장.
// - 원시 쓰기(빛 없음)로 바꾼 청크를 다시 계산하면 그 청크의 빛 = 같은 블록을 보통 쓰기로 놓은 세계의 빛 = 7×7 을 새로 켠 값, 둘레는 그대로(Paper).
// - 계산하는 동안 둘레에서 일어난 빛 갱신은 붙잡혔다가 끼운 뒤 풀려 잃지 않는다(무작위 퍼즈 — 5×5 전체를 새로 켠 값과 비교, 붙잡기를 끄면 틀린다).
// - 저장·종료·둘레 재생성·같은 청크 되풀이 relight.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
import org.domi.furnace.server.light.RelightCompute;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelightTest {

    /** 다시 계산할 청크(구역 0 한가운데 — 7×7 이 한 region). */
    private static final int CX = 8, CZ = 8;
    private static final int MIN_Y = -64;
    private static final long C = Regionizer.chunkKey(CX, CZ);

    @AfterEach
    void restore() { LightSettings.install(LightSettings.DEFAULT); }

    // ------------------------------------------------------------------ 장면

    /** 원시 쓰기 장면(빛 갱신 없음): C 한가운데 공중 발광석, 하늘을 가리는 돌판 5×5, 땅속에 묻은 발광석. 모두 맨 아래 섹션(공기뿐 섹션 아님). */
    private static List<int[]> rawScene() {
        int glow = B.defaultState("glowstone"), stone = B.defaultState("stone");
        int bx = CX * 16, bz = CZ * 16;
        List<int[]> l = new ArrayList<>();
        l.add(new int[] {bx + 8, -55, bz + 8, glow});
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) l.add(new int[] {bx + x, -50, bz + z, stone});
        l.add(new int[] {bx + 12, -62, bz + 3, glow});
        return l;
    }

    private static void rawWrite(ChunkColumn col, int x, int y, int z, int state) {
        int local = y - MIN_Y;
        col.setSectionStateRaw(local >> 4, (local & 15) << 8 | (z & 15) << 4 | (x & 15), state, true);
    }

    // ------------------------------------------------------------------ Paper 계약

    /** 받은 수·콜백·done, 그 청크의 빛 = 보통 쓰기 세계 = 7×7 새로 켠 값, 둘레는 그대로, 저장 대상, 붙잡기·핀 0 으로 끝. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void relightMatchesIncrementalAndFreshLightAndLeavesNeighboursAlone(boolean asyncLight) throws Exception {
        try (Env oracle = new Env(asyncLight); Env env = new Env(asyncLight)) {
            for (int[] b : rawScene()) oracle.at(r -> r.level().setBlock(new Pos(b[0], b[1], b[2]), b[3], 2));
            oracle.at(r -> r.level().drainLight());
            int[] expected = oracle.call(r -> lightOf(r.ownedChunk(CX, CZ)));

            env.at(r -> {
                ChunkColumn col = r.ownedChunk(CX, CZ);
                for (int[] b : rawScene()) rawWrite(col, b[0], b[1], b[2], b[3]);
                assertEquals(0, r.level().getBlockLight(new Pos(CX * 16 + 8, -55, CZ * 16 + 8)), "원시 쓰기는 빛을 바꾸지 않는다");
            });
            int[][] ringBefore = env.call(RelightTest::ring);
            assertFalse(java.util.Arrays.equals(expected, env.call(r -> lightOf(r.ownedChunk(CX, CZ)))), "다시 계산 전에는 빛이 다르다(시험이 뭔가를 본다)");

            List<Long> called = Collections.synchronizedList(new ArrayList<>());
            List<String> threads = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Integer> done = new CompletableFuture<>();
            int n = env.world.relight().relight(new long[] {C, C, Regionizer.chunkKey(5000, 5000)}, k -> {
                called.add(k);
                threads.add(Thread.currentThread().getName());
            }, i -> {
                threads.add(Thread.currentThread().getName());
                done.complete(i);
            });
            assertEquals(1, n, "로드된 청크만, 한 번씩 받는다");
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            assertEquals(List.of(C), called);
            assertTrue(threads.stream().allMatch(t -> t.startsWith("furnace-relight-callback-")), "콜백은 region 스레드가 아니라 콜백 줄에서: " + threads);
            env.at(r -> r.level().drainLight());

            assertArrayEquals(expected, env.call(r -> lightOf(r.ownedChunk(CX, CZ))), "다시 계산한 빛 = 보통 쓰기로 놓은 세계의 빛");
            assertArrayEquals(oracle.<int[]>call(r -> r.ownedChunk(CX, CZ).light().lowestSource.clone()),
                    env.<int[]>call(r -> r.ownedChunk(CX, CZ).light().lowestSource.clone()), "하늘빛 원천 높이 = 보통 쓰기 세계");
            assertArrayEquals(env.<int[]>call(r -> freshLight(r, 3)[3 * 7 + 3]), env.<int[]>call(r -> lightOf(r.ownedChunk(CX, CZ))), "다시 계산한 빛 = 7×7 을 새로 켠 값");
            int[][] ringAfter = env.call(RelightTest::ring);
            for (int i = 0; i < ringBefore.length; i++) assertArrayEquals(ringBefore[i], ringAfter[i], "둘레 청크 " + i + " 의 빛은 그대로(Paper 와 같다)");
            assertTrue(env.<Boolean>call(r -> r.ownedChunk(CX, CZ).dirty()), "다시 계산한 청크는 저장 대상");
            assertEquals(0, env.world.relight().holdCount());
            assertFalse(env.world.relight().pinned(C));
        }
    }

    /** 받은 것이 없으면 0 을 돌려주고 done(0) 이 온다. */
    @Test
    void emptyRequestCompletesWithZero() throws Exception {
        try (Env env = new Env(false)) {
            CompletableFuture<Integer> done = new CompletableFuture<>();
            assertEquals(0, env.world.relight().relight(new long[] {Regionizer.chunkKey(-4000, 7)}, k -> { throw new AssertionError(); }, done::complete));
            assertEquals(0, done.get(10, TimeUnit.SECONDS));
        }
    }

    // ------------------------------------------------------------------ 붙잡기(검토 R6 a·b)

    /**
     * 이미 맞는 빛의 5×5 에서 가운데를 relight 하고, 일꾼을 잡은 동안 C·둘레·거리 2 에 무작위로 블록·광원을 바꾼다. 끝난 뒤 5×5 전체 = 같은 블록으로
     * 새로 켠 값(relight 대상이 원래 맞았으므로 "둘레 그대로" 효과와 섞이지 않고 붙잡기만 잰다).
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void concurrentChangesDuringComputeAreNotLost(int seed) throws Exception {
        for (boolean async : new boolean[] {false, true}) {
            int bad = fuzz(seed, async, true);
            assertEquals(0, bad, "시드 " + seed + " async=" + async + ": 새로 켠 값과 다른 청크 수");
        }
    }

    /** 변이: 붙잡기를 끄면 위 퍼즈가 실패해야 한다(4.3 예 — 끼우기가 계산 중 바뀐 빛을 덮는다). */
    @Test
    void withoutHoldsTheFuzzFails() throws Exception {
        int bad = 0;
        for (int seed = 1; seed <= 4; seed++) bad += fuzz(seed, false, false);
        assertTrue(bad > 0, "붙잡기 없이도 맞았다 — 퍼즈가 붙잡기를 시험하지 못한다");
    }

    /** 한 판: 틀린 청크 수(5×5 중). */
    private static int fuzz(int seed, boolean async, boolean holds) throws Exception {
        Random rnd = new Random(seed);
        int[] palette = {B.defaultState("glowstone"), B.defaultState("sea_lantern"), B.defaultState("stone"), B.defaultState("glass"),
                B.defaultState("air"), B.defaultState("air")};
        try (Env env = new Env(async)) {
            env.world.relight().holdsEnabledForTest(holds);
            // 처음 장면(빛이 맞는 상태): C 와 이웃 경계 근처에 광원·가림
            for (int i = 0; i < 40; i++) {
                Pos p = randomPos(rnd);
                int s = palette[rnd.nextInt(palette.length)];
                env.at(r -> r.level().setBlock(p, s, 2));
            }
            env.at(r -> r.level().drainLight());
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            env.world.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            env.world.relight().relight(new long[] {C}, null, done::complete);
            awaitJob(jobs);
            for (int i = 0; i < 30; i++) {
                Pos p = randomPos(rnd);
                int s = palette[rnd.nextInt(palette.length)];
                boolean drain = rnd.nextInt(4) == 0;
                env.at(r -> {
                    r.level().setBlock(p, s, 2);
                    if (drain) r.level().drainLight();
                });
            }
            jobs.poll().run();
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            env.at(r -> r.level().drainLight());
            int[][] fresh = env.call(r -> freshLight(r, 3));
            int[][] got = env.call(r -> lightArea(r, 3));
            int bad = 0;
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                int i = (dz + 3) * 7 + dx + 3;
                if (!java.util.Arrays.equals(fresh[i], got[i])) bad++;
            }
            return bad;
        }
    }

    /** C 를 가운데로 거리 2 청크 안, 경계 쪽에 치우친 칸(땅 위 맨 아래 섹션). */
    private static Pos randomPos(Random rnd) {
        int cx = CX + rnd.nextInt(5) - 2, cz = CZ + rnd.nextInt(5) - 2;
        int[] edge = {0, 1, 2, 13, 14, 15, 7, 8};
        int x = cx * 16 + edge[rnd.nextInt(edge.length)], z = cz * 16 + rnd.nextInt(16);
        if (rnd.nextBoolean()) {
            x = cx * 16 + rnd.nextInt(16);
            z = cz * 16 + edge[rnd.nextInt(edge.length)];
        }
        return new Pos(x, -60 + rnd.nextInt(10), z);
    }

    // ------------------------------------------------------------------ 저장·종료(검토 R1)

    /** 계산 중 저장: 붙잡힌 검사의 체비셰프 1 안 청크는 저장 빛을 믿지 않게 표시하고, 더 먼 청크는 표시하지 않는다. */
    @Test
    void savingWhileChecksAreHeldMarksNearbyLightStale() throws Exception {
        try (Env env = new Env(true)) {
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            env.world.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            env.world.relight().relight(new long[] {C}, null, done::complete);
            awaitJob(jobs);
            Pos east = new Pos((CX + 1) * 16 + 15, -58, CZ * 16 + 7);   // 동쪽 이웃의 먼 끝 — 그 옆 청크(CX+2)도 빛을 받는다
            env.at(r -> {
                r.level().setBlock(east, B.defaultState("glowstone"), 2);
                for (int dx = -3; dx <= 3; dx++) r.level().settleLightForSave(CX + dx, CZ);
            });
            assertTrue(env.world.lightStale().contains(CX + 1, CZ), "붙잡힌 검사가 있는 청크");
            assertTrue(env.world.lightStale().contains(CX + 2, CZ), "붙잡힌 검사 옆 청크(붙잡기 밖) — 풀리면 빛이 번진다");
            assertTrue(env.world.lightStale().contains(CX, CZ), "C");
            assertFalse(env.world.lightStale().contains(CX - 1, CZ), "붙잡힌 검사에서 2 청크 떨어진 청크");
            jobs.poll().run();
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            env.at(r -> {
                r.level().drainLight();
                assertEquals(15, r.level().getBlockLight(east), "풀린 검사가 계산됐다");
            });
        }
    }

    /** 종료: 붙잡기를 모두 풀어 저장 전 빛 계산이 마저 하고, 끼우지 못한 대상은 stale, 늦게 끝난 계산은 끼우지 않고 콜백도 부르지 않는다. */
    @Test
    void shutdownReleasesHoldsAndMarksUninstalledTargets() throws Exception {
        try (Env env = new Env(false)) {
            env.at(r -> {
                ChunkColumn col = r.ownedChunk(CX, CZ);
                for (int[] b : rawScene()) rawWrite(col, b[0], b[1], b[2], b[3]);
            });
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            env.world.relight().useExecutor(jobs::add);
            List<Long> called = Collections.synchronizedList(new ArrayList<>());
            env.world.relight().relight(new long[] {C}, called::add, null);
            awaitJob(jobs);
            Pos east = new Pos((CX + 1) * 16 + 1, -58, CZ * 16 + 7);
            env.at(r -> {
                r.level().setBlock(east, B.defaultState("glowstone"), 2);
                r.level().drainLight();
                assertEquals(0, r.level().getBlockLight(east), "붙잡혀 있다");
            });
            env.world.relight().shutdown();
            assertEquals(0, env.world.relight().holdCount());
            assertTrue(env.world.lightStale().contains(CX, CZ), "끼우지 못한 대상 — 들여온 블록 + 옛 빛");
            env.at(r -> {
                r.saveDirtyChunks();
                assertEquals(15, r.level().getBlockLight(east), "저장 전 빛 계산이 풀린 검사를 마저 했다");
            });
            int[] before = env.call(r -> lightOf(r.ownedChunk(CX, CZ)));
            jobs.poll().run();
            Thread.sleep(200);
            env.at(r -> { });
            assertArrayEquals(before, env.call(r -> lightOf(r.ownedChunk(CX, CZ))), "종료 뒤 끝난 계산은 끼우지 않는다");
            assertTrue(called.isEmpty(), "종료 뒤에는 콜백을 부르지 않는다");
            CompletableFuture<Integer> late = new CompletableFuture<>();
            assertEquals(0, env.world.relight().relight(new long[] {C}, null, late::complete), "종료 뒤 새 요청은 받지 않는다");
            assertEquals(0, late.get(10, TimeUnit.SECONDS));
        }
    }

    // ------------------------------------------------------------------ 둘레 컬럼이 바뀜(검토 R3)

    /**
     * 계산 중 동쪽 이웃을 재생성한다(같은 키, 다른 컬럼 — 내렸다 다시 올린 것과 같은 꼴). 계산은 옛 이웃(경계에 발광석)으로 했으므로 그대로 끼우면 C 에
     * 유령 빛이 남는다 → 다시 계산해야 한다. 끝난 뒤 C 의 빛 = 7×7 을 새로 켠 값.
     */
    @Test
    void replacedNeighbourColumnTriggersRecompute() throws Exception {
        try (Env env = new Env(false)) {
            Pos east = new Pos((CX + 1) * 16, -58, CZ * 16 + 7);
            env.at(r -> {
                r.level().setBlock(east, B.defaultState("glowstone"), 2);
                r.level().drainLight();
                ChunkColumn col = r.ownedChunk(CX, CZ);
                for (int[] b : rawScene()) rawWrite(col, b[0], b[1], b[2], b[3]);
            });
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            env.world.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            env.world.relight().relight(new long[] {C}, null, done::complete);
            awaitJob(jobs);
            ChunkColumn oldEast = env.call(r -> r.ownedChunk(CX + 1, CZ));
            assertTrue(env.world.regenerateChunk(CX + 1, CZ, false, false).get(30, TimeUnit.SECONDS));
            assertTrue(oldEast != env.call(r -> r.ownedChunk(CX + 1, CZ)), "재생성은 컬럼을 바꾼다");
            jobs.poll().run();   // 옛 이웃으로 계산 → 설치가 다른 컬럼을 보고 다시 준비
            long until = System.currentTimeMillis() + 10_000;
            while (!done.isDone() && System.currentTimeMillis() < until) {
                Runnable j = jobs.poll();
                if (j != null) j.run();
                else Thread.sleep(5);
            }
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            env.at(r -> r.level().drainLight());
            assertArrayEquals(env.<int[]>call(r -> freshLight(r, 3)[3 * 7 + 3]), env.<int[]>call(r -> lightOf(r.ownedChunk(CX, CZ))), "C = 새로 켠 값(유령 빛 없음)");
        }
    }

    /**
     * 계산 중 둘레에 새 청크가 올라온다(계산 때는 없음). 그 청크의 빛 켜기가 대상으로 민 하늘빛(지붕 밑 가장자리)은 검사가 아닌 전파라 붙잡히지 않는다 —
     * 그대로 끼우면 덮이므로 다시 계산해야 한다. 끝난 뒤 대상의 빛 = 3×3 을 새로 켠 값.
     */
    @Test
    void newlyLoadedNeighbourTriggersRecompute() throws Exception {
        try (Env env = new Env(false)) {
            int tx = CX + 4, tz = CZ;   // 올린 9×9 의 동쪽 끝 — 동쪽 이웃(CX+5)은 아직 없다
            long t = Regionizer.chunkKey(tx, tz);
            env.at(r -> {
                ChunkColumn col = r.ownedChunk(tx, tz);
                int stone = B.defaultState("stone");
                for (int x = 10; x <= 15; x++) for (int z = 0; z < 16; z++) rawWrite(col, tx * 16 + x, -55, tz * 16 + z, stone);
                assertTrue(r.ownedChunk(tx + 1, tz) == null, "동쪽 이웃은 아직 없다");
            });
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            env.world.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            env.world.relight().relight(new long[] {t}, null, done::complete);
            awaitJob(jobs);
            env.at(r -> r.chunkAtBlocking(new ChunkPos(tx + 1, tz)));   // 계산 중 이웃이 올라와 지붕 밑으로 하늘빛을 민다
            jobs.poll().run();
            long until = System.currentTimeMillis() + 10_000;
            while (!done.isDone() && System.currentTimeMillis() < until) {
                Runnable j = jobs.poll();
                if (j != null) j.run();
                else Thread.sleep(5);
            }
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            env.at(r -> r.level().drainLight());
            assertArrayEquals(env.<int[]>call(r -> freshLight(r, tx, tz, 1)[4]), env.<int[]>call(r -> lightOf(r.ownedChunk(tx, tz))),
                    "대상 = 새 이웃까지 넣어 새로 켠 값");
        }
    }

    // ------------------------------------------------------------------ 되풀이 relight(검토 R4)

    /** 플러그인이 같은 청크를 쉬지 않고 relight 해도 그 옆 빛 갱신(발광석 놓기)은 정해진 시간 안에 반영된다. */
    @Test
    void repeatedRelightDoesNotStarveNearbyLight() throws Exception {
        try (Env env = new Env(false)) {
            java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicInteger finished = new java.util.concurrent.atomic.AtomicInteger();
            Thread spam = new Thread(() -> {
                while (!stop.get()) {
                    env.world.relight().relight(new long[] {C}, null, i -> finished.incrementAndGet());
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            spam.start();
            try {
                Thread.sleep(200);
                Pos east = new Pos((CX + 1) * 16, -58, CZ * 16 + 7);
                env.at(r -> r.level().setBlock(east, B.defaultState("glowstone"), 2));
                long until = System.currentTimeMillis() + 5_000;
                int light = 0;
                while (System.currentTimeMillis() < until && (light = env.call(r -> r.level().getBlockLight(east))) != 15) Thread.sleep(20);
                assertEquals(15, light, "되풀이 relight 중에도 옆 빛이 반영된다");
            } finally {
                stop.set(true);
                spam.join();
            }
            assertTrue(finished.get() > 1, "relight 가 실제로 되풀이됐다: " + finished.get());
        }
    }

    // ------------------------------------------------------------------ 빛 읽기

    private static void awaitJob(ConcurrentLinkedQueue<Runnable> jobs) throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        while (jobs.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(5);
        assertEquals(1, jobs.size(), "준비가 끝나 계산이 대기 중");
    }

    /** 청크 빛 전체(하늘·블록, 모든 섹션). */
    private static int[] lightOf(ChunkColumn col) {
        assertNotNull(col);
        return lightOf(col.light());
    }

    private static int[] lightOf(ChunkLight l) {
        assertNotNull(l);
        int[] out = new int[ChunkLight.SECTIONS * 2 * 4096];
        int o = 0;
        for (int s = 0; s < ChunkLight.SECTIONS; s++) {
            byte[] sky = l.sky(s), block = l.block(s);
            for (int i = 0; i < 4096; i++) {
                int x = i & 15, z = i >> 4 & 15, y = i >> 8;
                out[o++] = sky == null ? -1 : ChunkLight.get(sky, x, y, z);
                out[o++] = block == null ? -1 : ChunkLight.get(block, x, y, z);
            }
        }
        return out;
    }

    /** C 둘레 8 청크의 빛. */
    private static int[][] ring(Region r) {
        int[][] out = new int[8][];
        int i = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (dx != 0 || dz != 0) out[i++] = lightOf(r.ownedChunk(CX + dx, CZ + dz));
        return out;
    }

    /** C 둘레 (2·rad+1)² 의 지금 빛(행 우선, dz 바깥). */
    private static int[][] lightArea(Region r, int rad) {
        int w = 2 * rad + 1;
        int[][] out = new int[w * w][];
        for (int dz = -rad; dz <= rad; dz++) for (int dx = -rad; dx <= rad; dx++) out[(dz + rad) * w + dx + rad] = lightOf(r.ownedChunk(CX + dx, CZ + dz));
        return out;
    }

    /** C 둘레 (2·rad+1)² 를 지금 블록으로 빈 빛에서 켠 값(바깥 줄은 이웃이 빠져 있어 비교에 쓰지 않는다). 주인 스레드. */
    private static int[][] freshLight(Region r, int rad) { return freshLight(r, CX, CZ, rad); }

    /** (CX, CZ) 둘레 — 로드된 청크만 넣는다. */

    private static int[][] freshLight(Region r, int CX, int CZ, int rad) {
        RegionLevel lv = r.level();
        RelightCompute rc = new RelightCompute(lv.minBuildHeight(), lv.maxBuildHeight(), lv.lightBedrockState(), lv.hasSkyLight(), r.world().virtualLights());
        List<ChunkColumn.LightBlocks> frozen = new ArrayList<>();
        for (int dz = -rad; dz <= rad; dz++) for (int dx = -rad; dx <= rad; dx++) {
            ChunkColumn col = r.ownedChunk(CX + dx, CZ + dz);
            if (col == null) continue;   // 없는 청크는 빛도 없는 것으로(Furnace·바닐라 빛 규칙)
            ChunkColumn.LightBlocks lb = col.freezeForLight();
            frozen.add(lb);
            rc.addColumn(CX + dx, CZ + dz, lb);
        }
        try {
            rc.run();
        } finally {
            for (var lb : frozen) lb.thaw();
        }
        int w = 2 * rad + 1;
        int[][] out = new int[w * w][];
        for (int dz = -rad; dz <= rad; dz++) for (int dx = -rad; dx <= rad; dx++) {
            ChunkLight l = rc.result(CX + dx, CZ + dz);
            out[(dz + rad) * w + dx + rad] = l == null ? null : lightOf(l);
        }
        return out;
    }

    // ------------------------------------------------------------------ 세계

    /** 평지 세계 하나 — C 둘레 9×9 를 한 region 에 올린다(7×7 의 바깥 줄도 이웃이 있게). */
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
