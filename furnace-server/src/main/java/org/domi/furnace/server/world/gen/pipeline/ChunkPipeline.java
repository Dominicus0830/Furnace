package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureStart;

import java.util.ArrayList;
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
    // decoration에 필요한 5x5 grid 반경(±2) — 안쪽 3x3 origin 각각 자신의 3x3 view를 가지려면 필요.
    private static final int GRID_RADIUS = 2;
    private static final int GRID_SIZE = 2 * GRID_RADIUS + 1;

    private final NoiseWorldSource source;
    private final ForkJoinPool genPool;

    public ChunkPipeline(NoiseWorldSource source) {
        this.source = source;
        this.genPool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    private CompletableFuture<ProtoColumn> toStructureStarts(int cx, int cz) {
        return CompletableFuture.supplyAsync(() -> {
            ProtoColumn proto = new ProtoColumn(cx, cz);
            proto.col = source.generate(cx, cz);
            proto.biomeIds = source.sampleBiomes(cx, cz);
            proto.status = GenStatus.SURFACE;
            source.applyCarvers(proto.col, cx, cz);
            proto.status = GenStatus.CARVERS;
            proto.starts = source.applyStructureStarts(cx, cz);
            proto.status = GenStatus.STRUCTURE_STARTS;
            return proto;
        }, genPool);
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
                for (int ddz = -1; ddz <= 1; ddz++) {
                    for (int ddx = -1; ddx <= 1; ddx++) {
                        int ox = cx + ddx;
                        int oz = cz + ddz;

                        ChunkColumn[] viewGrid = new ChunkColumn[9];
                        int[][] viewBiomeGrid = new int[9][];
                        for (int sdz = -1; sdz <= 1; sdz++) {
                            for (int sdx = -1; sdx <= 1; sdx++) {
                                ProtoColumn p = grid[gridIndex(ddx + sdx, ddz + sdz)];
                                int subIdx = (sdz + 1) * 3 + (sdx + 1);
                                viewGrid[subIdx] = p.col;
                                viewBiomeGrid[subIdx] = p.biomeIds;
                            }
                        }
                        FeatureWorldView view = new FeatureWorldView(viewGrid, viewBiomeGrid, ox, oz, false, source.seed());
                        Set<String> neighborBiomes = source.collectNeighborBiomes(ox, oz);

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
