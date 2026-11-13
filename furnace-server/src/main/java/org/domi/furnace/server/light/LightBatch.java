// 빛 작업 한 묶음(T-064, 바닐라 ThreadedLevelLightEngine 의 빛 스레드 몫). region 스레드가 넘긴 블록 읽기 사본(얼린 구역)과
// 보이는 빛의 쓰기 사본 위에서 빛 스레드가 계산한다. 결과(복사해 고친 층·원천 높이·바뀐 섹션 표시)는 region 스레드가 넣는다.
package org.domi.furnace.server.light;

import java.util.ArrayList;
import java.util.List;

public final class LightBatch implements LightWorld {

    /** 이 묶음이 아는 청크 하나: 블록 읽기 사본, 보이는 빛, 그 쓰기 사본. */
    public static final class Column {
        public final int cx, cz;
        final LevelLight.StateReader blocks;
        public final ChunkLight visible;
        final ChunkLight copy;

        Column(int cx, int cz, LevelLight.StateReader blocks, ChunkLight visible) {
            this.cx = cx;
            this.cz = cz;
            this.blocks = blocks;
            this.visible = visible;
            this.copy = ChunkLight.writeCopyOf(visible);
        }
    }

    /** 바뀐 빛 섹션 표시를 받는 쪽(ChunkHolder.sectionLightChanged 와 같은 꼴 — 키 = sx<<32 | sz, {하늘빛, 블록빛} 마스크). */
    public interface Marks {
        void mark(long chunkKey, long skyMask, long blockMask);
    }

    private final int minY, maxY, unloadedState;
    private final VirtualLights virtualLights;
    private final LevelLight engine;

    // 청크 키 → Column (열린 주소법, 키 0 도 쓰므로 쓰임 표시를 따로 둔다)
    private long[] keys = new long[64];
    private Column[] vals = new Column[64];
    private int size;
    private final List<Column> columns = new ArrayList<>();

    // 새 청크 빛 켜기(lightChunk·attachPrecomputed): {cx, cz, 미리 계산했는가} — 넣은 순서대로
    private int[] chunkOps = new int[12];
    private int chunkOpCount;

    // 바뀐 섹션 표시(작업 안에서 모은다): 키 → {하늘빛, 블록빛}
    private final java.util.HashMap<Long, long[]> marks = new java.util.HashMap<>();
    private long initialChunk = Long.MIN_VALUE;
    private int lastX = Integer.MIN_VALUE, lastY, lastZ;
    private boolean lastSky;

    public LightBatch(int minY, int maxY, int unloadedState, boolean hasSkyLight, VirtualLights virtualLights) {
        this.minY = minY;
        this.maxY = maxY;
        this.unloadedState = unloadedState;
        this.virtualLights = virtualLights;
        this.engine = new LevelLight(this, hasSkyLight);
    }

    /** 이 묶음의 엔진 — region 의 미처리 일을 {@link LevelLight#moveWorkTo} 로 받는다. */
    public LevelLight engine() { return engine; }

    // ------------------------------------------------------------------ region 스레드: 묶음 채우기

    /** 빛이 있는 청크 하나를 이 묶음 세계에 넣는다(이미 있으면 무시). blocks 는 계산이 끝날 때까지 바뀌지 않아야 한다. */
    public boolean addColumn(int cx, int cz, LevelLight.StateReader blocks, ChunkLight visible) {
        long key = key(cx, cz);
        if (find(key) != null) return false;
        Column c = new Column(cx, cz, blocks, visible);
        put(key, c);
        columns.add(c);
        return true;
    }

    public boolean hasColumn(int cx, int cz) { return find(key(cx, cz)) != null; }

    /** 새로 올라온 청크의 빛 켜기를 넣는다(그 청크는 {@link #addColumn} 으로 이미 들어 있어야 한다). */
    public void addChunkOp(int cx, int cz, boolean precomputed) {
        if (chunkOpCount + 3 > chunkOps.length) chunkOps = java.util.Arrays.copyOf(chunkOps, chunkOps.length * 2);
        chunkOps[chunkOpCount++] = cx;
        chunkOps[chunkOpCount++] = cz;
        chunkOps[chunkOpCount++] = precomputed ? 1 : 0;
    }

    public List<Column> columns() { return columns; }

    // ------------------------------------------------------------------ 빛 스레드: 계산

