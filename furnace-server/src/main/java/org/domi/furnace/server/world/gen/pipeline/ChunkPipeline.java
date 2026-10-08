package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

// 청크 단계별 파이프라인 오케스트레이터. 5x5 grid를 CARVERS까지 병렬 생성하고 안쪽 3x3 origin 9개를
// 전부 decorate한다 — 이웃 origin의 decoration이 center로 넘어오는 spill(나무 캐노피 등)을 재현하기 위함.
public final class ChunkPipeline {
    // 스펙 §2 R1 — STRUCTURE_REFERENCES 상당: 각 origin의 features는 최대 radius 8 떨어진 곳에서 시작된
    // 구조물 조각을 볼 수 있어야 한다(구조물 최대 크기 21x21 등 고려한 vanilla GENERATION_PYRAMID 값).
    private static final int STRUCTURE_START_RADIUS = 8;
    /** 디버그: 요청 청크만 decorate(이웃 origin 생략) — 순정을 청크 1개만 forceload한 결과와 대조할 때 쓴다. */
    private static final boolean ISOLATED_DECORATE = Boolean.getBoolean("furnace.isolatedDecorate");

    /** 3x3 origin decorate 순서(ddx,ddz). 기본은 dz→dx 행 우선; 디버그용 -Dfurnace.decorateOrder="ddx,ddz;..." 로 덮어쓴다. */
    private static final int[][] DECORATE_ORDER = parseOrder(System.getProperty("furnace.decorateOrder"));

    /**
     * 디버그: 순정 서버가 실제로 청크를 decorate한 순서(-Dfurnace.decorateRanks=파일, 줄마다 "cx cz rank").
     * 지정하면 각 중심 청크의 3x3 origin을 순정 순위대로 정렬해 decorate한다 — 순서 비결정성 때문에 생기는
     * 불일치와 진짜 feature 버그를 분리하려는 측정 전용 경로이며 서버 기본 동작에는 영향이 없다.
     */
    private static final java.util.Map<Long, Integer> RANKS = loadRanks(System.getProperty("furnace.decorateRanks"));

