// 노이즈 최적화 전후 동일성·시간 측정 도구(-Dfurnace.bench=true 일 때만 JUnit 으로 돈다, main 으로도 실행 가능).
//  hash <출력 파일>: 시드·차원·월드 타입·위치 여러 곳의 원시 청크(지형+표면+카버+바이옴) 해시와, 라우터 밀도 함수 15종을
//                    무작위 좌표에서 직접 평가한 double 비트 해시를 한 줄씩 적는다. 옛 커밋과 새 커밋의 출력 파일을 diff 한다.
//                    JUnit identityHash 는 같은 줄을 금 파일 identity/worldgen-noise-full.txt 와 비교한다(IdentityKit).
//                    짧은 판은 평소 시험 NoiseIdentityGoldenTest 가 금 파일 worldgen-noise-quick.txt 와 비교한다.
//  time: 한 스레드에서 단계별 청크당 CPU 시간(NoiseChunk 생성·지형 채우기·바이옴·원시 청크 전체)을 잰다.
package org.domi.furnace.server.world.gen;

import java.io.PrintWriter;
import org.domi.furnace.server.identity.SceneHash;
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
        // 네더 시드 더(표면 규칙 바이옴 조회 — 네더 바이옴 경계가 섞인 곳)
        for (long s : new long[]{1L, -1L, 8675309L, Long.MAX_VALUE, 20261111L, -4172144997902289642L})
            c.add(new Config("nether", NoiseWorldSource.Dimension.NETHER, "minecraft:nether", s));
        return c;
    }

    /** 짧은 판 설정: 월드 타입·차원마다 하나 이상, 네더는 표면 규칙 바이옴 경계 시드 하나 더. */
    static final java.util.Set<String> QUICK = java.util.Set.of("overworld 0", "overworld 12345", "large_biomes 12345",
            "amplified 7", "nether 12345", "nether -4172144997902289642", "end 42");

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
        // 월드 경계(블록 ±29,999,984 = 청크 ±1874999) 바로 안·바깥 — 큰 좌표의 quart·지터 계산
        p.add(new int[]{1874999, 1874999});
        p.add(new int[]{-1875000, -1875000});
        p.add(new int[]{1875000 + r.nextInt(4), -1874999});
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

    /**
     * 동일성 장면: 설정마다 라우터 해시, 위치마다 원시 청크 해시, 일부 설정은 이어진 5×5 를 3 스레드로.
     * full=false 는 CI 용 짧은 부분집합(설정 7개 × 라우터 + 청크 두 곳, 동시 생성 없음) — NoiseIdentityGoldenTest.
     * 시간은 대부분 오버월드 계열 소스마다 첫 청크(지연 초기화, 1.2~2.4초)라 설정 수를 줄였다 — 소스 만들기 자체는 합쳐 약 1.6초
     * (PR #290 검토 R5 측정). 29개 전부면 두 번 돌려 약 54초(4코어).
     */
    static void record(SceneHash.Recorder rec, boolean full) throws Exception {
        int ci = 0;
        for (Config cfg : configs()) {
            if (!full && !QUICK.contains(cfg.name + " " + cfg.seed)) continue;
            NoiseWorldSource src = cfg.source();
            rec.line("router " + cfg.name + " " + cfg.seed, hashRouter(src.router(), cfg.seed));
            List<int[]> ps = positions(cfg);
            // 짧은 판: 원점 2×2 묶음의 첫 칸과 먼 곳(±10만 블록) 한 칸
            if (!full) ps = List.of(ps.get(0), ps.get(4 + 10 + 3));
            for (int[] p : ps) {
                NoiseWorldSource.Generated g = src.generateWorldChunk(p[0], p[1]);
                src.applyCarvers(g, p[0], p[1]);
                rec.line("chunk " + cfg.name + " " + cfg.seed + " " + p[0] + " " + p[1], hashColumn(g));
            }
            if (full && (ci++ % 3 == 0 || cfg.dim == NoiseWorldSource.Dimension.NETHER)) {
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
                if (res.size() != 25) throw new AssertionError("동시 생성 결과 " + res.size() + "/25");
                for (var e : res.entrySet()) {
                    int i = e.getKey().intValue();
                    rec.line("area " + cfg.name + " " + cfg.seed + " " + (bx + i % 5) + " " + (bz + i / 5), e.getValue());
                }
            }
        }
    }

    /** 옛 커밋·새 커밋 출력 파일을 diff 하는 손 비교용(main hash). 줄 형식은 금 파일과 같다. */
    public static void hash(Path out) throws Exception {
        List<String> lines = SceneHash.record(rec -> record(rec, true));
        Files.write(out, (String.join("\n", lines) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("NOISE_IDENTITY " + lines.get(lines.size() - 1));
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
                NoiseWorldSource src = sourceFor(System.getProperty("furnace.bench.dim", "overworld"));
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
            case "starts" -> starts(Path.of(args[1]), Integer.parseInt(args.length > 2 ? args[2] : "200"));
            case "area" -> area(args.length > 1 ? args[1] : "overworld", Integer.parseInt(args.length > 2 ? args[2] : "3"),
                    Integer.parseInt(args.length > 3 ? args[3] : "24"));
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /**
     * 구조물 시작점(종류·상자·조각 상자)을 side×side 청크 영역에서 모두 적는다 — baseBlock·높이 질의가 바뀌었을 때 배치가 같은지 본다.
     * 시드·차원마다 청크 순서를 섞지 않고 차례로(캐시 상태와 무관하게 같은 값이어야 한다).
     */
    static String bb(org.domi.furnace.server.world.gen.structure.BoundingBox b) {
        return b.minX() + "," + b.minY() + "," + b.minZ() + ".." + b.maxX() + "," + b.maxY() + "," + b.maxZ();
    }

    public static void starts(Path out, int side) throws Exception {
        List<Config> cs = new ArrayList<>();
        for (long s : new long[]{0L, 12345L, -1L, 42L, 8675309L})
            cs.add(new Config("overworld", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:overworld", s));
        cs.add(new Config("amplified", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:amplified", 7L));
        cs.add(new Config("large_biomes", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:large_biomes", -99L));
        for (long s : new long[]{0L, 12345L, 42L, -7L, 1L})
            cs.add(new Config("nether", NoiseWorldSource.Dimension.NETHER, "minecraft:nether", s));
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            for (Config cfg : cs) {
                NoiseWorldSource src = cfg.source();
                for (int cx = -side / 2; cx < side / 2; cx++) {
                    for (int cz = -side / 2; cz < side / 2; cz++) {
                        for (var st : src.structureStartsFresh(cx, cz)) {
                            var b = st.boundingBox();
                            StringBuilder sb = new StringBuilder();
                            sb.append(cfg.name).append(' ').append(cfg.seed).append(' ').append(cx).append(' ').append(cz).append(' ')
                              .append(st.structureId()).append(' ').append(bb(b));
                            for (var piece : st.pieces()) sb.append(" | ").append(bb(piece.boundingBox()));
                            w.println(sb);
                            counts.merge(cfg.name + " " + st.structureId(), 1, Integer::sum);
                        }
                    }
                }
                w.flush();
            }
        }
        counts.forEach((k, v) -> System.out.println("STARTS " + k + " " + v));
    }

    /**
     * 서버처럼 이어진 땅을 만든다: side×side 청크를 가운데서 바깥으로(나선 비슷하게 거리순) threads 개 스레드가 나눠 원시 청크(지형·표면·카버)로.
     * 벽시계·전체 CPU·꼭짓점 캐시 적중을 적는다. 라운드마다 새 곳(캐시가 비어 있는 땅).
     */
    static NoiseWorldSource sourceFor(String dim) {
        return switch (dim) {
            case "nether" -> NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES);
            case "end" -> NoiseWorldSource.end(12345L, BLOCKS, REGISTRIES);
            case "amplified" -> NoiseWorldSource.overworld(12345L, BLOCKS, REGISTRIES, "minecraft:amplified");
            default -> NoiseWorldSource.overworld(12345L, BLOCKS, REGISTRIES);
        };
    }

    public static void area(String dim, int threads, int side) throws Exception {
        NoiseWorldSource src = sourceFor(dim);
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

    /** 전체 판(설정 29개 × 위치 약 22곳 + 동시 생성 영역): 결정성 점검 뒤 금 파일 worldgen-noise-full 과 비교, 결과 파일도 남긴다. */
    @Test
    void identityHash() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        List<String> lines = SceneHash.assertGolden("worldgen-noise-full", rec -> record(rec, true));
        Files.write(Path.of(System.getProperty("furnace.bench.out", "build/noise-identity.txt")),
                (String.join("\n", lines) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void timing() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        time(System.getProperty("furnace.bench.dims"));
    }
}