    /**
     * 계산: 미룬 하늘빛 원천 고치기 → 새 청크 빛 켜기(넣은 순서, 바닐라 lightChunk 처럼 그 청크 자신의 변경은 표시하지 않는다) → 칸 검사·전파.
     * 블록을 하나씩 바꾸며 그 자리에서 계산한 것과 고정점(빛 값)이 같다.
     */
    public void run() {
        engine.applyPendingSources();
        for (int i = 0; i < chunkOpCount; i += 3) {
            int cx = chunkOps[i], cz = chunkOps[i + 1];
            initialChunk = (long) cx << 32 | (cz & 0xFFFFFFFFL);
            try {
                if (chunkOps[i + 2] != 0) engine.attachPrecomputed(cx, cz);
                else engine.lightChunk(cx, cz);
            } finally {
                initialChunk = Long.MIN_VALUE;
            }
        }
        engine.runUpdates();
    }

    // ------------------------------------------------------------------ region 스레드: 결과 넣기

    /** 계산이 끝난 뒤 region 스레드에서: 바꾼 빛을 보이는 쪽에 넣고 바뀐 섹션 표시를 넘긴다. */
    public void publish(Marks out) {
        for (Column c : columns) c.copy.publishTo(c.visible);
        for (var e : marks.entrySet()) out.mark(e.getKey(), e.getValue()[0], e.getValue()[1]);
    }

    /** 새 청크 빛 켜기 목록: {cx, cz, 미리 계산했는가(1/0)} 반복. */
    public int[] chunkOps() { return java.util.Arrays.copyOf(chunkOps, chunkOpCount); }

    // ------------------------------------------------------------------ LightWorld

    private Column col(int cx, int cz) { return find(key(cx, cz)); }

    @Override public ChunkLight light(int cx, int cz) {
        Column c = col(cx, cz);
        return c == null ? null : c.copy;
    }

    @Override public int state(int x, int y, int z) {
        if (y < minY || y >= maxY) return 12958;   // void_air
        return col(x >> 4, z >> 4).blocks.state(x, y, z);
    }

    @Override public int uniformSectionState(int cx, int sy, int cz) {
        Column c = col(cx, cz);
        return c == null ? -1 : c.blocks.uniformSectionState(sy);
    }

    @Override public boolean sectionMayEmit(int cx, int sy, int cz) {
        Column c = col(cx, cz);
        return c == null || c.blocks.sectionMayEmit(sy);
    }

    @Override public int unloadedState() { return unloadedState; }

    @Override public int minY() { return minY; }

    @Override public int maxY() { return maxY; }

    @Override public int extraEmission(int x, int y, int z) { return virtualLights == null ? 0 : virtualLights.emission(x, y, z); }

    /** ChunkLevel.LightWorldImpl.lightChanged + markLightChanged 와 같은 표시(같은 섹션 연속 기록 건너뛰기 포함). */
    @Override public void lightChanged(int x, int y, int z, boolean sky) {
        int x0 = (x - 1) >> 4, x1 = (x + 1) >> 4, y0 = (y - 1) >> 4, y1 = (y + 1) >> 4, z0 = (z - 1) >> 4, z1 = (z + 1) >> 4;
        if (x0 == x1 && y0 == y1 && z0 == z1) {
            if (x0 == lastX && y0 == lastY && z0 == lastZ && sky == lastSky) return;
            lastX = x0; lastY = y0; lastZ = z0; lastSky = sky;
        }
        int base = (minY >> 4) - 1, count = ((maxY - minY) >> 4) + 2;
        for (int sx = x0; sx <= x1; sx++) for (int sy = y0; sy <= y1; sy++) for (int sz = z0; sz <= z1; sz++) {
            long key = (long) sx << 32 | (sz & 0xFFFFFFFFL);
            if (initialChunk != Long.MIN_VALUE && initialChunk == key) continue;
            int idx = sy - base;
            if (idx < 0 || idx >= count) continue;
            marks.computeIfAbsent(key, k -> new long[2])[sky ? 0 : 1] |= 1L << idx;
        }
    }

    // ------------------------------------------------------------------ 청크 표

    private static long key(int cx, int cz) { return (long) cx << 32 | (cz & 0xFFFFFFFFL); }

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ h >>> 32) & mask;
    }

    private Column find(long key) {
        int mask = keys.length - 1;
        for (int i = slot(key, mask); ; i = i + 1 & mask) {
            Column c = vals[i];
            if (c == null) return null;
            if (keys[i] == key) return c;
        }
    }

    private void put(long key, Column c) {
        if ((size + 1) * 2 > keys.length) {
            long[] ok = keys;
            Column[] ov = vals;
            keys = new long[ok.length * 2];
            vals = new Column[ok.length * 2];
            for (int i = 0; i < ok.length; i++) if (ov[i] != null) insert(ok[i], ov[i]);
        }
        insert(key, c);
        size++;
    }

    private void insert(long key, Column c) {
        int mask = keys.length - 1;
        int i = slot(key, mask);
        while (vals[i] != null) i = i + 1 & mask;
        keys[i] = key;
        vals[i] = c;
    }
}
