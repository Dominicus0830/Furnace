// 노이즈 최적화 전후 동일성·시간 측정 도구(-Dfurnace.bench=true 일 때만 JUnit 으로 돈다, main 으로도 실행 가능).
//  hash <출력 파일>: 시드·차원·월드 타입·위치 여러 곳의 원시 청크(지형+표면+카버+바이옴) 해시와, 라우터 밀도 함수 15종을
//                    무작위 좌표에서 직접 평가한 double 비트 해시를 한 줄씩 적는다. 옛 커밋과 새 커밋의 출력 파일을 diff 한다.
//  time: 한 스레드에서 단계별 청크당 CPU 시간(NoiseChunk 생성·지형 채우기·바이옴·원시 청크 전체)을 잰다.
package org.domi.furnace.server.world.gen;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class NoiseIdentityBench {

    static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    record Config(String name, NoiseWorldSource.Dimension dim, String noiseSettings, long seed) {
        NoiseWorldSource source() {
            return switch (dim) {
                case OVERWORLD -> NoiseWorldSource.overworld(seed, BLOCKS, REGISTRIES, noiseSettings);
                case NETHER -> NoiseWorldSource.nether(seed, BLOCKS, REGISTRIES);
                case END -> NoiseWorldSource.end(seed, BLOCKS, REGISTRIES);
            };
        }
    }

    static List<Config> configs() {
        List<Config> c = new ArrayList<>();
        long[] owSeeds = {0L, 12345L, -1L, 42L, 3141592653589793L, 8675309L, Long.MIN_VALUE + 7, 20261111L,
                -4172144997902289642L, 1L};
        for (long s : owSeeds) c.add(new Config("overworld", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:overworld", s));
        for (long s : new long[]{12345L, 7L, -99L}) {
            c.add(new Config("large_biomes", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:large_biomes", s));
            c.add(new Config("amplified", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:amplified", s));
        }
        for (long s : new long[]{0L, 12345L, 42L, -7L}) {
            c.add(new Config("nether", NoiseWorldSource.Dimension.NETHER, "minecraft:nether", s));
            c.add(new Config("end", NoiseWorldSource.Dimension.END, "minecraft:end", s));
        }
        return c;
    }

    /** 위치: 원점 근처 2×2 묶음(이웃 캐시 공유), 흩어진 곳, 음수, 먼 곳(±16만 블록, ±160만 블록). 시드마다 조금씩 옮긴다. */
    static List<int[]> positions(Config cfg) {
        long h = cfg.seed * 0x9E3779B97F4A7C15L + cfg.name.hashCode();
        java.util.Random r = new java.util.Random(h);
        List<int[]> p = new ArrayList<>();
        int bx = r.nextInt(64) - 32, bz = r.nextInt(64) - 32;
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) p.add(new int[]{bx + dx, bz + dz});
        for (int i = 0; i < 10; i++) p.add(new int[]{r.nextInt(600) - 300, r.nextInt(600) - 300});
        for (int i = 0; i < 3; i++) p.add(new int[]{r.nextInt(20000) - 10000, r.nextInt(20000) - 10000});
        p.add(new int[]{100000 + r.nextInt(10), -100000 - r.nextInt(10)});
        p.add(new int[]{-1, -1});
        p.add(new int[]{0, 0});
        if (cfg.dim == NoiseWorldSource.Dimension.END) {
            // 바깥 섬(원점에서 1000블록 너머)
            for (int i = 0; i < 4; i++) p.add(new int[]{80 + r.nextInt(200), -80 - r.nextInt(200)});
        }
        return p;
    }

    static long fnv(long h, long v) { return (h ^ v) * 0x100000001B3L; }

    static long hashColumn(NoiseWorldSource.Generated g) {
        ChunkColumn col = g.col();
        long h = 0xCBF29CE484222325L;
        for (int y = col.minY(); y <= col.maxY(); y++)
            for (int bz = 0; bz < 16; bz++) for (int bx = 0; bx < 16; bx++)
                h = fnv(h, col.getBlock(bx, y, bz));
        for (int b : g.biomeIds()) h = fnv(h, b);
        // 유체 후처리 표시·생성 유체 틱(대수층 경계 — 순서까지)
        for (int[] m : col.postProcessMarks()) for (int v : m) h = fnv(h, v);
        h = fnv(h, 0x7FFF);
        for (int[] t : col.generatedFluidTicks()) for (int v : t) h = fnv(h, v);
        return h;
    }

    /** 라우터 밀도 함수를 NoiseChunk 없이 바로 평가 — 노이즈 원시 함수·DF 노드의 double 비트를 그대로 해시. */
    static long hashRouter(NoiseRouter r, long seed) {
        DensityFunction[] fns = {r.finalDensity(), r.initialDensityWithoutJaggedness(), r.temperature(), r.humidity(),
                r.continents(), r.erosion(), r.depth(), r.ridges(), r.barrierNoise(), r.fluidLevelFloodednessNoise(),
                r.fluidLevelSpreadNoise(), r.lavaNoise(), r.veinToggle(), r.veinRidged(), r.veinGap()};
        java.util.Random rnd = new java.util.Random(seed ^ 0x5DEECE66DL);
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < 400; i++) {
            int range = i < 300 ? 20000 : 29_000_000;
            int x = rnd.nextInt(2 * range) - range, z = rnd.nextInt(2 * range) - range, y = rnd.nextInt(448) - 64;
            var ctx = new DensityFunction.SinglePointContext(x, y, z);
            for (DensityFunction f : fns) h = fnv(h, Double.doubleToRawLongBits(f.compute(ctx)));
        }
        return h;
    }

    public static void hash(Path out) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            long total = 0xCBF29CE484222325L;
            int chunks = 0, ci = 0;
            for (Config cfg : configs()) {
                NoiseWorldSource src = cfg.source();
                long rh = hashRouter(src.router(), cfg.seed);
                w.printf("router %s %d %016x%n", cfg.name, cfg.seed, rh);
                total = fnv(total, rh);
                for (int[] p : positions(cfg)) {
                    NoiseWorldSource.Generated g = src.generateWorldChunk(p[0], p[1]);
                    src.applyCarvers(g, p[0], p[1]);
                    long ch = hashColumn(g);
                    w.printf("chunk %s %d %d %d %016x%n", cfg.name, cfg.seed, p[0], p[1], ch);
                    total = fnv(total, ch);
                    chunks++;
                }
                if (ci++ % 3 == 0) {
                    // 이어진 5×5 를 3 스레드가 동시에 — 이웃 꼭짓점 공유·동시 접근 경로를 거친 결과도 같아야 한다
                    int bx = 777 + (int) (cfg.seed & 63), bz = -333;
                    java.util.concurrent.ConcurrentSkipListMap<Long, Long> res = new java.util.concurrent.ConcurrentSkipListMap<>();
                    java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
                    List<Thread> ts = new ArrayList<>();
                    for (int t = 0; t < 3; t++) {
                        Thread th = new Thread(() -> {
                            for (int i; (i = next.getAndIncrement()) < 25; ) {
                                int cx = bx + i % 5, cz = bz + i / 5;
                                NoiseWorldSource.Generated g = src.generateWorldChunk(cx, cz);
                                src.applyCarvers(g, cx, cz);
                                res.put((long) i, hashColumn(g));
                            }
                        });
                        th.start();
                        ts.add(th);
                    }
                    for (Thread th : ts) th.join();
                    for (var e : res.entrySet()) {
                        int i = e.getKey().intValue();
                        w.printf("area %s %d %d %d %016x%n", cfg.name, cfg.seed, bx + i % 5, bz + i / 5, e.getValue());
                        total = fnv(total, e.getValue());
                        chunks++;
                    }
                }
                w.flush();
            }
            w.printf("TOTAL %016x chunks=%d%n", total, chunks);
            System.out.printf("NOISE_IDENTITY TOTAL %016x chunks=%d%n", total, chunks);
        }
    }

    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void time(String which) throws Exception {
        String[] dims = which == null ? new String[]{"overworld", "nether", "end"} : which.split(",");
        for (String d : dims) {
            NoiseWorldSource src = switch (d) {
                case "nether" -> NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES);
                case "end" -> NoiseWorldSource.end(12345L, BLOCKS, REGISTRIES);
                case "amplified" -> NoiseWorldSource.overworld(12345L, BLOCKS, REGISTRIES, "minecraft:amplified");
                default -> NoiseWorldSource.overworld(12345L, BLOCKS, REGISTRIES);
            };
            NoiseRouter router = src.router();
            NoiseSettings settings = src.noiseSettings();
            // 예열
            for (int i = 0; i < 60; i++) { var g = src.generateWorldChunk(5000 + i, -5000); src.applyCarvers(g, 5000 + i, -5000); }
            int rounds = Integer.getInteger("furnace.bench.rounds", 5), n = Integer.getInteger("furnace.bench.n", 48);
            double[][] res = new double[rounds][5];
            for (int round = 0; round < rounds; round++) {
                long tCtor = 0, tFill = 0, tBiome = 0, tFull = 0, tSlice = 0;
                for (int i = 0; i < n; i++) {
                    int cx = 20000 + round * 300 + (i % 8) * 3, cz = -20000 + (i / 8) * 3;
                    long c0 = MX.getCurrentThreadCpuTime();
                    NoiseChunk nc = new NoiseChunk(cx, cz, router, settings, BLOCKS);
                    long c1 = MX.getCurrentThreadCpuTime();
                    Aquifer aq = settings.aquifersEnabled()
                        ? new NoiseBasedAquifer(nc, cx, cz, router, settings, router.aquiferRandom(), 1, 2, 3)
                        : new DisabledAquifer(settings.seaLevel(), settings.lavaFloorY(), 2, 3);
                    int[] ids = nc.generateBlockIds(1, aq);
                    tSlice += nc.sliceFillNanos;
                    long c2 = MX.getCurrentThreadCpuTime();
                    src.biomeSampler().sampleChunk(cx, cz, nc);
                    long c3 = MX.getCurrentThreadCpuTime();
                    // 원시 청크 전체(구조물 beard·표면·카버 포함) — 다른 좌표에서(위 단계의 캐시를 안 쓰게)
                    var g = src.generateWorldChunk(cx + 100000, cz);
                    src.applyCarvers(g, cx + 100000, cz);
                    long c4 = MX.getCurrentThreadCpuTime();
                    tCtor += c1 - c0; tFill += c2 - c1; tBiome += c3 - c2; tFull += c4 - c3;
                    if (ids.length == 0) throw new AssertionError();
                }
                res[round] = new double[]{tCtor / 1e6 / n, tFill / 1e6 / n, tBiome / 1e6 / n, tFull / 1e6 / n, tSlice / 1e6 / n};
                System.out.printf("NOISE_TIME %s round=%d ctor=%.2f fill=%.2f (corners=%.2f) biome=%.2f pristine=%.2f ms/chunk%n",
                        d, round, res[round][0], res[round][1], res[round][4], res[round][2], res[round][3]);
            }
            double[] med = new double[5];
            for (int k = 0; k < 5; k++) {
                double[] col = new double[rounds];
                for (int r = 0; r < rounds; r++) col[r] = res[r][k];
                Arrays.sort(col);
                med[k] = col[rounds / 2];
            }
            System.out.printf("NOISE_TIME %s MEDIAN ctor=%.2f fill=%.2f (corners=%.2f) biome=%.2f pristine=%.2f ms/chunk%n", d, med[0], med[1], med[4], med[2], med[3]);
        }
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "hash" -> hash(Path.of(args[1]));
            case "time" -> time(args.length > 1 ? args[1] : null);
            case "pristine" -> {
                // 프로파일용: 여러 스레드로 원시 청크만 계속 만든다
                NoiseWorldSource src = NoiseWorldSource.overworld(12345L, BLOCKS, REGISTRIES);
                int threads = Integer.parseInt(args.length > 1 ? args[1] : "4"), per = Integer.parseInt(args.length > 2 ? args[2] : "200");
                // 예열: 요새 고리 계산(월드당 한 번)·JIT 를 측정에서 뺀다
                for (int i = 0; i < 40; i++) { var g = src.generateWorldChunk(-3000 + i, 700); src.applyCarvers(g, -3000 + i, 700); }
                String jfr = System.getProperty("furnace.bench.jfr");
                jdk.jfr.Recording rec = null;
                if (jfr != null) {
                    rec = new jdk.jfr.Recording();
                    rec.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1));
                    rec.start();
                }
                List<Thread> ts = new ArrayList<>();
                long t0 = System.nanoTime();
                for (int t = 0; t < threads; t++) {
                    int tt = t;
                    Thread th = new Thread(() -> {
                        for (int i = 0; i < per; i++) { var g = src.generateWorldChunk(3000 + tt * 100 + i % 20, 700 + i / 20); src.applyCarvers(g, 3000 + tt * 100 + i % 20, 700 + i / 20); }
                    });
                    th.start();
                    ts.add(th);
                }
                for (Thread th : ts) th.join();
                System.out.printf("PRISTINE %d chunks %.1f s%n", threads * per, (System.nanoTime() - t0) / 1e9);
                if (rec != null) { rec.stop(); rec.dump(Path.of(jfr)); }
            }
            case "area" -> area(args.length > 1 ? args[1] : "overworld", Integer.parseInt(args.length > 2 ? args[2] : "3"),
                    Integer.parseInt(args.length > 3 ? args[3] : "24"));
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /**
     * 서버처럼 이어진 땅을 만든다: side×side 청크를 가운데서 바깥으로(나선 비슷하게 거리순) threads 개 스레드가 나눠 원시 청크(지형·표면·카버)로.
     * 벽시계·전체 CPU·꼭짓점 캐시 적중을 적는다. 라운드마다 새 곳(캐시가 비어 있는 땅).
     */
    public static void area(String dim, int threads, int side) throws Exception {
        NoiseWorldSource src = switch (dim) {
            case "nether" -> NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES);
            case "end" -> NoiseWorldSource.end(12345L, BLOCKS, REGISTRIES);
            default -> NoiseWorldSource.overworld(12345L, BLOCKS, REGISTRIES);
        };
        for (int i = 0; i < 60; i++) { var g = src.generateWorldChunk(-9000 + i, 700); src.applyCarvers(g, -9000 + i, 700); }
        int rounds = Integer.getInteger("furnace.bench.rounds", 4);
        for (int round = 0; round < rounds; round++) {
            int ox = 40000 + round * 500, oz = -40000;
            List<int[]> order = new ArrayList<>();
            for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) order.add(new int[]{x - side / 2, z - side / 2});
            order.sort(java.util.Comparator.comparingInt(p -> Math.max(Math.abs(p[0]), Math.abs(p[1])) * 1000 + Math.abs(p[0]) + Math.abs(p[1])));
            java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicLong cpu = new java.util.concurrent.atomic.AtomicLong();
            SliceColumnCache cache = src.router().sliceColumns();
            long h0 = cache.hits.sum(), m0 = cache.misses.sum();
            List<Thread> ts = new ArrayList<>();
            long t0 = System.nanoTime();
            for (int t = 0; t < threads; t++) {
                Thread th = new Thread(() -> {
                    long c0 = MX.getCurrentThreadCpuTime();
                    for (int i; (i = next.getAndIncrement()) < order.size(); ) {
                        int cx = ox + order.get(i)[0], cz = oz + order.get(i)[1];
                        var g = src.generateWorldChunk(cx, cz);
                        src.applyCarvers(g, cx, cz);
                    }
                    cpu.addAndGet(MX.getCurrentThreadCpuTime() - c0);
                });
                th.start();
                ts.add(th);
            }
            for (Thread th : ts) th.join();
            long wall = System.nanoTime() - t0;
            long hits = cache.hits.sum() - h0, misses = cache.misses.sum() - m0;
            System.out.printf("NOISE_AREA %s round=%d chunks=%d wall=%.2fs cpu=%.2f ms/chunk cornerHit=%.1f%%%n", dim, round, order.size(),
                    wall / 1e9, cpu.get() / 1e6 / order.size(), hits + misses == 0 ? 0.0 : 100.0 * hits / (hits + misses));
        }
    }

    @Test
    void identityHash() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        hash(Path.of(System.getProperty("furnace.bench.out", "build/noise-identity.txt")));
    }

    @Test
    void timing() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        time(System.getProperty("furnace.bench.dims"));
    }
}
