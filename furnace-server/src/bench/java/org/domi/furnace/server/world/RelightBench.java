// T-197 빛 다시 계산 벤치(설계 6장 M1·M2). ./gradlew -PskipOracle=true :furnace-server:bench --tests '*RelightBench*'
// 실제 지형(시드 20261115) 34×34 청크를 한 region 에 올리고 1·4·8·16·32 변 정사각형을 relight — 끝까지 벽시계, 일꾼 계산 ms/컬럼(둘레 포함),
// region 몫(타일 준비 ms, 청크 설치 ms — 설치는 층 끼우기·원천·저장 표시·패킷 만들기·보내기), 따로 잰 전 섹션 빛 패킷 만들기 ms/청크.
// M3(relightUnderEdits): 빛 작업 끔·켬 × {쉼, 편집만, 편집 + 32×32 relight 를 쉬지 않고, relight 만} 각 8초 — region 틱 평균·최대 ms(작업 큐 포함).
// 편집량은 -Dfurnace.bench.edits(기본 틱마다 20).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.config.LightSettings;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelightBench {

    private static final int C0 = 16;   // relight 정사각형의 아래 모서리(청크) — 구역 0(shift 6 = 64청크) 안

    @AfterEach
    void restore() { LightSettings.install(LightSettings.DEFAULT); }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.MINUTES)
    void relightBench() throws Exception {
        LightSettings.install(new LightSettings(false, 1));
        RegistrySet regs = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemContext.install(new TagRegistries(regs));
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newFixedThreadPool(2);
        try {
            World w = new World(NoiseWorldSource.overworld(20261115L, reg, regs), new ChunkWorkerPool(2), sched, 2, 6, 1, 2, 2,
                    reg.directBits(), 7, io, gen);
            w.regionizer().want(C0, C0, null);
            long t0 = System.nanoTime();
            at(w, r -> {
                for (int x = C0 - 1; x <= C0 + 32; x++) for (int z = C0 - 1; z <= C0 + 32; z++) r.chunkAtBlocking(new ChunkPos(x, z));
                r.level().drainLight();
            });
            System.out.printf("T197 BENCH 올리기 34×34 %.1f s, 계산 일꾼 %d%n", (System.nanoTime() - t0) / 1e9, Relight.poolThreads());
            for (int round = 0; round < 3; round++) {
                for (int side : new int[] {1, 4, 8, 16, 32}) {
                    long[] keys = new long[side * side];
                    int i = 0;
                    for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) keys[i++] = Regionizer.chunkKey(C0 + x, C0 + z);
                    long[] before = w.relight().stats();
                    CompletableFuture<Integer> done = new CompletableFuture<>();
                    long s0 = System.nanoTime();
                    w.relight().relight(keys, null, done::complete);
                    assertEquals(keys.length, done.get(5, TimeUnit.MINUTES));
                    long wall = System.nanoTime() - s0;
                    long[] after = w.relight().stats();
                    long prep = after[0] - before[0], comp = after[1] - before[1], inst = after[2] - before[2];
                    long cols = after[3] - before[3], installs = after[4] - before[4], tiles = after[5] - before[5];
                    System.out.printf("T197 BENCH round %d side %2d (%4d청크): 벽시계 %7.1f ms | 일꾼 %6.2f ms/컬럼(컬럼 %d, %.2f배) | "
                            + "준비 %.3f ms/타일(%d) | 설치 %.3f ms/청크%n",
                            round, side, keys.length, wall / 1e6, comp / 1e6 / Math.max(1, cols), cols, cols / (double) keys.length,
                            prep / 1e6 / Math.max(1, tiles), tiles, inst / 1e6 / Math.max(1, installs));
                }
                // 전 섹션 빛 패킷 만들기만(설치 안에 들어 있는 몫)
                double[] pkt = new double[1];
                Object[] sink = new Object[1];
                at(w, r -> {
                    RegionLevel lv = r.level();
                    int lightBase = (lv.minBuildHeight() >> 4) - 1 - org.domi.furnace.server.light.ChunkLight.MIN_SECTION;
                    int count = ((lv.maxBuildHeight() - lv.minBuildHeight()) >> 4) + 2;
                    long all = (1L << count) - 1;
                    long p0 = System.nanoTime();
                    for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                        sink[0] = LightUpdate.of(C0 + x, C0 + z, r.ownedChunk(C0 + x, C0 + z).light(), all, all, lightBase, count);
                    }
                    pkt[0] = (System.nanoTime() - p0) / 1e6 / 1024;
                });
                double[] src = new double[1];
                at(w, r -> {
                    long p0 = System.nanoTime();
                    for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) r.level().lightEngine().recomputeSources(C0 + x, C0 + z);
                    src[0] = (System.nanoTime() - p0) / 1e6 / 1024;
                });
                System.out.printf("T197 BENCH round %d 빛 패킷 만들기 %.3f ms/청크, 하늘빛 원천 다시 세기 %.3f ms/청크%n", round, pkt[0], src[0]);
            }
        } finally {
            io.shutdown();
            gen.shutdown();
            sched.close();
        }
    }

    /** M3 편집량: 틱마다 발광석 켜고 끄기(32×32 청크, y 60~99). 100 이면 이 컨테이너에서 편집만으로 틱이 50 ms 를 넘는다. */
    private static final int EDITS_PER_TICK = Integer.getInteger("furnace.bench.edits", 20);

    /** M3: 편집과 relight 가 겹칠 때 region 틱 시간. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.MINUTES)
    void relightUnderEdits(boolean async) throws Exception {
        LightSettings.install(new LightSettings(async, 1));
        RegistrySet regs = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemContext.install(new TagRegistries(regs));
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newFixedThreadPool(2);
        var ticker = Executors.newSingleThreadScheduledExecutor();
        try {
            World w = new World(NoiseWorldSource.overworld(20261115L, reg, regs), new ChunkWorkerPool(2), sched, 2, 6, 1, 2, 2,
                    reg.directBits(), 7, io, gen);
            w.regionizer().want(C0, C0, null);
            at(w, r -> {
                for (int x = C0 - 1; x <= C0 + 32; x++) for (int z = C0 - 1; z <= C0 + 32; z++) r.chunkAtBlocking(new ChunkPos(x, z));
                r.level().drainLight();
            });
            w.addPluginTicket(C0 - 1, C0 - 1, C0 + 32, C0 + 32, false).get(5, TimeUnit.MINUTES);   // 틱을 켜도 내려가지 않게
            w.regionizer().gameTicks(true);   // 틱 끝 빛 계산·빛 작업 넘기기가 실제로 돈다
            int glow = org.domi.furnace.server.block.B.defaultState("glowstone"), air = org.domi.furnace.server.block.B.defaultState("air");
            java.util.SplittableRandom rnd = new java.util.SplittableRandom(197);
            java.util.concurrent.atomic.AtomicBoolean editing = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicLong edits = new java.util.concurrent.atomic.AtomicLong();
            ticker.scheduleAtFixedRate(() -> {
                if (!editing.get()) return;
                long[] ps = new long[EDITS_PER_TICK];
                synchronized (rnd) {
                    for (int i = 0; i < ps.length; i++) ps[i] = org.domi.furnace.server.light.LightEngine.pack(C0 * 16 + rnd.nextInt(512), 60 + rnd.nextInt(40),
                            C0 * 16 + rnd.nextInt(512));
                }
                w.runAtChunk(new ChunkPos(C0, C0), r -> {
                    for (long p : ps) {
                        var pos = new org.domi.furnace.server.block.Pos(org.domi.furnace.server.light.LightEngine.px(p),
                                org.domi.furnace.server.light.LightEngine.py(p), org.domi.furnace.server.light.LightEngine.pz(p));
                        r.level().setBlock(pos, r.level().getBlockState(pos) == glow ? air : glow, 2);
                    }
                    edits.addAndGet(ps.length);
                });
            }, 0, 50, TimeUnit.MILLISECONDS);
            long[] keys = new long[32 * 32];
            int i = 0;
            for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) keys[i++] = Regionizer.chunkKey(C0 + x, C0 + z);
            for (int round = 0; round < 3; round++) {
                for (String phase : new String[] {"쉼", "일꾼 흉내", "편집만", "편집+relight", "relight만"}) {
                    editing.set(phase.contains("편집"));
                    boolean relighting = phase.contains("relight");
                    // 일꾼 흉내: relight 없이 다른 스레드 하나가 빛 배열 크기 메모리를 쉬지 않고 쓴다 — 틱이 CPU·메모리 다툼만으로 얼마나 늘어나는지
                    java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean(phase.equals("일꾼 흉내"));
                    Thread hog = new Thread(() -> {
                        long sink = 0;
                        while (busy.get()) {
                            byte[][] a = new byte[48][2048];
                            for (byte[] b : a) for (int j = 0; j < b.length; j++) b[j] = (byte) (j * 31 + sink);
                            sink += a[7][100];
                        }
                        if (sink == 42) System.out.print("");
                    });
                    if (busy.get()) hog.start();
                    long[] before = w.relight().stats();
                    long ring0 = w.regionizer().tickRing().count();
                    long[] gc0 = gc();
                    long e0 = edits.get(), t0 = System.nanoTime(), ticks0 = w.regionizer().tickWindows().totalTicks(),
                            nanos0 = w.regionizer().tickWindows().totalNanos();
                    int requests = 0;
                    while (System.nanoTime() - t0 < 8_000_000_000L) {
                        if (relighting) {
                            CompletableFuture<Integer> done = new CompletableFuture<>();
                            w.relight().relight(keys, null, done::complete);
                            assertEquals(keys.length, done.get(5, TimeUnit.MINUTES));
                            requests++;
                        } else {
                            Thread.sleep(100);
                        }
                    }
                    busy.set(false);
                    if (hog.isAlive()) hog.join();
                    long span = System.nanoTime() - t0;
                    var win = w.regionizer().tickWindows().window(System.nanoTime(), span - 500_000_000L);
                    long ticks = w.regionizer().tickWindows().totalTicks() - ticks0, nanos = w.regionizer().tickWindows().totalNanos() - nanos0;
                    long[] after = w.relight().stats();
                    long tiles = after[5] - before[5], installs = after[4] - before[4];
                    // 이 구간 틱만(고리 512칸 — 구간은 8초 = 약 160~250틱)
                    long ring1 = w.regionizer().tickRing().count();
                    long[] byNo = w.regionizer().tickRing().recentByTickNumber(512);
                    long[] seg = new long[(int) Math.min(512, ring1 - ring0)];
                    for (int j = 0; j < seg.length; j++) seg[j] = byNo[(int) ((ring1 - seg.length + j) % 512)];
                    java.util.Arrays.sort(seg);
                    long[] gc1 = gc();
                    double p50 = seg.length == 0 ? 0 : seg[seg.length / 2] / 1e6, p95 = seg.length == 0 ? 0 : seg[(int) (seg.length * 0.95)] / 1e6;
                    System.out.printf("T197 M3 async=%s round %d %-12s: 틱 %d개 평균 %.2f ms p50 %.2f p95 %.2f 최대(창) %.2f ms | 편집 %d | relight %d번 | 준비 %.3f ms/타일(%d) 설치 %.3f ms/청크 | relight region 몫 %.3f ms/틱 | GC %d번 %d ms%n",
                            async, round, phase, ticks, nanos / 1e6 / Math.max(1, ticks), p50, p95, win.maxNanos() / 1e6, edits.get() - e0, requests,
                            (after[0] - before[0]) / 1e6 / Math.max(1, tiles), tiles, (after[2] - before[2]) / 1e6 / Math.max(1, installs),
                            (after[0] - before[0] + after[2] - before[2]) / 1e6 / Math.max(1, ticks), gc1[0] - gc0[0], gc1[1] - gc0[1]);
                }
            }
            editing.set(false);
        } finally {
            ticker.shutdownNow();
            io.shutdown();
            gen.shutdown();
            sched.close();
        }
    }

    /** {GC 횟수, GC 시간 ms} 누계. */
    private static long[] gc() {
        long n = 0, ms = 0;
        for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        return new long[] {n, ms};
    }

    private static void at(World w, java.util.function.Consumer<Region> task) throws Exception {
        var f = new CompletableFuture<Void>();
        w.runAtChunk(new ChunkPos(C0, C0), r -> {
            try {
                task.accept(r);
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        f.get(20, TimeUnit.MINUTES);
    }
}
