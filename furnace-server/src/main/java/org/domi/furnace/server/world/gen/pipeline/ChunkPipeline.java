package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

// 청크 단계별 파이프라인 오케스트레이터. 9-grid를 CARVERS까지 병렬 생성한다.
public final class ChunkPipeline {
    // 스펙 §2 R1 — STRUCTURE_REFERENCES 상당: center features는 최대 radius 8 떨어진 곳에서 시작된
    // 구조물 조각을 볼 수 있어야 한다(구조물 최대 크기 21x21 등 고려한 vanilla GENERATION_PYRAMID 값).
    private static final int STRUCTURE_START_RADIUS = 8;

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

    public CompletableFuture<ChunkColumn> generateFull(int cx, int cz) {
        List<CompletableFuture<ProtoColumn>> futures = new ArrayList<>(9);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                futures.add(toStructureStarts(cx + dx, cz + dz));
            }
        }

        // radius 8 링(9-grid 밖) — STRUCTURE_STARTS만 필요, col/carvers 불필요(순수 위치함수, F6).
        List<CompletableFuture<List<StructureStart>>> ringFutures = new ArrayList<>();
        for (int dz = -STRUCTURE_START_RADIUS; dz <= STRUCTURE_START_RADIUS; dz++) {
            for (int dx = -STRUCTURE_START_RADIUS; dx <= STRUCTURE_START_RADIUS; dx++) {
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) continue; // 9-grid가 이미 커버
                int nx = cx + dx;
                int nz = cz + dz;
                ringFutures.add(CompletableFuture.supplyAsync(() -> source.applyStructureStarts(nx, nz), genPool));
            }
        }

        List<CompletableFuture<?>> all = new ArrayList<>(futures.size() + ringFutures.size());
        all.addAll(futures);
        all.addAll(ringFutures);

        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]))
            .thenApply(__ -> {
                ProtoColumn[] grid = new ProtoColumn[9];
                for (int i = 0; i < grid.length; i++) {
                    grid[i] = futures.get(i).join();
                }

                // vanilla 동일: center (cx,cz) 기준으로 한 번 decorate.
                // 3×3 이웃 모두 readable, center만 writable.
                ChunkColumn[] viewGrid = new ChunkColumn[9];
                int[][] viewBiomeGrid = new int[9][];
                for (int i = 0; i < 9; i++) {
                    viewGrid[i] = grid[i].col;
                    viewBiomeGrid[i] = grid[i].biomeIds;
                }
                FeatureWorldView view = new FeatureWorldView(viewGrid, viewBiomeGrid, cx, cz, false, source.seed());
                Set<String> neighborBiomes = source.collectNeighborBiomes(cx, cz);

                // STRUCTURE_REFERENCES 상당: radius 8 내 모든 start 중 BoundingBox가 center 청크에 걸치는 것만 수집(F6).
                List<StructureStart> allStarts = new ArrayList<>();
                for (ProtoColumn p : grid) {
                    allStarts.addAll(p.starts);
                }
                for (CompletableFuture<List<StructureStart>> rf : ringFutures) {
                    allStarts.addAll(rf.join());
                }
                BoundingBox centerColumn = new BoundingBox(
                    cx * 16, Integer.MIN_VALUE / 2, cz * 16, cx * 16 + 15, Integer.MAX_VALUE / 2, cz * 16 + 15);
                List<StructureStart> referencedStarts = new ArrayList<>();
                for (StructureStart start : allStarts) {
                    if (start.boundingBox().intersects(centerColumn)) {
                        referencedStarts.add(start);
                    }
                }

                source.decorateOrigin(view, neighborBiomes, cx, cz, referencedStarts);

                grid[4].col.compactSections();
                grid[4].col.clearDirty();
                grid[4].status = GenStatus.FULL;
                return grid[4].col;
            });
    }
}
