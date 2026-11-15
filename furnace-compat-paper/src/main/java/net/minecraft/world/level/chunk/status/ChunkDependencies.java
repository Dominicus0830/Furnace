package net.minecraft.world.level.chunk.status;


/**
 * 반경별로 필요한 생성 단계(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 의 표 만들기 그대로(바닐라는 Guava ImmutableList 를 받지만 shim 은 java.util.List — 호환 모듈에 Guava 가 없다). 0 번 칸이 가장 높은 단계이고 멀어질수록 낮아진다.
 */
public final class ChunkDependencies {

    private final java.util.List<ChunkStatus> dependencyByRadius;
    private final int[] radiusByDependency;

    public ChunkDependencies(java.util.List<ChunkStatus> dependencyByRadius) {
        this.dependencyByRadius = dependencyByRadius;
        int size = dependencyByRadius.isEmpty() ? 0 : dependencyByRadius.get(0).getIndex() + 1;
        this.radiusByDependency = new int[size];
        for (int r = 0; r < dependencyByRadius.size(); r++) {
            int top = dependencyByRadius.get(r).getIndex();
            for (int s = 0; s <= top; s++) radiusByDependency[s] = r;
        }
    }

    public int getRadiusOf(ChunkStatus status) {
        int i = status.getIndex();
        if (i >= radiusByDependency.length) {
            throw new IllegalArgumentException("Requesting a ChunkStatus(" + status + ") outside of dependency range(" + dependencyByRadius + ")");
        }
        return radiusByDependency[i];
    }

    public int getRadius() { return Math.max(0, dependencyByRadius.size() - 1); }

    public int size() { return dependencyByRadius.size(); }

    public ChunkStatus get(int radius) { return dependencyByRadius.get(radius); }

    @Override
    public String toString() { return dependencyByRadius.toString(); }
}