    private static java.util.Map<Long, Integer> loadRanks(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            java.util.Map<Long, Integer> m = new java.util.HashMap<>();
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(path))) {
                String[] t = line.trim().split("\\s+");
                if (t.length == 3) m.put(chunkKey(Integer.parseInt(t[0]), Integer.parseInt(t[1])), Integer.parseInt(t[2]));
            }
            return m;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("decorateRanks 읽기 실패: " + path, e);
        }
    }

    private static int[][] decorateOrderFor(int cx, int cz) {
        if (RANKS == null) return DECORATE_ORDER;
        int[][] order = DECORATE_ORDER.clone();
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(
            dd -> RANKS.getOrDefault(chunkKey(cx + dd[0], cz + dd[1]), Integer.MAX_VALUE)));
        return order;
    }

    private static int[][] parseOrder(String spec) {
        if (spec == null || spec.isBlank()) {
            int[][] def = new int[9][];
            int i = 0;
            for (int ddz = -1; ddz <= 1; ddz++) for (int ddx = -1; ddx <= 1; ddx++) def[i++] = new int[] {ddx, ddz};
            return def;
        }
        String[] parts = spec.split(";");
        int[][] r = new int[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] xy = parts[i].split(",");
            r[i] = new int[] {Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())};
        }
        return r;
    }

    // decoration에 필요한 5x5 grid 반경(±2) — 안쪽 3x3 origin 각각 자신의 3x3 view를 가지려면 필요.
    private static final int GRID_RADIUS = 2;
    private static final int GRID_SIZE = 2 * GRID_RADIUS + 1;
    // pristine(CARVERS 상태, 장식 이전) 캐시 크기 — bounded 필수. 서빙 패턴은 5x5 grid(25칸)가 1칸씩
    // 이동하므로 20개가 겹친다; 128이면 그 작업 집합 + 여러 동시 generateFull 호출의 여유분까지 충분히 커버한다.
    private static final int PRISTINE_CACHE_SIZE = 128;

    private final NoiseWorldSource source;
    private final ForkJoinPool genPool;
    // decoration이 col을 변형하므로 여기 캐시된 값은 절대 그대로 넘기지 않고 항상 copy()해서 쓴다(toStructureStarts 참조).
    // synchronized LinkedHashMap(accessOrder=true) + removeEldestEntry로 단순 LRU — 동시성 정확성만 필요, 정교함 불필요.
    private final LinkedHashMap<Long, CompletableFuture<PristineChunk>> pristineCache =
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<PristineChunk>> eldest) {
                return size() > PRISTINE_CACHE_SIZE;
            }
        };

    public ChunkPipeline(NoiseWorldSource source) {
        this.source = source;
        this.genPool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private CompletableFuture<PristineChunk> pristineFor(int cx, int cz) {
        long key = chunkKey(cx, cz);
        synchronized (pristineCache) {
            CompletableFuture<PristineChunk> existing = pristineCache.get(key);
            if (existing != null) return existing;
            CompletableFuture<PristineChunk> future = CompletableFuture.supplyAsync(() -> {
                NoiseWorldSource.Generated generated = source.generateWithBiomes(cx, cz);
                ChunkColumn col = generated.col();
                int[] biomeIds = generated.biomeIds();
                source.applyCarvers(col, cx, cz);
                List<StructureStart> starts = source.applyStructureStarts(cx, cz);
                return new PristineChunk(col, biomeIds, source.biomeNamesOf(biomeIds), starts);
            }, genPool);
            pristineCache.put(key, future);
            return future;
        }
    }

    private CompletableFuture<ProtoColumn> toStructureStarts(int cx, int cz) {
        return pristineFor(cx, cz).thenApply(pristine -> {
            ProtoColumn proto = new ProtoColumn(cx, cz);
            proto.col = pristine.col().copy();
            proto.biomeIds = pristine.biomeIds();
            proto.biomeNames = pristine.biomeNames();
            proto.starts = pristine.starts();
            proto.status = GenStatus.STRUCTURE_STARTS;
            return proto;
        });
    }

    private int gridIndex(int dx, int dz) {
        return (dz + GRID_RADIUS) * GRID_SIZE + (dx + GRID_RADIUS);
    }

    public CompletableFuture<ChunkColumn> generateFull(int cx, int cz) {
        List<CompletableFuture<ProtoColumn>> futures = new ArrayList<>(GRID_SIZE * GRID_SIZE);
        for (int dz = -GRID_RADIUS; dz <= GRID_RADIUS; dz++) {
            for (int dx = -GRID_RADIUS; dx <= GRID_RADIUS; dx++) {
                futures.add(toStructureStarts(cx + dx, cz + dz));
            }
        }

        // 안쪽 3×3 origin 9개 각각의 radius 8 링(5x5 grid 밖) — STRUCTURE_STARTS만 필요, col/carvers 불필요(순수
        // 위치함수, F6). 여러 origin의 링이 겹치는 좌표는 dedupe(같은 (x,z) future 재사용 — 성능 최적화가 아니라
        // 동일 좌표에 중복 future를 띄우지 않기 위한 최소한의 정합성 조치).
        Map<Long, CompletableFuture<List<StructureStart>>> ringFutures = new ConcurrentHashMap<>();
        for (int ddz = -1; ddz <= 1; ddz++) {
            for (int ddx = -1; ddx <= 1; ddx++) {
                int ox = cx + ddx;
                int oz = cz + ddz;
                for (int dz = -STRUCTURE_START_RADIUS; dz <= STRUCTURE_START_RADIUS; dz++) {
                    for (int dx = -STRUCTURE_START_RADIUS; dx <= STRUCTURE_START_RADIUS; dx++) {
                        int nx = ox + dx;
                        int nz = oz + dz;
                        if (Math.abs(nx - cx) <= GRID_RADIUS && Math.abs(nz - cz) <= GRID_RADIUS) continue; // 5x5 grid가 이미 커버
                        long key = (((long) nx) << 32) ^ (nz & 0xFFFFFFFFL);
                        ringFutures.computeIfAbsent(key,
                            k -> CompletableFuture.supplyAsync(() -> source.applyStructureStarts(nx, nz), genPool));
                    }
                }
            }
        }

        List<CompletableFuture<?>> all = new ArrayList<>(futures.size() + ringFutures.size());
        all.addAll(futures);
        all.addAll(ringFutures.values());

        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]))
            .thenApply(__ -> {
                ProtoColumn[] grid = new ProtoColumn[GRID_SIZE * GRID_SIZE];
                for (int i = 0; i < grid.length; i++) {
                    grid[i] = futures.get(i).join();
                }

                // 링 밖 start 전체(중복 제거된 미래들의 합집합) — origin별 referencedStarts 필터링에 공용으로 쓴다.
                List<StructureStart> ringStarts = new ArrayList<>();
                for (CompletableFuture<List<StructureStart>> rf : ringFutures.values()) {
                    ringStarts.addAll(rf.join());
                }

                // 안쪽 3×3 origin 9개를 dz 오름차순→dx 오름차순(행 우선)으로 decorate — oracle featurespill과
                // 반드시 동일한 순서라야 겹치는 spill 영역에서 byte-exact가 성립한다(§3 참조, 오라클 쪽 주석 동일).
                int centerIdx = gridIndex(0, 0);
                for (int[] dd : decorateOrderFor(cx, cz)) {
                    int ddx = dd[0];
                    int ddz = dd[1];
                    {
                        if (ISOLATED_DECORATE && (ddx != 0 || ddz != 0)) continue;
                        int ox = cx + ddx;
                        int oz = cz + ddz;

                        // 읽기는 5x5 grid 전체(oracle OracleWorldGenLevel의 getChunk radius=2와 동일 범위)를 그대로
                        // 넘긴다 — origin 기준 3x3로 잘라 넘기면 fancy oak makeLimb의 isFree 판정(읽기)이 grid
                        // 경계 밖을 조기에 air로 간주해 oracle과 분기 판정이 달라진다(§3 참조). 쓰기는
                        // FeatureWorldView 내부에서 origin(ox,oz) ±1로 별도 제한되므로 안전하다.
                        ChunkColumn[] viewGrid = new ChunkColumn[GRID_SIZE * GRID_SIZE];
                        int[][] viewBiomeGrid = new int[GRID_SIZE * GRID_SIZE][];
                        for (int i = 0; i < grid.length; i++) {
                            viewGrid[i] = grid[i].col;
                            viewBiomeGrid[i] = grid[i].biomeIds;
                        }
                        FeatureWorldView view = new FeatureWorldView(
                            viewGrid, viewBiomeGrid, ox, oz, cx, cz, GRID_RADIUS, false, source.seed());
                        // origin 주변 3x3 청크의 biome 이름 합집합 — 5x5 grid에 이미 샘플된 값을 재사용(origin마다 9청크를 다시 샘플하지 않음)
                        Set<String> neighborBiomes = new java.util.HashSet<>();
                        for (int sdz = -1; sdz <= 1; sdz++) {
                            for (int sdx = -1; sdx <= 1; sdx++) {
                                neighborBiomes.addAll(grid[gridIndex(ddx + sdx, ddz + sdz)].biomeNames);
                            }
                        }

                        // STRUCTURE_REFERENCES 상당: radius 8 내 모든 start 중 BoundingBox가 이 origin 청크에
                        // 걸치는 것만 수집(F6). 5x5 grid 내부 start + 공용 ring start를 합쳐서 필터링.
                        List<StructureStart> allStarts = new ArrayList<>(ringStarts);
                        for (int sdz = -1; sdz <= 1; sdz++) {
                            for (int sdx = -1; sdx <= 1; sdx++) {
                                allStarts.addAll(grid[gridIndex(ddx + sdx, ddz + sdz)].starts);
                            }
                        }
                        BoundingBox originColumn = new BoundingBox(
                            ox * 16, Integer.MIN_VALUE / 2, oz * 16, ox * 16 + 15, Integer.MAX_VALUE / 2, oz * 16 + 15);
                        List<StructureStart> referencedStarts = new ArrayList<>();
                        for (StructureStart start : allStarts) {
                            if (start.boundingBox().intersects(originColumn)) {
                                referencedStarts.add(start);
                            }
                        }

                        source.decorateOrigin(view, neighborBiomes, ox, oz, referencedStarts);
                    }
                }

                grid[centerIdx].col.compactSections();
                grid[centerIdx].col.clearDirty();
                grid[centerIdx].status = GenStatus.FULL;
                return grid[centerIdx].col;
            });
    }
}
