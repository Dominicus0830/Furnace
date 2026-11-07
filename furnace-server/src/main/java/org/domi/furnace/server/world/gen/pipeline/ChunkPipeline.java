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
    // 비행처럼 앞으로 계속 나아가면 앞쪽 2열의 pristine 이 다음 청크들에서 다시 쓰인다 — 시야 한 줄(최대 65청크)×5 이상 담는다.
    private static final int PRISTINE_CACHE_SIZE = 512;

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
        // 생성이 모든 코어를 먹으면 틱 스레드가 밀려 틱이 튄다 — 코어 하나는 남기고, 생성 스레드는 낮은 우선순위로 돈다(윈도·맥에서 효과).
        int cores = Runtime.getRuntime().availableProcessors();
        this.genPool = new ForkJoinPool(Math.max(1, cores > 2 ? cores - 1 : cores), pool -> {
            var t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }, null, false);
    }

    /** 독립 경로의 pristine 캐시를 비운다(월드젠 훅이 바뀌었을 때 — 재생성이 지금 훅으로 만들게). */
    public void clearPristineCache() {
        synchronized (pristineCache) {
            pristineCache.clear();
        }
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
                NoiseWorldSource.Generated generated = source.generateWorldChunk(cx, cz);
                ChunkColumn col = generated.col();
                int[] biomeIds = generated.biomeIds();
                source.applyCarvers(generated, cx, cz);
                return new PristineChunk(col, biomeIds, source.biomeNamesOf(biomeIds), List.of());
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
                        long key = (((long) nx) << 32) ^ (nz & 0xFFFFFFFFL);
                        // 공유 캐시에 있는 결과(빈 결과가 대부분)는 작업을 띄우지 않는다.
                        ringFutures.computeIfAbsent(key, k -> {
                            List<StructureStart> cached = source.cachedStructureStarts(nx, nz);
                            return cached != null ? CompletableFuture.completedFuture(cached)
                                : CompletableFuture.supplyAsync(() -> source.structureStartsFresh(nx, nz), genPool);
                        });
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
                ringStarts.sort(java.util.Comparator.comparingInt(StructureStart::chunkX).thenComparingInt(StructureStart::chunkZ));

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
                        List<StructureStart> allStarts = ringStarts;
                        BoundingBox originColumn = new BoundingBox(
                            ox * 16, Integer.MIN_VALUE / 2, oz * 16, ox * 16 + 15, Integer.MAX_VALUE / 2, oz * 16 + 15);
                        List<StructureStart> referencedStarts = new ArrayList<>();
                        for (StructureStart start : allStarts) {
                            // createReferences: 반경 8 청크 안의 시작점만 참조된다.
                            if (Math.abs(start.chunkX() - ox) <= 8 && Math.abs(start.chunkZ() - oz) <= 8
                                && start.boundingBox().intersects(originColumn)) {
                                referencedStarts.add(start);
                            }
                        }

                        source.decorateOrigin(view, neighborBiomes, ox, oz, referencedStarts);
                    }
                }

                applyPostProcessing(grid, cx, cz);
                List<StructureStart> own = new ArrayList<>();
                for (StructureStart st : ringStarts) if (st.chunkX() == cx && st.chunkZ() == cz) own.add(st);
                grid[centerIdx].col.structures(
                    org.domi.furnace.server.world.gen.structure.ChunkStructures.generated(cx, cz, own, ringStarts));
                grid[centerIdx].col.compactSections();
                grid[centerIdx].col.clearDirty();
                grid[centerIdx].status = GenStatus.FULL;
                return grid[centerIdx].col;
            });
    }

    /**
     * 바닐라 LevelChunk.postProcessGeneration — 생성 중 markPosForPostprocessing 된 칸(울타리·횃불·사다리·철창 …)을
     * 섹션 순서(아래→위), 섹션 안에서는 표시 순서로 이웃 shape 갱신한다. 유체 블록은 건너뛴다(유체 틱은 서버 틱에서).
     */
    private void applyPostProcessing(ProtoColumn[] grid, int cx, int cz) {
        ProtoColumn center = grid[gridIndex(0, 0)];
        java.util.List<int[]> marks = center.col.postProcessMarks();
        if (marks.isEmpty()) return;
        marks.sort(java.util.Comparator.comparingInt(m -> Math.floorDiv(m[1], 16)));   // 안정 정렬: 섹션 오름차순
        ChunkColumn[] viewGrid = new ChunkColumn[GRID_SIZE * GRID_SIZE];
        int[][] viewBiomeGrid = new int[GRID_SIZE * GRID_SIZE][];
        for (int i = 0; i < grid.length; i++) {
            viewGrid[i] = grid[i].col;
            viewBiomeGrid[i] = grid[i].biomeIds;
        }
        FeatureWorldView view = new FeatureWorldView(viewGrid, viewBiomeGrid, cx, cz, cx, cz, GRID_RADIUS, true, source.seed());
        org.domi.furnace.server.world.gen.structure.StructureEngine e = source.structureEngine();
        for (int[] m : marks) {
            int x = cx * 16 + m[0], y = m[1], z = cz * 16 + m[2];
            int state = view.getBlock(x, y, z);
            String name = e.table().name(state);
            if (name.equals("minecraft:water") || name.equals("minecraft:lava")) continue;
            int s2 = org.domi.furnace.server.world.block.ShapeUpdater.updateFromNeighbourShapes(view, e.shapes(), state, x, y, z);
            if (s2 != state) view.setBlock(x, y, z, s2);
        }
        center.col.clearPostProcessMarks();
    }

    // ================================================================== 공유 월드 장식(서버 경로)
    //
    // 바닐라처럼 각 청크(origin)를 딱 한 번만 장식하고 그 결과(이웃으로 넘친 나무·광물 등)를 모든 청크가 공유한다.
    // generateFull(위)은 청크마다 주변 9개 origin 을 자기 복사본에 다시 장식해 (1) 장식 비용이 9배이고
    // (2) 경계에 걸친 나무가 양쪽 청크에서 서로 다르게(반쪽 나무) 나올 수 있었다. 여기서는:
    //  - pristine(CARVERS 상태) 청크를 ForkJoin 풀에서 병렬로 만들어 공유 저장소에 둔다.
    //  - 장식·후처리·완성은 단일 "장식 레인" 스레드가 차례로 한다 — 장식은 이웃 청크에 쓰므로 순서를 하나로 두면
    //    잠금 없이 일관되고, 청크당 장식은 수 ms 라 레인 하나로 초당 수백 청크를 처리한다.
    //  - 청크 C 를 완성하려면 C 의 3×3 origin 이 모두 장식돼야 한다(바닐라 FULL 이 이웃 FEATURES 를 요구하는 것과 같다).
    //    origin O 장식은 O 의 5×5 를 읽고 3×3 에 쓴다 → C 의 7×7 pristine 과 3×3 origin 의 반경 8 구조물 시작점이 필요.
    //  - 구조물 시작점도 장식용으로 한 번 만든 객체를 계속 쓴다(바닐라처럼 첫 배치 때 정해진 조각 상태가 유지된다).
    //  - 완성된 청크 객체는 그대로 월드로 넘기고, 이웃 장식이 읽을 수 있게 저장소에는 블록 복사본을 남긴다.
    // 같은 순서로 요청하면 결과가 같다. 요청 순서가 다르면 겹치는 장식이 달라질 수 있는데, 바닐라도 생성 순서에 따라 그렇다.

    private static final int SHARED_SOFT_CAP = 2048;
    private static final int SHARED_HARD_CAP = 4096;
    /** 최근 이만큼의 완성 안에 쓰인 청크는 버리거나 보관하지 않는다(플레이어 앞쪽에 막 만든 청크). */
    private static final int RECENT_USES = 768;
    /** 보관함(압축) 상한 — 넘치면 가장 오래된 것부터 버린다(그 근처를 다시 만들면 장식이 일부 빠질 수 있다). */
    private static final int PARKED_CAP = 16384;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ChunkPipeline.class);

    /** 공유 저장소의 청크 — 장식 레인만 고친다(col 교체·플래그). */
    private static final class Proto {
        final int cx, cz;
        ChunkColumn col;
        final int[] biomeIds;
        final Set<String> biomeNames;
        boolean decorated;   // 이 origin 의 장식을 마쳤다
        boolean spilled;     // 이웃(또는 자신)의 장식이 이 청크에 썼을 수 있다 — 버리면 다시 만들 수 없다
        boolean finalized;   // 완성돼 월드로 넘어갔다(col 은 읽기용 복사본)
        long lastUse;

        Proto(int cx, int cz, ChunkColumn col, int[] biomeIds, Set<String> biomeNames) {
            this.cx = cx; this.cz = cz; this.col = col; this.biomeIds = biomeIds; this.biomeNames = biomeNames;
        }
    }

    private final ConcurrentHashMap<Long, CompletableFuture<Proto>> sharedPristine = new ConcurrentHashMap<>();
    /** 장식용 구조물 시작점(요청 단계에서 있는지 보고, 레인에서 처음 것만 넣는다). */
    private final ConcurrentHashMap<Long, List<StructureStart>> decoStarts = new ConcurrentHashMap<>();
    // ---- 아래는 장식 레인 전용 ----
    private final java.util.HashMap<Long, Proto> store = new java.util.HashMap<>();
    // 오래 쓰이지 않았지만 다시 만들 수 없는 청크(이웃 장식이 써 넣은 시야 가장자리 등) — 섹션을 압축해 따로 둔다.
    // 저장소 전체를 훑는 비용·메모리가 비행 거리에 비례해 자라 저장소가 막히던 문제를 막는다.
    private final java.util.LinkedHashMap<Long, Proto> parked = new java.util.LinkedHashMap<>();
    private long lastEvictAt;
    private final java.util.HashSet<Long> decoratedKeys = new java.util.HashSet<>();
    private final java.util.HashSet<Long> finalizedKeys = new java.util.HashSet<>();
    private long useClock;
    private boolean warnedLossyEvict;
    private volatile long decorations;

    /** 공유 경로에서 지금까지 장식한 origin 수(테스트·모니터링용). */
    public long sharedDecorationCount() { return decorations; }
    private final java.util.concurrent.ExecutorService lane = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furnace-worldgen-decorate");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private CompletableFuture<Proto> sharedPristineFor(int cx, int cz) {
        return sharedPristine.computeIfAbsent(chunkKey(cx, cz), k -> CompletableFuture.supplyAsync(() -> {
            NoiseWorldSource.Generated g = source.generateWorldChunk(cx, cz);
            ChunkColumn col = g.col();
            source.applyCarvers(g, cx, cz);
            return new Proto(cx, cz, col, g.biomeIds(), source.biomeNamesOf(g.biomeIds()));
        }, genPool));
    }

    /** 서버 경로: 공유 월드에서 장식을 한 번씩만 해 청크를 완성한다. 돌려준 객체는 호출자 소유(더는 건드리지 않는다). */
    public CompletableFuture<ChunkColumn> generateShared(int cx, int cz) {
        List<CompletableFuture<Proto>> protos = new ArrayList<>(49);
        for (int dz = -3; dz <= 3; dz++)
            for (int dx = -3; dx <= 3; dx++)
                protos.add(sharedPristineFor(cx + dx, cz + dz));
        int r = STRUCTURE_START_RADIUS + 1;
        Map<Long, CompletableFuture<List<StructureStart>>> starts = new java.util.HashMap<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int nx = cx + dx, nz = cz + dz;
                long key = chunkKey(nx, nz);
                if (decoStarts.containsKey(key)) continue;
                List<StructureStart> cached = source.cachedStructureStarts(nx, nz);
                starts.put(key, cached != null ? CompletableFuture.completedFuture(cached)
                    : CompletableFuture.supplyAsync(() -> source.structureStartsFresh(nx, nz), genPool));
            }
        }
        List<CompletableFuture<?>> all = new ArrayList<>(protos.size() + starts.size());
        all.addAll(protos);
        all.addAll(starts.values());
        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]))
            .thenApplyAsync(__ -> finalizeShared(cx, cz, protos, starts), lane);
    }

    private ChunkColumn finalizeShared(int cx, int cz, List<CompletableFuture<Proto>> protos,
                                       Map<Long, CompletableFuture<List<StructureStart>>> starts) {
        long now = ++useClock;
        for (CompletableFuture<Proto> f : protos) {
            Proto p = f.join();
            long k = chunkKey(p.cx, p.cz);
            Proto in = store.get(k);
            if (in == null) {
                in = parked.remove(k);   // 보관함에 있던 것이면 되살린다(같은 객체)
                if (in == null) in = p;
                store.put(k, in);
            }
            in.lastUse = now;
        }
        for (Map.Entry<Long, CompletableFuture<List<StructureStart>>> e : starts.entrySet()) decoStarts.putIfAbsent(e.getKey(), e.getValue().join());

        long ck = chunkKey(cx, cz);
        Proto c = store.get(ck);
        if (c.finalized || finalizedKeys.contains(ck)) {
            // 이미 넘긴 청크를 다시 달라는 경우(월드가 저장 전에 버렸을 때 등) — 공유 상태로는 같은 결과를 다시 만들 수 없으니
            // 독립 경로로 만든다(드물다).
            LOG.debug("[WORLDGEN] 이미 완성된 청크 재요청 {},{} — 독립 경로로 생성", cx, cz);
            return generateFull(cx, cz).join();
        }
        for (int[] dd : decorateOrderFor(cx, cz)) {
            Proto o = store.get(chunkKey(cx + dd[0], cz + dd[1]));
            if (!o.decorated && !decoratedKeys.contains(chunkKey(o.cx, o.cz))) decorateShared(o);
        }
        postProcessShared(c);
        ChunkColumn out = c.col;
        out.structures(structuresOf(cx, cz));
        c.col = out.copy();          // 이웃 장식이 읽을 블록 복사본 — 넘긴 객체는 월드 스레드가 고친다
        c.finalized = true;
        finalizedKeys.add(ck);
        out.compactSections();
        out.clearDirty();
        evictShared();
        return out;
    }

    /** 완성 청크의 구조물 시작점·참조 — 장식에 쓴 시작점 객체 그대로(장식 레인에서, 조각 높이 보정이 끝난 뒤). */
    private org.domi.furnace.server.world.gen.structure.ChunkStructures structuresOf(int cx, int cz) {
        List<StructureStart> own = decoStarts.getOrDefault(chunkKey(cx, cz), List.of());
        List<StructureStart> nearby = new ArrayList<>();
        for (int dz = -STRUCTURE_START_RADIUS; dz <= STRUCTURE_START_RADIUS; dz++)
            for (int dx = -STRUCTURE_START_RADIUS; dx <= STRUCTURE_START_RADIUS; dx++) {
                List<StructureStart> l = decoStarts.get(chunkKey(cx + dx, cz + dz));
                if (l != null && !l.isEmpty()) nearby.addAll(l);
            }
        return org.domi.furnace.server.world.gen.structure.ChunkStructures.generated(cx, cz, own, nearby);
    }

    private Proto[] gridAround(int ox, int oz) {
        Proto[] g = new Proto[GRID_SIZE * GRID_SIZE];
        for (int dz = -GRID_RADIUS; dz <= GRID_RADIUS; dz++)
            for (int dx = -GRID_RADIUS; dx <= GRID_RADIUS; dx++)
                g[gridIndex(dx, dz)] = store.get(chunkKey(ox + dx, oz + dz));
        return g;
    }

    private void decorateShared(Proto o) {
        int ox = o.cx, oz = o.cz;
        Proto[] grid = gridAround(ox, oz);
        ChunkColumn[] cols = new ChunkColumn[grid.length];
        int[][] biomes = new int[grid.length][];
        for (int i = 0; i < grid.length; i++) { cols[i] = grid[i].col; biomes[i] = grid[i].biomeIds; }
        FeatureWorldView view = new FeatureWorldView(cols, biomes, ox, oz, ox, oz, GRID_RADIUS, false, source.seed());
        Set<String> neighborBiomes = new java.util.HashSet<>();
        for (int sdz = -1; sdz <= 1; sdz++)
            for (int sdx = -1; sdx <= 1; sdx++)
                neighborBiomes.addAll(grid[gridIndex(sdx, sdz)].biomeNames);
        BoundingBox originColumn = new BoundingBox(
            ox * 16, Integer.MIN_VALUE / 2, oz * 16, ox * 16 + 15, Integer.MAX_VALUE / 2, oz * 16 + 15);
        List<StructureStart> referenced = new ArrayList<>();
        for (int dz = -STRUCTURE_START_RADIUS; dz <= STRUCTURE_START_RADIUS; dz++) {
            for (int dx = -STRUCTURE_START_RADIUS; dx <= STRUCTURE_START_RADIUS; dx++) {
                List<StructureStart> l = decoStarts.get(chunkKey(ox + dx, oz + dz));
                if (l == null) continue;
                for (StructureStart st : l) if (st.boundingBox().intersects(originColumn)) referenced.add(st);
            }
        }
        referenced.sort(java.util.Comparator.comparingInt(StructureStart::chunkX).thenComparingInt(StructureStart::chunkZ));
        source.decorateOrigin(view, neighborBiomes, ox, oz, referenced);
        decorations++;
        o.decorated = true;
        decoratedKeys.add(chunkKey(ox, oz));
        for (int sdz = -1; sdz <= 1; sdz++)
            for (int sdx = -1; sdx <= 1; sdx++)
                grid[gridIndex(sdx, sdz)].spilled = true;
    }

    private void postProcessShared(Proto c) {
        java.util.List<int[]> marks = c.col.postProcessMarks();
        if (marks.isEmpty()) return;
        marks.sort(java.util.Comparator.comparingInt(m -> Math.floorDiv(m[1], 16)));
        Proto[] grid = gridAround(c.cx, c.cz);
        ChunkColumn[] cols = new ChunkColumn[grid.length];
        int[][] biomes = new int[grid.length][];
        for (int i = 0; i < grid.length; i++) { cols[i] = grid[i].col; biomes[i] = grid[i].biomeIds; }
        FeatureWorldView view = new FeatureWorldView(cols, biomes, c.cx, c.cz, c.cx, c.cz, GRID_RADIUS, true, source.seed());
        org.domi.furnace.server.world.gen.structure.StructureEngine e = source.structureEngine();
        for (int[] m : marks) {
            int x = c.cx * 16 + m[0], y = m[1], z = c.cz * 16 + m[2];
            int state = view.getBlock(x, y, z);
            String name = e.table().name(state);
            if (name.equals("minecraft:water") || name.equals("minecraft:lava")) continue;
            int s2 = org.domi.furnace.server.world.block.ShapeUpdater.updateFromNeighbourShapes(view, e.shapes(), state, x, y, z);
            if (s2 != state) view.setBlock(x, y, z, s2);
        }
        c.col.clearPostProcessMarks();
    }

    /**
     * 저장소가 상한을 넘으면 다시 만들 수 있거나 더는 쓰이지 않는 청크부터 버린다:
     *  - 아무 장식도 닿지 않은 pristine (다시 만들면 같다)
     *  - 완성됐고 반경 2 안의 origin 이 모두 장식된 청크 (더 읽거나 쓸 일이 없다)
     * 그래도 hard cap 을 넘으면(멀리 흩어진 시야 가장자리가 쌓인 경우) 오래된 것부터 버린다 — 그 근처를 나중에 다시 만들면
     * 이미 장식된 이웃의 넘침이 빠질 수 있다(바닐라는 이런 미완성 청크를 디스크에 저장한다; 후속 과제).
     */
    private static final boolean DEBUG_EVICT = Boolean.getBoolean("furnace.debugEvict");
    private long evictCalls, evictNanos, finalizes;

    private void evictShared() {
        finalizes++;
        if (DEBUG_EVICT && finalizes % 200 == 0) {
            LOG.info("[WORLDGEN-DBG] finalizes={} store={} parked={} evictScans={} evictMs={}", finalizes, store.size(), parked.size(), evictCalls, evictNanos / 1_000_000);
        }
        // 시작점 상한은 store 크기와 무관하게 매 완성마다 본다(크기 비교만이라 싸다)
        if (decoStarts.size() > decoStartsCap) trimDecoStarts();
        if (store.size() <= SHARED_SOFT_CAP) return;
        // 훑기는 완성 64번에 한 번(hard cap 을 넘었을 때만 바로) — 매번 전체를 훑으면 장식 레인이 그만큼 느려진다.
        if (store.size() <= SHARED_HARD_CAP && finalizes - lastEvictAt < 64) return;
        lastEvictAt = finalizes;
        long t0 = System.nanoTime();
        evictCalls++;
        try { evictSharedScan(); } finally { evictNanos += System.nanoTime() - t0; }
    }

    private void evictSharedScan() {
        int target = SHARED_SOFT_CAP * 3 / 4;
        long recent = useClock - RECENT_USES;
        List<Proto> droppable = new ArrayList<>();   // 다시 만들면 같거나 더 쓰일 일이 없다
        List<Proto> parkable = new ArrayList<>();    // 다시 만들 수 없다 — 압축해 보관
        for (Proto p : store.values()) {
            if (p.lastUse > recent) continue;        // 앞쪽에 막 만든 청크는 그대로 둔다(버리면 곧 다시 만든다)
            if (!p.spilled && !p.decorated && !p.finalized) droppable.add(p);
            else if (p.finalized && allDecoratedAround(p.cx, p.cz, GRID_RADIUS)) droppable.add(p);
            else parkable.add(p);
        }
        droppable.sort(java.util.Comparator.comparingLong(p -> p.lastUse));
        int toDrop = Math.min(droppable.size(), store.size() - target);
        for (int i = 0; i < toDrop; i++) drop(droppable.get(i));
        if (store.size() > target) {
            parkable.sort(java.util.Comparator.comparingLong(p -> p.lastUse));
            int n = Math.min(parkable.size(), store.size() - target);
            for (int i = 0; i < n; i++) park(parkable.get(i));
        }
        if (store.size() > SHARED_HARD_CAP) {
            // 최근 것까지 꽉 찼다(아주 넓은 시야·여러 플레이어) — 오래된 것부터 보관함으로
            List<Proto> rest = new ArrayList<>(store.values());
            rest.sort(java.util.Comparator.comparingLong(p -> p.lastUse));
            int n = store.size() - target;
            for (int i = 0; i < n && i < rest.size(); i++) park(rest.get(i));
        }
        if (parked.size() > PARKED_CAP) {
            var it = parked.values().iterator();
            while (parked.size() > PARKED_CAP * 3 / 4 && it.hasNext()) {
                Proto p = it.next();
                it.remove();
                sharedPristine.remove(chunkKey(p.cx, p.cz));
            }
            if (!warnedLossyEvict) {
                warnedLossyEvict = true;
                LOG.warn("[WORLDGEN] 미완성 청크 보관함이 가득 차 오래된 것을 버렸다(그 근처를 다시 만들면 장식이 일부 빠질 수 있다)");
            }
        }
        if (decoratedKeys.size() > (1 << 21)) decoratedKeys.clear();
        if (finalizedKeys.size() > (1 << 21)) finalizedKeys.clear();
    }

    /** 테스트용: 서버 경로가 이 청크에 대해 들고 있는 시작점 목록(없으면 null). */
    List<StructureStart> decoStartsAt(int cx, int cz) { return decoStarts.get(chunkKey(cx, cz)); }

    // decoStarts 상한. 넘으면 다시 만들어도 같은 결과인 항목만 버린다(테스트가 낮춘다).
    int decoStartsCap = 1 << 18;

    // 감사 I-1 후속: 통째로 clear() 하면 장식 도중인 시작점(사막 신전 hasPlacedChest·heightPosition 등)이 새 객체로 다시 만들어져
    // 남은 청크를 처음 상태로 장식한다. 버려도 되는 것은 ① 가변 조각이 없는 결과(빈 결과·jigsaw 전용 — 다시 만들어도 같다)
    // ② 반경 8 안 청크가 전부 완성돼 더는 장식·참조 질의에 쓰이지 않는 결과뿐이다. 그래도 남으면 상한을 늘린다.
    private void trimDecoStarts() {
        var it = decoStarts.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            boolean immutable = true;
            for (StructureStart st : e.getValue()) {
                for (var piece : st.pieces()) if (!(piece instanceof org.domi.furnace.server.world.gen.structure.JigsawPiece)) { immutable = false; break; }
                if (!immutable) break;
            }
            if (immutable) { it.remove(); continue; }
            int kx = (int) (e.getKey() >> 32), kz = (int) (long) e.getKey();
            if (allFinalizedAround(kx, kz, STRUCTURE_START_RADIUS)) it.remove();
        }
        if (decoStartsCap > 0 && decoStarts.size() > decoStartsCap * 3 / 4) decoStartsCap = Math.max(decoStartsCap, decoStarts.size() * 2);
    }

    private boolean allFinalizedAround(int cx, int cz, int r) {
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++)
                if (!finalizedKeys.contains(chunkKey(cx + dx, cz + dz))) return false;
        return true;
    }

    private boolean allDecoratedAround(int cx, int cz, int r) {
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++)
                if (!decoratedKeys.contains(chunkKey(cx + dx, cz + dz))) return false;
        return true;
    }

    /** 다시 만들 수 없는 청크를 압축해 보관함으로(되살릴 때는 같은 객체를 쓴다 — 압축된 섹션에도 그대로 쓸 수 있다). */
    private void park(Proto p) {
        long k = chunkKey(p.cx, p.cz);
        store.remove(k);
        p.col.compactSections();
        parked.put(k, p);
    }

    private void drop(Proto p) {
        long k = chunkKey(p.cx, p.cz);
        store.remove(k);
        sharedPristine.remove(k);
    }
}
