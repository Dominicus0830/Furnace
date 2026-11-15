package net.minecraft.world.level.chunk.status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 생성·불러오기 단계표(Mojang 이름 shim, N2b-6). 단계별 이웃 요구는 바닐라 1.21.1 그대로(생성: 구조물 시작 8, 바이옴 1, 지형 장식 앞 조각 1,
 * 빛 앞 빛 초기화 1, 스폰 앞 바이옴 1 / 불러오기: 빛 앞 빛 초기화 1). 생성 작업은 없다.
 */
public record ChunkPyramid(java.util.List<ChunkStep> steps) {

    public static final ChunkPyramid GENERATION_PYRAMID = new Builder()
            .step(ChunkStatus.EMPTY, s -> s)
            .step(ChunkStatus.STRUCTURE_STARTS, s -> s)
            .step(ChunkStatus.STRUCTURE_REFERENCES, s -> s.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8))
            .step(ChunkStatus.BIOMES, s -> s.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8))
            .step(ChunkStatus.NOISE, s -> s.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).addRequirement(ChunkStatus.BIOMES, 1).blockStateWriteRadius(0))
            .step(ChunkStatus.SURFACE, s -> s.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).addRequirement(ChunkStatus.BIOMES, 1).blockStateWriteRadius(0))
            .step(ChunkStatus.CARVERS, s -> s.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).blockStateWriteRadius(0))
            .step(ChunkStatus.FEATURES, s -> s.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).addRequirement(ChunkStatus.CARVERS, 1).blockStateWriteRadius(1))
            .step(ChunkStatus.INITIALIZE_LIGHT, s -> s)
            .step(ChunkStatus.LIGHT, s -> s.addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1))
            .step(ChunkStatus.SPAWN, s -> s.addRequirement(ChunkStatus.BIOMES, 1))
            .step(ChunkStatus.FULL, s -> s)
            .build();

    public static final ChunkPyramid LOADING_PYRAMID = new Builder()
            .step(ChunkStatus.EMPTY, s -> s)
            .step(ChunkStatus.STRUCTURE_STARTS, s -> s)
            .step(ChunkStatus.STRUCTURE_REFERENCES, s -> s)
            .step(ChunkStatus.BIOMES, s -> s)
            .step(ChunkStatus.NOISE, s -> s)
            .step(ChunkStatus.SURFACE, s -> s)
            .step(ChunkStatus.CARVERS, s -> s)
            .step(ChunkStatus.FEATURES, s -> s)
            .step(ChunkStatus.INITIALIZE_LIGHT, s -> s)
            .step(ChunkStatus.LIGHT, s -> s.addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1))
            .step(ChunkStatus.SPAWN, s -> s)
            .step(ChunkStatus.FULL, s -> s)
            .build();

    public ChunkStep getStepTo(ChunkStatus status) { return steps.get(status.getIndex()); }

    /** 바닐라 ChunkPyramid.Builder. */
    public static final class Builder {
        private final List<ChunkStep> steps = new ArrayList<>();

        public ChunkPyramid build() { return new ChunkPyramid(java.util.List.copyOf(steps)); }

        public Builder step(ChunkStatus status, UnaryOperator<ChunkStep.Builder> f) {
            ChunkStep parent = steps.isEmpty() ? null : steps.get(steps.size() - 1);
            steps.add(f.apply(new ChunkStep.Builder(status, parent)).build());
            return this;
        }
    }
}
