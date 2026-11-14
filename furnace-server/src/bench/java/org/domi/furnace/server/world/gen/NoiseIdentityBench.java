// 노이즈 최적화 전후 동일성·시간 측정 도구(bench 작업 :furnace-server:bench 에서 JUnit 으로 돈다, main 으로도 실행 가능). 장면 부분은 시험 소스 NoiseIdentityScene.
//  hash <출력 파일>: 시드·차원·월드 타입·위치 여러 곳의 원시 청크(지형+표면+카버+바이옴) 해시와, 라우터 밀도 함수 15종을
//                    무작위 좌표에서 직접 평가한 double 비트 해시를 한 줄씩 적는다. 옛 커밋과 새 커밋의 출력 파일을 diff 한다.
//                    JUnit identityHash 는 같은 줄을 금 파일 identity/worldgen-noise-full.txt 와 비교한다(IdentityKit).
//                    짧은 판은 평소 시험 scene.SceneLibraryTest(장면 noise-quick)가 금 파일 worldgen-noise-quick.txt 와 비교한다.
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
import org.junit.jupiter.api.Test;

public class NoiseIdentityBench {
    // 장면 부분(설정·위치·해시·record)은 시험 소스 NoiseIdentityScene — 평소 시험도 쓴다(T-003).
    static final org.domi.furnace.server.world.BlockRegistry BLOCKS = NoiseIdentityScene.BLOCKS;
    static final org.domi.furnace.server.net.registry.RegistrySet REGISTRIES = NoiseIdentityScene.REGISTRIES;

    /** 옛 커밋·새 커밋 출력 파일을 diff 하는 손 비교용(main hash). 줄 형식은 금 파일과 같다. */
    public static void hash(Path out) throws Exception {
        List<String> lines = SceneHash.record(rec -> NoiseIdentityScene.record(rec, true));
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
        List<NoiseIdentityScene.Config> cs = new ArrayList<>();
        for (long s : new long[]{0L, 12345L, -1L, 42L, 8675309L})
            cs.add(new NoiseIdentityScene.Config("overworld", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:overworld", s));
        cs.add(new NoiseIdentityScene.Config("amplified", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:amplified", 7L));
        cs.add(new NoiseIdentityScene.Config("large_biomes", NoiseWorldSource.Dimension.OVERWORLD, "minecraft:large_biomes", -99L));
        for (long s : new long[]{0L, 12345L, 42L, -7L, 1L})
            cs.add(new NoiseIdentityScene.Config("nether", NoiseWorldSource.Dimension.NETHER, "minecraft:nether", s));
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            for (NoiseIdentityScene.Config cfg : cs) {
                NoiseWorldSource src = cfg.source();
                for (int cx = -side / 2; cx < side / 2; cx++) {
                    for (int cz = -side / 2; cz < side / 2; cz++) {
                        for (var st : src.structureStartsFresh(cx, cz)) {
                            var b = st.boundingBox();
                            StringBuilder sb = new StringBuilder();
                            sb.append(cfg.name()).append(' ').append(cfg.seed()).append(' ').append(cx).append(' ').append(cz).append(' ')
                              .append(st.structureId()).append(' ').append(bb(b));
                            for (var piece : st.pieces()) sb.append(" | ").append(bb(piece.boundingBox()));
                            w.println(sb);
                            counts.merge(cfg.name() + " " + st.structureId(), 1, Integer::sum);
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
        List<String> lines = SceneHash.assertGolden("worldgen-noise-full", rec -> NoiseIdentityScene.record(rec, true));
        Files.write(Path.of(System.getProperty("furnace.bench.out", "build/noise-identity.txt")),
                (String.join("\n", lines) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void timing() throws Exception {
        time(System.getProperty("furnace.bench.dims"));
    }
}
