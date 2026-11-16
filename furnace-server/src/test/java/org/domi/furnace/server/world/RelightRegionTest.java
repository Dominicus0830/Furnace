// 빛 다시 계산(T-197)의 region 경계 시험 — 계산 중 region 합치기·나누기, 타일이 두 region 에 걸침, 준비 실패 정리(PR #439 독립 검토 재현에서 옮김).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelightRegionTest {
    static final int CX = 8, CZ = 0;
    static final long C = Regionizer.chunkKey(CX, CZ);

    @AfterEach
    void restore() { LightSettings.install(LightSettings.DEFAULT); }

    interface Body { void run(World w, Regionizer rz) throws Exception; }

    static void withWorld(boolean async, Body b) throws Exception {
        LightSettings.install(new LightSettings(async, 1));
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = Executors.newSingleThreadExecutor();
        var gen = Executors.newSingleThreadExecutor();
        try {
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 3, 4, 1, 2, 2, reg.directBits(), 7, io, gen);
            Regionizer rz = w.regionizer();
            rz.want(CX, CZ, null);
            rz.want(40, 0, null);
            at(w, CX, r -> {
                for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) r.chunkAtBlocking(new ChunkPos(CX + dx, CZ + dz));
                r.level().drainLight();
            });
            at(w, 40, r -> {
                for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) r.chunkAtBlocking(new ChunkPos(40 + dx, CZ + dz));
                r.level().drainLight();
            });
            b.run(w, rz);
        } finally {
            io.shutdown();
            gen.shutdown();
            sched.close();
        }
    }

    static void at(World w, int cx, java.util.function.Consumer<Region> task) throws Exception {
        var done = new CompletableFuture<Void>();
        w.runAtChunk(new ChunkPos(cx, 0), r -> {
            try {
                task.accept(r);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(30, TimeUnit.SECONDS);
    }

    static <T> T call(World w, int cx, java.util.function.Function<Region, T> f) throws Exception {
        var done = new CompletableFuture<T>();
        w.runAtChunk(new ChunkPos(cx, 0), r -> {
            try {
                done.complete(f.apply(r));
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done.get(30, TimeUnit.SECONDS);
    }

    static void merge(World w, Regionizer rz) throws Exception {
        rz.want(24, 0, null);
        long until = System.currentTimeMillis() + 10_000;
        while (rz.ownerOf(CX, 0) != rz.ownerOf(40, 0) && System.currentTimeMillis() < until) Thread.sleep(10);
        assertSame(rz.ownerOf(CX, 0), rz.ownerOf(40, 0), "합쳐짐");
    }

    static void split(World w, Regionizer rz) throws Exception {
        for (int tries = 0; tries < 10; tries++) {
            Region o = rz.ownerOf(24, 0);
            if (o == null) break;
            rz.release(o, 24, 0);
            if (!o.present.contains(Regionizer.chunkKey(24, 0))) break;
        }
        long until = System.currentTimeMillis() + 15_000;
        while (rz.ownerOf(CX, 0) == rz.ownerOf(40, 0) && System.currentTimeMillis() < until) Thread.sleep(10);
        assertNotSame(rz.ownerOf(CX, 0), rz.ownerOf(40, 0), "나눠짐");
    }

    static int[] lightOf(ChunkLight l) {
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

    /** C 둘레 3×3 이 5×5 를 새로 켠 값과 같은지 — 틀린 청크 수. */
    static int badAround(Region r) { return badAround(r, CX); }
    static int badAround(Region r, int CX) {
        RegionLevel lv = r.level();
        RelightCompute rc = new RelightCompute(lv.minBuildHeight(), lv.maxBuildHeight(), lv.lightBedrockState(), lv.hasSkyLight(), r.world().virtualLights());
        List<ChunkColumn.LightBlocks> frozen = new ArrayList<>();
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            ChunkColumn col = r.ownedChunk(CX + dx, CZ + dz);
            ChunkColumn.LightBlocks lb = col.freezeForLight();
            frozen.add(lb);
            rc.addColumn(CX + dx, CZ + dz, lb);
        }
        try { rc.run(); } finally { for (var lb : frozen) lb.thaw(); }
        int bad = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (!java.util.Arrays.equals(lightOf(rc.result(CX + dx, CZ + dz)), lightOf(r.ownedChunk(CX + dx, CZ + dz).light()))) bad++;
        }
        return bad;
    }

    static final Pos GLOW = new Pos((CX + 1) * 16, -58, 5);        // 동쪽 이웃 서쪽 끝 — 빛이 C 로 번진다
    static final Pos GLOW40 = new Pos(41 * 16, -58, 5);
    static final Pos GLOW2 = new Pos(CX * 16 + 2, -58, -1);         // 북쪽 이웃 남쪽 끝

    /** 계산 중 합치기: 붙잡힌 검사 통이 합친 region 으로 옮겨 가고, 끼운 뒤 풀려 계산된다. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mergeDuringCompute(boolean async) throws Exception {
        withWorld(async, (w, rz) -> {
            assertNotSame(rz.ownerOf(CX, 0), rz.ownerOf(40, 0));
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            w.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            assertEquals(1, w.relight().relight(new long[] {C}, null, done::complete));
            long until = System.currentTimeMillis() + 10_000;
            while (jobs.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(5);
            assertEquals(1, jobs.size());
            int glow = B.defaultState("glowstone");
            at(w, CX, r -> {
                r.level().setBlock(GLOW, glow, 2);
                r.level().setBlock(GLOW2, glow, 2);
                r.level().drainLight();
                assertTrue(r.level().lightEngine().heldCount() > 0, "검사가 붙잡혔다");
                assertEquals(0, r.level().getBlockLight(GLOW), "아직 계산 안 됨");
            });
            merge(w, rz);
            jobs.poll().run();
            assertEquals(1, done.get(30, TimeUnit.SECONDS));
            at(w, CX, r -> r.level().drainLight());
            int bad = call(w, CX, r -> {
                assertEquals(0, r.level().lightEngine().heldCount(), "통이 비었다");
                assertEquals(15, r.level().getBlockLight(GLOW));
                return badAround(r);
            });
            assertEquals(0, w.relight().holdCount());
            assertEquals(0, bad, "3×3 중 새로 켠 값과 다른 청크");
        });
    }

    /** 계산 중 나누기: C 쪽 region 에 붙잡힌 통이 남고(또는 옮겨지고) 끼운 뒤 풀린다. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void splitDuringCompute(boolean async) throws Exception {
        withWorld(async, (w, rz) -> {
            merge(w, rz);
            ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
            w.relight().useExecutor(jobs::add);
            CompletableFuture<Integer> done = new CompletableFuture<>();
            assertEquals(2, w.relight().relight(new long[] {C, Regionizer.chunkKey(40, 0)}, null, done::complete));
            long until = System.currentTimeMillis() + 10_000;
            while (jobs.size() < 2 && System.currentTimeMillis() < until) Thread.sleep(5);
            assertEquals(2, jobs.size());
            int glow = B.defaultState("glowstone");
            at(w, CX, r -> {
                r.level().setBlock(GLOW, glow, 2);
                r.level().setBlock(GLOW2, glow, 2);
                r.level().drainLight();
                assertTrue(r.level().lightEngine().heldCount() > 0, "검사가 붙잡혔다");
            });
            at(w, 40, r -> {
                r.level().setBlock(GLOW40, glow, 2);
                r.level().drainLight();
            });
            split(w, rz);
            jobs.poll().run();
            jobs.poll().run();
            assertEquals(2, done.get(30, TimeUnit.SECONDS));
            at(w, CX, r -> r.level().drainLight());
            int bad = call(w, CX, r -> {
                assertEquals(0, r.level().lightEngine().heldCount(), "통이 비었다");
                assertEquals(15, r.level().getBlockLight(GLOW));
                return badAround(r);
            });
            int bad40 = call(w, 40, r -> {
                r.level().drainLight();
                assertEquals(0, r.level().lightEngine().heldCount(), "통이 비었다(40)");
                assertEquals(15, r.level().getBlockLight(GLOW40), "40 옆 발광석");
                return badAround(r, 40);
            });
            assertEquals(0, bad40, "40 둘레");
            assertEquals(0, w.relight().holdCount());
            assertEquals(0, bad, "3×3 중 새로 켠 값과 다른 청크");
        });
    }

    /** 준비 뒤 계산을 못 넘기면(실행기 예외) 붙잡기·핀·얼림을 정리하고 끝으로 센다 — 남으면 그 둘레 빛이 영영 계산되지 않는다(검토 F1 회귀). */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void prepareFailureReleasesHolds(boolean async) throws Exception {
        withWorld(async, (w, rz) -> {
            w.relight().useExecutor(r -> { throw new java.util.concurrent.RejectedExecutionException("시험"); });
            CompletableFuture<Integer> done = new CompletableFuture<>();
            List<Long> got = java.util.Collections.synchronizedList(new ArrayList<>());
            assertEquals(1, w.relight().relight(new long[] {C}, got::add, done::complete));
            assertEquals(1, done.get(30, TimeUnit.SECONDS), "실패해도 done 은 온다");
            assertEquals(List.of(C), got);
            assertEquals(0, w.relight().holdCount(), "붙잡기가 남았다(누수)");
            assertFalse(w.relight().pinned(C), "핀이 남았다");
            int glow = B.defaultState("glowstone");
            at(w, CX, r -> {
                r.level().setBlock(GLOW, glow, 2);
                r.level().drainLight();
            });
            int light = call(w, CX, r -> {
                r.level().drainLight();
                assertEquals(0, r.level().lightEngine().heldCount(), "붙잡힌 검사");
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    for (ChunkSection cs : r.ownedChunk(CX + dx, CZ + dz).sections()) assertFalse(cs.lightFrozen(), "얼림이 남았다");
                }
                return r.level().getBlockLight(GLOW);
            });
            assertEquals(15, light, "옆 발광석 빛이 계산되지 않는다");
        });
    }

    /** 설치가 실패해도 붙잡기·핀을 풀고 끝으로 세며, 반쯤 끼웠을 수 있는 그 청크는 저장 빛을 믿지 않게 표시한다(검토 F1). */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void installFailureReleasesHolds(boolean async) throws Exception {
        withWorld(async, (w, rz) -> {
            w.relight().installHookForTest(() -> { throw new IllegalStateException("시험"); });
            CompletableFuture<Integer> done = new CompletableFuture<>();
            List<Long> got = java.util.Collections.synchronizedList(new ArrayList<>());
            assertEquals(1, w.relight().relight(new long[] {C}, got::add, done::complete));
            assertEquals(1, done.get(30, TimeUnit.SECONDS), "실패해도 done 은 온다");
            assertEquals(List.of(C), got);
            assertEquals(0, w.relight().holdCount(), "붙잡기가 남았다(누수)");
            assertFalse(w.relight().pinned(C), "핀이 남았다");
            assertTrue(w.lightStale().contains(CX, CZ), "끼우다 만 청크는 stale");
            w.relight().installHookForTest(null);
            int glow = B.defaultState("glowstone");
            int light = call(w, CX, r -> {
                r.level().setBlock(GLOW, glow, 2);
                r.level().drainLight();
                return r.level().getBlockLight(GLOW);
            });
            assertEquals(15, light, "옆 발광석 빛이 계산되지 않는다");
        });
    }

    /** 한 타일(8×8)이 두 region 에 걸침: 구역 2청크(shift 1)·반경 1 — 청크 0 과 6 은 같은 타일, 다른 region. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tileAcrossTwoRegions(boolean async) throws Exception {
        LightSettings.install(new LightSettings(async, 1));
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = Executors.newSingleThreadExecutor();
        var gen = Executors.newSingleThreadExecutor();
        try {
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 3, 1, 1, 2, 2, reg.directBits(), 7, io, gen);
            Regionizer rz = w.regionizer();
            int[] xs = {0, 6};
            for (int x : xs) rz.want(x, 0, null);
            for (int x : xs) at(w, x, r -> {
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) r.chunkAtBlocking(new ChunkPos(x + dx, dz));
                r.level().drainLight();
            });
            assertNotSame(rz.ownerOf(0, 0), rz.ownerOf(6, 0), "두 region");
            int glow = B.defaultState("glowstone");
            for (int x : xs) at(w, x, r -> {
                ChunkColumn col = r.ownedChunk(x, 0);
                int local = -55 - (-64);
                col.setSectionStateRaw(local >> 4, (local & 15) << 8 | 8 << 4 | 8, glow, true);
            });
            CompletableFuture<Integer> done = new CompletableFuture<>();
            List<Long> got = java.util.Collections.synchronizedList(new ArrayList<>());
            assertEquals(2, w.relight().relight(new long[] {Regionizer.chunkKey(0, 0), Regionizer.chunkKey(6, 0)}, got::add, done::complete));
            assertEquals(2, done.get(30, TimeUnit.SECONDS));
            assertEquals(2, got.size());
            for (int x : xs) {
                int light = call(w, x, r -> {
                    r.level().drainLight();
                    return r.level().getBlockLight(new Pos(x * 16 + 8, -55, 8));
                });
                assertEquals(15, light, "청크 " + x);
            }
            assertEquals(0, w.relight().holdCount());
            assertFalse(w.relight().pinned(Regionizer.chunkKey(0, 0)) || w.relight().pinned(Regionizer.chunkKey(6, 0)));
            long[] st = w.relight().stats();
            assertEquals(2, st[5], "타일이 region 마다 따로 준비됐다");
        } finally {
            io.shutdown();
            gen.shutdown();
            sched.close();
        }
    }
}
