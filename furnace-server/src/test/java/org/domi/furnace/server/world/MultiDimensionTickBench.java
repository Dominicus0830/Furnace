// 가짜 차원 N개(평지 월드, 월드마다 Regionizer·작업 스레드 풀)를 동시에 돌릴 때 region 틱 간격의 흔들림을 잰다(기본 꺼짐: -Dfurnace.bench=true).
// 각 region 에 매 틱 다시 거는 지연 작업을 두고, 그 작업이 도는 시각의 간격 - 틱 길이 를 "늦음"으로 본다. 부하는 매 틱 바쁜 대기(ms).
// 공유 스케줄러(B안) 검토용: docs/feature-parity/shared-scheduler-review-2026-10-27.md
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MultiDimensionTickBench {

    private static final long TICK = 50_000_000L;

    @Test
    void dimensionsTickJitter() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        String[] ns = System.getProperty("furnace.bench.dims", "3,6,12").split(",");
        String[] loads = System.getProperty("furnace.bench.loadMs", "0,5").split(",");
        int regionsPerDim = Integer.getInteger("furnace.bench.regions", 2);
        int ticks = Integer.getInteger("furnace.bench.ticks", 100);
        int threads = Integer.getInteger("furnace.bench.threads", Runtime.getRuntime().availableProcessors());
        for (String n : ns) {
            for (String l : loads) run(Integer.parseInt(n.trim()), regionsPerDim, Double.parseDouble(l.trim()), ticks, threads);
        }
    }

    private void run(int dims, int regionsPerDim, double loadMs, int ticks, int threads) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(TICK, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        List<ExecutorService> pools = new ArrayList<>();
        List<World> worlds = new ArrayList<>();
        long beforeThreads = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("furnace-region")).count();
        for (int d = 0; d < dims; d++) {
            ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
            pools.add(io);
            pools.add(gen);
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, threads, 4, 1, 2, 2,
                    reg.directBits(), 7, io, gen);
            w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
            worlds.add(w);
        }
        long regionThreads = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("furnace-region")).count() - beforeThreads;
        int total = dims * regionsPerDim;
        long[][] starts = new long[total][ticks + 1];
        int[] count = new int[total];
        long spin = (long) (loadMs * 1_000_000L);
        List<CompletableFuture<Void>> done = new ArrayList<>();
        long cpu0 = processCpuNanos(), wall0 = System.nanoTime();
        int idx = 0;
        for (World w : worlds) {
            for (int k = 0; k < regionsPerDim; k++) {
                final int slot = idx++;
                ChunkPos cp = new ChunkPos(k * 4096, 0);   // 서로 멀리 — region 이 나뉜다
                CompletableFuture<Void> f = new CompletableFuture<>();
                done.add(f);
                long key = Regionizer.chunkKey(cp.x(), cp.z());
                w.runAtChunk(cp, r -> {
                    r.chunkAtBlocking(cp);
                    r.schedule(key, new java.util.function.Consumer<Region>() {
                        @Override public void accept(Region r2) {
                            long now = System.nanoTime();
                            int c = count[slot];
                            starts[slot][c] = now;
                            count[slot] = c + 1;
                            if (spin > 0) { long end = now + spin; while (System.nanoTime() < end) Thread.onSpinWait(); }
                            if (c + 1 > ticks) f.complete(null);
                            else r2.schedule(key, this, 1);
                        }
                    }, 1);
                });
            }
        }
        CompletableFuture.allOf(done.toArray(new CompletableFuture[0])).get(ticks * 200L + 60_000L, TimeUnit.MILLISECONDS);
        long cpu = processCpuNanos() - cpu0, wall = System.nanoTime() - wall0;
        // 첫 5 간격(예열) 제외, 늦음 = 간격 - 틱
        List<Long> late = new ArrayList<>();
        for (int s = 0; s < total; s++) for (int i = 6; i <= ticks; i++) late.add(starts[s][i] - starts[s][i - 1] - TICK);
        long[] a = late.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf("MDBENCH dims=%d regions/dim=%d regionThreads=%d (per-dim %d) cores=%d loadMs=%.1f samples=%d"
                        + " | 간격-50ms p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms min=%.3fms | cpu=%.0fms wall=%.0fms%n",
                dims, regionsPerDim, regionThreads, threads, Runtime.getRuntime().availableProcessors(), loadMs, a.length,
                a[a.length / 2] / 1e6, a[a.length * 95 / 100] / 1e6, a[a.length * 99 / 100] / 1e6, a[a.length - 1] / 1e6, a[0] / 1e6,
                cpu / 1e6, wall / 1e6);
        sched.close();
        for (World w : worlds) w.regionizer().close();
        for (ExecutorService e : pools) e.shutdownNow();
    }

    // ===== 과부하 시나리오 (2026-10-27): -Dfurnace.bench=true -Dfurnace.bench.overload=true =====
    // 차원 하나 = (region 수, region 틱당 부하 ms, 측정 대상 여부). 지연 틱 = 간격-50ms 가 LATE_MS 를 넘는 틱.
    private static final double LATE_MS = 10.0;

    private record Dim(int regions, double loadMs, boolean measured) {}

    @Test
    void overloadScenarios() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench") && Boolean.getBoolean("furnace.bench.overload"));
        int cores = Runtime.getRuntime().availableProcessors();
        int ticks = Integer.getInteger("furnace.bench.ticks", 60);
        System.out.printf("OVBENCH cores=%d ticks=%d lateThreshold=%.0fms%n", cores, ticks, LATE_MS);
        // (1) 합계 부하 100/150/200% : 3차원 x 8 region, 부하는 균등
        for (int pct : new int[] {100, 150, 200}) {
            double per = cores * 50.0 * pct / 100.0 / 24;
            List<Dim> ds = List.of(new Dim(8, per, true), new Dim(8, per, true), new Dim(8, per, true));
            runOv("load" + pct + "%", ds, cores, ticks, false);
            runOv("load" + pct + "%", ds, cores, ticks, true);
        }
        // (2) 비대칭 : 차원 0 무거움(틱 40/60/80ms, region 많음), 나머지 2차원 가벼움(region 2, 1ms)
        for (int heavy : new int[] {40, 60, 80}) {
            for (int hr : new int[] {cores, cores * 3}) {
                List<Dim> ds = List.of(new Dim(hr, heavy, false), new Dim(2, 1.0, true), new Dim(2, 1.0, true));
                String nm = "asym heavy=" + heavy + "ms x" + hr;
                runOv(nm, ds, cores, ticks, false);
                runOv(nm, ds, cores, ticks, true);
            }
        }
        // (3) 차원 수 N, region-threads 변화 : 총 부하 약 150%, 차원 0 만 2배 무거움
        for (int n : new int[] {3, 6, 12}) {
            for (int th : new int[] {Math.max(1, cores / 2), cores, cores * 2}) {
                int per = 4;
                double base = cores * 50.0 * 1.5 / (per * (n + 1));
                List<Dim> ds = new ArrayList<>();
                ds.add(new Dim(per, base * 2, true));
                for (int d = 1; d < n; d++) ds.add(new Dim(per, base, true));
                String nm = "N=" + n + " th=" + th;
                runOv(nm, ds, th, ticks, false);
                runOv(nm, ds, th, ticks, true);
            }
        }
    }

    /** shared=true : 모든 region 을 한 월드에 넣는다(공유 큐 근사: 풀 하나, 데드라인 큐 하나). */
    private void runOv(String name, List<Dim> dims, int threads, int ticks, boolean shared) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(TICK, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        List<ExecutorService> pools = new ArrayList<>();
        List<World> worlds = new ArrayList<>();
        int nw = shared ? 1 : dims.size();
        for (int d = 0; d < nw; d++) {
            ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
            pools.add(io);
            pools.add(gen);
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, threads, 4, 1, 2, 2,
                    reg.directBits(), 7, io, gen);
            w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
            worlds.add(w);
        }
        int total = dims.stream().mapToInt(Dim::regions).sum();
        long[][] starts = new long[total][ticks + 1];
        int[] count = new int[total];
        int[] dimOf = new int[total];
        AtomicBoolean stop = new AtomicBoolean();
        List<CompletableFuture<Void>> done = new ArrayList<>();
        int idx = 0;
        for (int d = 0; d < dims.size(); d++) {
            Dim dim = dims.get(d);
            World w = worlds.get(shared ? 0 : d);
            long spin = (long) (dim.loadMs() * 1_000_000L);
            for (int k = 0; k < dim.regions(); k++) {
                final int slot = idx++;
                dimOf[slot] = d;
                ChunkPos cp = new ChunkPos((shared ? slot : k) * 4096, 0);
                CompletableFuture<Void> f = new CompletableFuture<>();
                if (dim.measured()) done.add(f);
                long key = Regionizer.chunkKey(cp.x(), cp.z());
                w.runAtChunk(cp, r -> {
                    r.chunkAt(cp);
                    r.schedule(key, new java.util.function.Consumer<Region>() {
                        @Override public void accept(Region r2) {
                            long now = System.nanoTime();
                            int c = count[slot];
                            if (c <= ticks) starts[slot][c] = now;
                            count[slot] = c + 1;
                            if (spin > 0) { long end = now + spin; while (System.nanoTime() < end) Thread.onSpinWait(); }
                            if (c + 1 > ticks) f.complete(null);
                            if (!stop.get()) r2.schedule(key, this, 1);
                        }
                    }, 1);
                });
            }
        }
        CompletableFuture.allOf(done.toArray(new CompletableFuture[0])).get(ticks * 1000L + 60_000L, TimeUnit.MILLISECONDS);
        stop.set(true);
        // 차원별 통계 : 첫 5 간격 제외
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < dims.size(); d++) {
            if (!dims.get(d).measured()) continue;
            List<Long> late = new ArrayList<>();
            int maxRun = 0, lateTicks = 0;
            for (int s = 0; s < total; s++) {
                if (dimOf[s] != d) continue;
                int run = 0;
                for (int i = 6; i <= ticks; i++) {
                    long l = starts[s][i] - starts[s][i - 1] - TICK;
                    late.add(l);
                    if (l / 1e6 > LATE_MS) { run++; lateTicks++; maxRun = Math.max(maxRun, run); } else run = 0;
                }
            }
            long[] a = late.stream().mapToLong(Long::longValue).sorted().toArray();
            sb.append(String.format(" | d%d p50=%.1f p95=%.1f max=%.1f late=%.0f%% maxRun=%d", d,
                    a[a.length / 2] / 1e6, a[a.length * 95 / 100] / 1e6, a[a.length - 1] / 1e6, 100.0 * lateTicks / a.length, maxRun));
        }
        System.out.printf("OVBENCH %-24s %-7s threads=%d%s%n", name, shared ? "shared" : "perDim", threads, sb);
        sched.close();
        for (World w : worlds) w.regionizer().close();
        for (ExecutorService e : pools) e.shutdownNow();
    }

    /** 프로세스 CPU 시간(ns). 공유 머신이라 다른 프로세스는 빠지지만 이 JVM 의 다른 스레드(GC·JIT)는 들어간다. */
    static long processCpuNanos() {
        var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return os instanceof com.sun.management.OperatingSystemMXBean s ? s.getProcessCpuTime() : 0L;
    }
}
