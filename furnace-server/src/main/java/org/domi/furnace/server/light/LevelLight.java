// 한 차원의 빛 엔진 묶음(바닐라 LevelLightEngine): 블록빛 + (하늘이 있으면) 하늘빛. 청크 켜기·블록 변경·갱신 실행.
package org.domi.furnace.server.light;

public final class LevelLight {
    private final LightWorld world;
    private final BlockLightEngine block;
    private final SkyLightEngine sky;

    public LevelLight(LightWorld world, boolean hasSkyLight) {
        this.world = world;
        this.block = new BlockLightEngine(world);
        this.sky = hasSkyLight ? new SkyLightEngine(world) : null;
    }

    /** 이웃 쪽(이 청크에서 본 방향)과 서로 이어지는 가장자리 방향 순서: 북 남 서 동. */
    private static final int[] SIDES = {LightEngine.NORTH, LightEngine.SOUTH, LightEngine.WEST, LightEngine.EAST};
    private static final int[] DCX = {0, 0, -1, 1}, DCZ = {-1, 1, 0, 0};

    /** 이 청크의 빛을 처음 만든다(ChunkLight 는 world 에 이미 올라와 있어야 한다). 이웃의 빛이 이쪽으로 번지게 가장자리도 민다. */
    public void lightChunk(int cx, int cz) {
        world.begin();
        try {
            lightChunk0(cx, cz);
        } finally {
            world.end();
        }
    }

    private void lightChunk0(int cx, int cz) {
        ChunkLight cl = world.light(cx, cz);
        cl.lightEnabled = true;
        if (sky != null) {
            sky.computeSources(cx, cz);
            sky.propagateLightSources(cx, cz);
        }
        block.propagateLightSources(cx, cz);
        for (int i = 0; i < 4; i++) {
            int nx = cx + DCX[i], nz = cz + DCZ[i];
            ChunkLight n = world.light(nx, nz);
            if (n == null || !n.lightEnabled) continue;
            int toward = SIDES[i] ^ 1;   // 이웃에서 이 청크 쪽 방향
            block.pushEdge(nx, nz, toward);
            if (sky != null) sky.pushEdge(nx, nz, toward);
        }
        runUpdates();
    }

    /** 청크 하나의 블록 상태를 읽는 함수(월드 좌표). 구역 힌트 두 개는 빠른 경로용({@link LightWorld} 의 같은 이름 참고). */
    public interface StateReader {
        int state(int x, int y, int z);

        default int uniformSectionState(int sectionY) { return -1; }

        default boolean sectionMayEmit(int sectionY) { return true; }
    }

    /**
     * 그 청크 하나만 있는 세계에서 빛을 미리 계산한다(생성 스레드용 — 월드 상태를 건드리지 않는다). 이웃은 없는 것으로 보므로
     * 바깥으로 번지는 빛과 바깥에서 들어오는 빛은 빠져 있다 — 올릴 때 {@link #attachPrecomputed} 로 가장자리를 맞춘다.
     */
    public static ChunkLight computeIsolated(int cx, int cz, StateReader blocks, int bedrockState, int minY, int maxY, boolean hasSkyLight) {
        ChunkLight own = new ChunkLight();
        LightWorld w = new LightWorld() {
            public ChunkLight light(int x, int z) { return x == cx && z == cz ? own : null; }
            public int state(int x, int y, int z) { return y < minY || y >= maxY ? 12958 : blocks.state(x, y, z); }
            public int unloadedState() { return bedrockState; }
            public int uniformSectionState(int x, int sy, int z) { return x == cx && z == cz ? blocks.uniformSectionState(sy) : -1; }
            public boolean sectionMayEmit(int x, int sy, int z) { return x != cx || z != cz || blocks.sectionMayEmit(sy); }
            public int minY() { return minY; }
            public int maxY() { return maxY; }
        };
        new LevelLight(w, hasSkyLight).lightChunk(cx, cz);
        return own;
    }

    /** 미리 계산한 빛이 올라온 청크를 이웃과 잇는다: 양쪽 가장자리를 서로 민다(이미 켜진 이웃만). */
    public void attachPrecomputed(int cx, int cz) {
        world.begin();
        try {
            attachPrecomputed0(cx, cz);
        } finally {
            world.end();
        }
    }

    private void attachPrecomputed0(int cx, int cz) {
        ChunkLight cl = world.light(cx, cz);
        cl.lightEnabled = true;
        for (int i = 0; i < 4; i++) {
            int nx = cx + DCX[i], nz = cz + DCZ[i];
            ChunkLight n = world.light(nx, nz);
            if (n == null || !n.lightEnabled) continue;
            int toward = SIDES[i] ^ 1;   // 이웃에서 이 청크 쪽 방향
            block.pushEdge(nx, nz, toward);
            block.pushEdge(cx, cz, SIDES[i]);
            if (sky != null) {
                sky.pushEdge(nx, nz, toward);
                sky.pushEdge(cx, cz, SIDES[i]);
            }
        }
        runUpdates();
    }

