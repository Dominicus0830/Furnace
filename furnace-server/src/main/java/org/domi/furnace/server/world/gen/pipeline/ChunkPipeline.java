package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

// 청크 단계별 파이프라인 오케스트레이터. 9-grid를 CARVERS까지 병렬 생성한다.
public final class ChunkPipeline {
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

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
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
                source.decorateOrigin(view, neighborBiomes, cx, cz);

                grid[4].col.compactSections();
                grid[4].col.clearDirty();
                grid[4].status = GenStatus.FULL;
                return grid[4].col;
            });
    }
}
