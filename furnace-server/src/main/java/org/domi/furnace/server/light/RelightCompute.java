// 청크 빛 다시 계산 한 덩이(T-197 relight, Paper Starlight relightChunks 의 일꾼 몫). region 스레드가 넘긴 얼린 블록만 읽고, 그 청크들을
// 빈 빛에서 처음부터 켠 세계를 만든다. 결과 중 relight 대상 청크의 빛만 region 스레드가 끼운다(둘레 청크는 빛이 그 청크로 번지는 세계로만 쓴다).
// 대상 청크의 둘레 1칸이 다 들어 있으면 그 청크의 값은 "지금 블록에 대한 올바른 빛"이다 — 빛은 15칸 안에서만 퍼진다(설계 4.2).
package org.domi.furnace.server.light;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.StateIds;

public final class RelightCompute implements LightWorld {

    /** 이 세계의 청크 하나: 얼린 블록과 새 빛. */
    public static final class Column {
        public final int cx, cz;
        final LevelLight.StateReader blocks;
        final ChunkLight light = new ChunkLight();

        Column(int cx, int cz, LevelLight.StateReader blocks) {
            this.cx = cx;
            this.cz = cz;
            this.blocks = blocks;
        }
    }

    private final int minY, maxY, unloadedState;
    private final boolean hasSkyLight;
    private final VirtualLights virtualLights;

    // 청크 키 → Column(열린 주소법). 빛 계산은 칸마다 청크를 찾으므로 해시 맵보다 싸게, 마지막으로 찾은 것을 하나 기억한다.
    private long[] keys = new long[64];
    private Column[] vals = new Column[64];
    private int size;
    private final List<Column> columns = new ArrayList<>();
    private long lastKey = Long.MIN_VALUE;
    private Column last;

    public RelightCompute(int minY, int maxY, int unloadedState, boolean hasSkyLight, VirtualLights virtualLights) {
        this.minY = minY;
        this.maxY = maxY;
        this.unloadedState = unloadedState;
        this.hasSkyLight = hasSkyLight;
        this.virtualLights = virtualLights;
    }

    /** 청크 하나를 이 세계에 넣는다(이미 있으면 무시). blocks 는 계산이 끝날 때까지 바뀌지 않아야 한다. region 스레드에서, run 전에. */
    public void addColumn(int cx, int cz, LevelLight.StateReader blocks) {
        long key = key(cx, cz);
        if (find(key) != null) return;
        Column c = new Column(cx, cz, blocks);
        put(key, c);
        columns.add(c);
    }

    public boolean hasColumn(int cx, int cz) { return find(key(cx, cz)) != null; }

    /** 넣은 청크를 모두 빈 빛에서 켠다(넣은 순서 — 켜는 순서는 고정점을 바꾸지 않는다). 일꾼 스레드에서 한 번. */
    public void run() {
        LevelLight engine = new LevelLight(this, hasSkyLight);
        for (Column c : columns) engine.lightChunk(c.cx, c.cz);
        // 가짜 광원(플러그인 빛 API)은 청크 켜기의 원천 훑기에 없다 — 보이는 세계처럼 켠 뒤 검사로 건다(ChunkLevel.reapplyVirtualLights)
        if (virtualLights != null && !virtualLights.isEmpty()) {
            boolean any = false;
            for (Column c : columns) {
                for (long p : virtualLights.sourcesIn(c.cx, c.cz)) {
                    engine.emissionChanged(LightEngine.px(p), LightEngine.py(p), LightEngine.pz(p));
                    any = true;
                }
            }
            if (any) engine.runUpdates();
        }
    }

    /** run 뒤: 그 청크의 새 빛(균일 층은 공유 상수로 줄였다). 부른 쪽이 가져간다 — 이 객체는 더 쓰지 않는다. */
    public ChunkLight result(int cx, int cz) {
        Column c = find(key(cx, cz));
        if (c == null) return null;
        c.light.compact();
        return c.light;
    }

    // ------------------------------------------------------------------ LightWorld

    private Column col(int cx, int cz) {
        long k = key(cx, cz);
        if (k == lastKey) return last;
        Column c = find(k);
        lastKey = k;
        last = c;
        return c;
    }

    @Override public ChunkLight light(int cx, int cz) {
        Column c = col(cx, cz);
        return c == null ? null : c.light;
    }

    @Override public int state(int x, int y, int z) {
        if (y < minY || y >= maxY) return StateIds.VOID_AIR;
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

    @Override public boolean sectionHasOnlyAir(int cx, int sy, int cz) {
        Column c = col(cx, cz);
        return c == null ? LightWorld.super.sectionHasOnlyAir(cx, sy, cz) : c.blocks.sectionHasOnlyAir(cx, sy, cz);
    }

    @Override public int unloadedState() { return unloadedState; }

    @Override public int minY() { return minY; }

    @Override public int maxY() { return maxY; }

    @Override public int extraEmission(int x, int y, int z) { return virtualLights == null ? 0 : virtualLights.emission(x, y, z); }

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
