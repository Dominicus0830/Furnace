// 노이즈 결과 동일 장면(NoiseIdentityBench 에서 뗀 장면 부분, T-003): 설정 목록·위치·청크/라우터 해시·record 장면.
// 시험 소스에 있어 평소 시험(NoiseIdentityGoldenTest, scene.NoiseQuickScene)과 bench 의 NoiseIdentityBench(전체 판·시간)가 함께 쓴다.
// 줄 형식은 금 파일 identity/worldgen-noise-{quick,full}.txt 와 같아야 한다 — 이 파일을 고칠 때는 줄이 그대로인지 두 금 파일로 확인한다.
package org.domi.furnace.server.world.gen;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.df.DensityFunction;

public final class NoiseIdentityScene {
    private NoiseIdentityScene() {}

    public static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    public static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    public record Config(String name, NoiseWorldSource.Dimension dim, String noiseSettings, long seed) {
        public NoiseWorldSource source() {
            return switch (dim) {
                case OVERWORLD -> NoiseWorldSource.overworld(seed, BLOCKS, REGISTRIES, noiseSettings);
                case NETHER -> NoiseWorldSource.nether(seed, BLOCKS, REGISTRIES);
                case END -> NoiseWorldSource.end(seed, BLOCKS, REGISTRIES);
            };
        }
    }

    public static List<Config> configs() {
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
    public static List<int[]> positions(Config cfg) {
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

    public static long fnv(long h, long v) { return (h ^ v) * 0x100000001B3L; }

    public static long hashColumn(NoiseWorldSource.Generated g) {
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
    public static long hashRouter(NoiseRouter r, long seed) {
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
    public static void record(SceneHash.Recorder rec, boolean full) throws Exception {
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
}