    // 빛 작업 모드(T-064): 하늘빛 원천 고치기를 블록이 바뀐 자리에서 하지 않고 칸 좌표만 모아 두었다가 빛 작업이 순서대로 한다
    // (원천 높이 배열은 빛 작업이 쓰는 동안 region 이 건드리면 안 된다). 끈 동안은 늘 비어 있다.
    private boolean deferSources;
    private long[] pendingSources = new long[16];
    private int pendingSourceCount;

    /** 켜면 이후 blockChanged 의 하늘빛 원천 고치기를 미룬다(다음 runUpdates·applyPendingSources 가 순서대로 한다). */
    public void deferSources(boolean on) { deferSources = on; }

    /** 블록이 바뀐 뒤(LevelChunk.setBlockState): 하늘빛 원천을 고치고 두 엔진에 검사를 건다. 실제 계산은 runUpdates. */
    public void blockChanged(int x, int y, int z) {
        long p = LightEngine.pack(x, y, z);
        if (sky != null) {
            if (deferSources) {
                if (pendingSourceCount == pendingSources.length) pendingSources = java.util.Arrays.copyOf(pendingSources, pendingSourceCount * 2);
                pendingSources[pendingSourceCount++] = p;
            } else {
                sky.updateSource(x, y, z);
            }
        }
        block.checkBlock(p);
        if (sky != null) sky.checkBlock(p);
    }

    /** 가짜 광원이 바뀌었거나 다시 걸 때: 블록빛만 그 칸을 다시 본다(하늘빛 원천은 블록이 그대로라 같다). 계산은 runUpdates. */
    public void emissionChanged(int x, int y, int z) { block.checkBlock(LightEngine.pack(x, y, z)); }

    /** 아직 처리하지 않은 칸 검사(블록빛·하늘빛)를 sel(칸 묶음 좌표)에 맞는 것만 target 으로 옮긴다 — region 합치기·나누기용. */
    public void moveChecksTo(LevelLight target, java.util.function.LongPredicate sel) {
        block.moveChecksTo(target.block, sel);
        if (sky != null && target.sky != null) sky.moveChecksTo(target.sky, sel);
    }

    public boolean hasWork() { return pendingSourceCount > 0 || block.hasWork() || sky != null && sky.hasWork(); }

    /** 미뤄 둔 하늘빛 원천 고치기를 넣은 순서대로 한다(블록 상태는 지금 것). */
    public void applyPendingSources() {
        if (pendingSourceCount == 0) return;
        world.begin();
        try {
            applyPendingSources0();
        } finally {
            world.end();
        }
    }

    private void applyPendingSources0() {
        int n = pendingSourceCount;
        pendingSourceCount = 0;
        for (int i = 0; i < n; i++) {
            long p = pendingSources[i];
            sky.updateSource(LightEngine.px(p), LightEngine.py(p), LightEngine.pz(p));
        }
    }

    /** 미처리 일(미룬 원천 고치기·칸 검사)을 순서를 지켜 통째로 target 으로 넘긴다 — 빛 작업에 한 묶음을 넘길 때. */
    public void moveWorkTo(LevelLight target) {
        for (int i = 0; i < pendingSourceCount; i++) {
            if (target.pendingSourceCount == target.pendingSources.length) {
                target.pendingSources = java.util.Arrays.copyOf(target.pendingSources, target.pendingSourceCount * 2);
            }
            target.pendingSources[target.pendingSourceCount++] = pendingSources[i];
        }
        pendingSourceCount = 0;
        moveChecksTo(target, p -> true);
    }

    /** 미처리 일이 걸린 칸 좌표(묶은 값)를 모두 본다 — 원천 고치기, 블록빛 검사, 하늘빛 검사 순(겹칠 수 있다). */
    public void forEachPendingPos(java.util.function.LongConsumer c) {
        for (int i = 0; i < pendingSourceCount; i++) c.accept(pendingSources[i]);
        block.forEachCheck(c);
        if (sky != null) sky.forEachCheck(c);
    }

    public void runUpdates() {
        world.begin();
        try {
            runUpdates0();
        } finally {
            world.end();
        }
    }

    private void runUpdates0() {
        if (pendingSourceCount > 0) applyPendingSources0();
        if (sky != null) sky.runUpdates();
        block.runUpdates();
    }

    public int skyLight(int x, int y, int z) {
        ChunkLight cl = world.light(x >> 4, z >> 4);
        if (cl == null || y < ChunkLight.MIN_Y || y >= ChunkLight.MAX_Y) return 0;
        return cl.skyAt(x, y, z);
    }

    public int blockLight(int x, int y, int z) {
        ChunkLight cl = world.light(x >> 4, z >> 4);
        if (cl == null || y < ChunkLight.MIN_Y || y >= ChunkLight.MAX_Y) return 0;
        return cl.blockAt(x, y, z);
    }
}
