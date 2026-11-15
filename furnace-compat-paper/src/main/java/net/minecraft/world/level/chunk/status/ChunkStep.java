package net.minecraft.world.level.chunk.status;

import java.util.Arrays;

/**
 * 생성 단계 하나의 이웃 요구(Mojang 이름 shim, N2b-6). 바닐라 1.21.1 의 직접·누적 요구 계산 그대로이고, 생성 작업 자체는 없다(Furnace 생성기는 서버 안에 있다).
 * 플러그인이 {@code ChunkPyramid.LOADING_PYRAMID.getStepTo(FULL).getAccumulatedRadiusOf(LIGHT)} 처럼 티켓 수준을 셈할 때 쓴다(FAWE 빛 티켓).
 */
public final class ChunkStep {

    private final ChunkStatus targetStatus;
    private final ChunkDependencies directDependencies;
    private final ChunkDependencies accumulatedDependencies;
    private final int blockStateWriteRadius;

    private ChunkStep(ChunkStatus targetStatus, ChunkDependencies direct, ChunkDependencies accumulated, int blockStateWriteRadius) {
        this.targetStatus = targetStatus;
        this.directDependencies = direct;
        this.accumulatedDependencies = accumulated;
        this.blockStateWriteRadius = blockStateWriteRadius;
    }

    public int getAccumulatedRadiusOf(ChunkStatus status) {
        return status == targetStatus ? 0 : accumulatedDependencies.getRadiusOf(status);
    }

    public ChunkStatus targetStatus() { return targetStatus; }

    public ChunkDependencies directDependencies() { return directDependencies; }

    public ChunkDependencies accumulatedDependencies() { return accumulatedDependencies; }

    public int blockStateWriteRadius() { return blockStateWriteRadius; }

    @Override
    public String toString() { return "ChunkStep[" + targetStatus + "]"; }

    /** 바닐라 ChunkStep.Builder — 요구 추가와 누적 계산. */
    public static final class Builder {
        final ChunkStatus status;
        final ChunkStep parent;
        ChunkStatus[] directDependenciesByRadius;
        int blockStateWriteRadius = -1;

        Builder(ChunkStatus status, ChunkStep parent) {
            this.status = status;
            this.parent = parent;
            if (parent != null) {
                if (status.getParent() != parent.targetStatus()) throw new IllegalArgumentException("Status " + status + " should come after " + parent.targetStatus());
                this.directDependenciesByRadius = new ChunkStatus[] {parent.targetStatus()};
            } else {
                this.directDependenciesByRadius = new ChunkStatus[0];
            }
        }

        public Builder addRequirement(ChunkStatus required, int radius) {
            if (required.isOrAfter(status)) throw new IllegalArgumentException("Status " + required + " can not be required by " + status);
            ChunkStatus[] old = directDependenciesByRadius;
            int n = radius + 1;
            if (n > old.length) {
                directDependenciesByRadius = new ChunkStatus[n];
                Arrays.fill(directDependenciesByRadius, required);
            }
            for (int i = 0; i < Math.min(n, old.length); i++) directDependenciesByRadius[i] = ChunkStatus.max(old[i], required);
            return this;
        }

        public Builder blockStateWriteRadius(int radius) {
            this.blockStateWriteRadius = radius;
            return this;
        }

        public ChunkStep build() {
            return new ChunkStep(status, new ChunkDependencies(java.util.List.of(directDependenciesByRadius)),
                    new ChunkDependencies(java.util.List.of(buildAccumulated())), blockStateWriteRadius);
        }

        private ChunkStatus[] buildAccumulated() {
            if (parent == null) return directDependenciesByRadius;
            int shift = radiusOfParent(parent.targetStatus());
            ChunkDependencies p = parent.accumulatedDependencies();
            ChunkStatus[] out = new ChunkStatus[Math.max(shift + p.size(), directDependenciesByRadius.length)];
            for (int r = 0; r < out.length; r++) {
                int k = r - shift;
                if (k < 0 || k >= p.size()) out[r] = directDependenciesByRadius[r];
                else if (r >= directDependenciesByRadius.length) out[r] = p.get(k);
                else out[r] = ChunkStatus.max(directDependenciesByRadius[r], p.get(k));
            }
            return out;
        }

        private int radiusOfParent(ChunkStatus s) {
            for (int r = directDependenciesByRadius.length - 1; r >= 0; r--) if (directDependenciesByRadius[r].isOrAfter(s)) return r;
            return 0;
        }
    }
}
