// 바닐라 LightEngine(블록빛·하늘빛 공통): 칸 검사 대기열 → 감소 전파 → 증가 전파. 큐 항목은 (칸, 플래그)이며
// 플래그는 하위 4비트가 밝기, 비트 4..9 가 방향별 전파 여부, 비트 10 이 "빈 모양에서 왔다", 비트 11 이 "발광에서 시작"이다.
package org.domi.furnace.server.light;

public abstract class LightEngine {
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    protected static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};

    static final long ALL_DIRS = 1008L, EMPTY_SHAPE_FLAG = 1024L, FROM_EMISSION_FLAG = 2048L;

    // ------------------------------------------------------------------ 큐 항목 플래그(LightEngine.QueueEntry)

    static long decreaseAllDirections(int level) { return withLevel(ALL_DIRS, level); }

    static long decreaseSkipOneDirection(int level, int dir) { return withLevel(ALL_DIRS & ~(1L << dir + 4), level); }

    static long increaseLightFromEmission(int level, boolean isEmptyShape) {
        long f = ALL_DIRS | FROM_EMISSION_FLAG;
        if (isEmptyShape) f |= EMPTY_SHAPE_FLAG;
        return withLevel(f, level);
    }

    static long increaseSkipOneDirection(int level, boolean isEmptyShape, int dir) {
        long f = ALL_DIRS & ~(1L << dir + 4);
        if (isEmptyShape) f |= EMPTY_SHAPE_FLAG;
        return withLevel(f, level);
    }

    static long increaseOnlyOneDirection(int level, boolean isEmptyShape, int dir) {
        long f = 0L;
        if (isEmptyShape) f |= EMPTY_SHAPE_FLAG;
        f |= 1L << dir + 4;
        return withLevel(f, level);
    }

    static long increaseSkySourceInDirections(boolean down, boolean north, boolean south, boolean west, boolean east) {
        long f = withLevel(0L, 15);
        if (down) f |= 1L << DOWN + 4;
        if (north) f |= 1L << NORTH + 4;
        if (south) f |= 1L << SOUTH + 4;
        if (west) f |= 1L << WEST + 4;
        if (east) f |= 1L << EAST + 4;
        return f;
    }

    static int fromLevel(long f) { return (int) (f & 15L); }

    static boolean isFromEmptyShape(long f) { return (f & EMPTY_SHAPE_FLAG) != 0L; }

    static boolean isIncreaseFromEmission(long f) { return (f & FROM_EMISSION_FLAG) != 0L; }

    static boolean shouldPropagateInDirection(long f, int dir) { return (f & 1L << dir + 4) != 0L; }

    private static long withLevel(long f, int level) { return f & -16L | level & 15L; }

    protected static final long CHECK_NODE = decreaseAllDirections(1);

    // ------------------------------------------------------------------ 칸 좌표 묶기(BlockPos.asLong)

    public static long pack(int x, int y, int z) { return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF; }

    public static int px(long p) { return (int) (p >> 38); }

    public static int py(long p) { return (int) (p << 52 >> 52); }

    public static int pz(long p) { return (int) (p << 26 >> 38); }

    static long offset(long p, int dir) { return pack(px(p) + DX[dir], py(p) + DY[dir], pz(p) + DZ[dir]); }

    // ------------------------------------------------------------------ 상태

    protected final LightWorld world;
    /** 검사할 칸(넣은 순서 유지, 중복 없음 — 옛 LinkedHashSet<Long> 과 같은 순서, 박싱 없음). */
    private final LongLinkedSet checks = new LongLinkedSet();
    protected final LongQueue increase = new LongQueue(), decrease = new LongQueue();

    protected LightEngine(LightWorld world) { this.world = world; }

    /** 그 칸의 블록 상태(안 올라온 청크는 기반암). */
    protected int stateAt(long pos) {
        int x = px(pos), y = py(pos), z = pz(pos);
        ChunkLight cl = world.light(x >> 4, z >> 4);
        return cl == null ? world.unloadedState() : world.state(x, y, z);
    }

    /** 그 칸이 빛을 저장하는 섹션에 있는가(올라온 청크이고 높이가 범위 안). */
    protected boolean storing(long pos) {
        int y = py(pos);
        return y >= ChunkLight.MIN_Y && y < ChunkLight.MAX_Y && world.light(px(pos) >> 4, pz(pos) >> 4) != null;
    }

    /** 이 엔진의 층(읽기용 — 공유 상수일 수 있다). */
    protected abstract byte[] layer(ChunkLight cl, int section);

    /** 이 엔진의 층(쓰기용 — 공유면 복사). */
    protected abstract byte[] writableLayer(ChunkLight cl, int section);

    protected abstract boolean isSky();

    protected int level(long pos) {
        int x = px(pos), y = py(pos), z = pz(pos);
        ChunkLight cl = world.light(x >> 4, z >> 4);
        return ChunkLight.get(layer(cl, ChunkLight.sectionIndex(y)), x, y, z);
    }

    protected void setLevel(long pos, int v) {
        int x = px(pos), y = py(pos), z = pz(pos);
        ChunkLight cl = world.light(x >> 4, z >> 4);
        ChunkLight.set(writableLayer(cl, ChunkLight.sectionIndex(y)), x, y, z, v);
        world.lightChanged(x, y, z, isSky());
    }

    /**
     * 감소 전파가 안 올라온 청크 칸에서 멈췄다 — 그 청크의 디스크 빛에는 지금 지운 빛이 남아 있을 수 있다(바닐라는 이웃이 늘 올라와 있어
     * 이런 일이 없다). 세계에 알려 그 청크를 다음에 올릴 때 저장 빛을 믿지 않게 한다. 높이 밖이면 아무것도 안 한다.
     */
    protected void decreaseBlocked(long np) {
        int y = py(np);
        if (y < ChunkLight.MIN_Y || y >= ChunkLight.MAX_Y) return;
        world.decreaseBlocked(px(np), y, pz(np));
    }

    protected static int opacity(int state) { return Math.max(1, LightTables.lightBlock(state)); }

    protected boolean shapeOccludes(int fromState, int toState, int dir) { return LightTables.shapeOccludes(fromState, toState, dir); }

    public void checkBlock(long pos) { checks.add(pos); }

    /**
     * 아직 처리하지 않은 칸 검사 중 {@code sel} 에 맞는 것을 순서를 지켜 target 으로 옮긴다(region 합치기·나누기 — 틱 사이에 놓인 블록의 검사가
     * 사라지지 않게). 증가·감소 대기열은 runUpdates 안에서만 차므로 옮길 것이 없다.
     */
    void moveChecksTo(LightEngine target, java.util.function.LongPredicate sel) {
        if (checks.isEmpty()) return;
        // 모두 꺼내 순서대로 나눈다 — 남는 것도 옮기는 것도 넣은 순서 그대로(LinkedHashSet 반복자 제거와 같은 결과)
        for (long p : checks.drain()) {
            if (sel.test(p)) target.checks.add(p);
            else checks.add(p);
        }
    }

    public boolean hasWork() { return !checks.isEmpty() || !increase.isEmpty() || !decrease.isEmpty(); }

    /** runLightUpdates: 검사 → 감소 전파 → 증가 전파. */
    public int runUpdates() {
        if (!checks.isEmpty()) {
            long[] order = checks.drain();   // 옛 toArray → checkNode → clear 와 같은 순서(checkNode 는 검사를 더 넣지 않는다)
            for (long p : order) checkNode(p);
        }
        int n = 0;
        while (!decrease.isEmpty()) {
            long pos = decrease.dequeue(), flags = decrease.dequeue();
            propagateDecrease(pos, flags);
            n++;
        }
        while (!increase.isEmpty()) {
            long pos = increase.dequeue(), flags = increase.dequeue();
            int stored = level(pos);
            int lvl = fromLevel(flags);
            if (isIncreaseFromEmission(flags) && stored < lvl) {
                setLevel(pos, lvl);
                stored = lvl;
            }
            if (stored == lvl) propagateIncrease(pos, flags, stored);
            n++;
        }
        return n;
    }

    /**
     * 이웃 청크가 새로 켜질 때: 이미 있는 이쪽 가장자리 칸의 빛을 그 방향으로 한 번 밀어 넣는다(바닐라는 나중에 같은 고정점에 이른다).
     * 건너편 칸이 이미 level-1 이상이면 전파가 아무것도 바꾸지 못하므로(propagateIncrease 의 첫 검사) 넣지 않는다 — 결과는 같다.
     */
    public void pushEdge(int cx, int cz, int towardDir) {
        ChunkLight cl = world.light(cx, cz);
        if (cl == null) return;
        int ncx = cx + DX[towardDir], ncz = cz + DZ[towardDir];
        ChunkLight other = world.light(ncx, ncz);
        if (other == null) return;   // 건너편 청크가 없으면 번질 곳이 없다
        int fixedX = towardDir == WEST ? 0 : towardDir == EAST ? 15 : -1, fixedZ = towardDir == NORTH ? 0 : towardDir == SOUTH ? 15 : -1;
        for (int i = 0; i < 16; i++) {
            int lx = fixedX >= 0 ? fixedX : i, lz = fixedZ >= 0 ? fixedZ : i;
            int nlx = towardDir == WEST ? 15 : towardDir == EAST ? 0 : lx, nlz = towardDir == NORTH ? 15 : towardDir == SOUTH ? 0 : lz;
            for (int sec = 0; sec < ChunkLight.SECTIONS; sec++) {
                byte[] layer = layer(cl, sec), nlayer = layer(other, sec);
                // 내 층이 모두 0(넣을 칸 없음)이거나 건너편 층이 모두 15(lvl - 1 <= 15)면 이 구역은 아래 칸마다 continue 뿐이다 — 통째로 건너뛴다
                if (ChunkLight.isKnownZero(layer) || ChunkLight.isKnownFull(nlayer)) continue;
                for (int ly = 0; ly < 16; ly++) {
                    int idx = ly << 8 | lz << 4 | lx, nidx = ly << 8 | nlz << 4 | nlx;
                    int b = layer[idx >> 1];
                    int lvl = (idx & 1) == 0 ? b & 15 : b >> 4 & 15;
                    if (lvl <= 1) continue;
                    int nb = nlayer[nidx >> 1];
                    int nl = (nidx & 1) == 0 ? nb & 15 : nb >> 4 & 15;
                    if (lvl - 1 <= nl) continue;
                    int y = ChunkLight.MIN_Y + sec * 16 + ly;
                    enqueueIncrease(pack(cx * 16 + lx, y, cz * 16 + lz), increaseOnlyOneDirection(lvl, false, towardDir));
                }
            }
        }
    }

    protected void enqueueDecrease(long pos, long flags) {
        decrease.enqueue(pos);
        decrease.enqueue(flags);
    }

    protected void enqueueIncrease(long pos, long flags) {
        increase.enqueue(pos);
        increase.enqueue(flags);
    }

    protected abstract void checkNode(long pos);

    protected abstract void propagateIncrease(long pos, long flags, int level);

    protected abstract void propagateDecrease(long pos, long flags);

    /** 단순한 long 큐(길이는 2의 거듭제곱 — 나머지 대신 마스크). */
    public static final class LongQueue {
        private long[] a = new long[1024];
        private int head, size;

        public boolean isEmpty() { return size == 0; }

        public void enqueue(long v) {
            if (size == a.length) {
                long[] n = new long[a.length * 2];
                int mask = a.length - 1;
                for (int i = 0; i < size; i++) n[i] = a[(head + i) & mask];
                a = n;
                head = 0;
            }
            a[(head + size) & (a.length - 1)] = v;
            size++;
        }

        public long dequeue() {
            long v = a[head];
            head = (head + 1) & (a.length - 1);
            size--;
            return v;
        }
    }

    /** 넣은 순서를 지키는 long 집합(중복 무시). 열린 주소 해시 + 순서 배열. */
    static final class LongLinkedSet {
        private static final long EMPTY = Long.MIN_VALUE;   // 빈 칸 표시. 이 값 자체도 키가 될 수 있어(x = -2^25) hasEmptyKey 로 따로 센다
        private long[] table = newTable(64);
        private long[] order = new long[64];
        private int size;
        private boolean hasEmptyKey;

        private static long[] newTable(int n) {
            long[] t = new long[n];
            java.util.Arrays.fill(t, EMPTY);
            return t;
        }

        boolean isEmpty() { return size == 0; }

        int size() { return size; }

        private static int mix(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ h >>> 32);
        }

        /** 새로 넣었으면 true. */
        boolean add(long k) {
            if (k == EMPTY) {
                if (hasEmptyKey) return false;
                hasEmptyKey = true;
            } else {
                int mask = table.length - 1;
                int i = mix(k) & mask;
                long t;
                while ((t = table[i]) != EMPTY) {
                    if (t == k) return false;
                    i = i + 1 & mask;
                }
                table[i] = k;
            }
            if (size == order.length) order = java.util.Arrays.copyOf(order, size * 2);
            order[size++] = k;
            if (size * 2 > table.length) rehash();
            return true;
        }

        private void rehash() {
            long[] t = newTable(table.length * 2);
            int mask = t.length - 1;
            for (int n = 0; n < size; n++) {
                long k = order[n];
                if (k == EMPTY) continue;
                int i = mix(k) & mask;
                while (t[i] != EMPTY) i = i + 1 & mask;
                t[i] = k;
            }
            table = t;
        }

        /** 넣은 순서대로 꺼내고 비운다. 큰 표는 줄여 둔다. */
        long[] drain() {
            long[] out = java.util.Arrays.copyOf(order, size);
            if (table.length > 4096) {
                table = newTable(64);
                order = new long[64];
            } else {
                java.util.Arrays.fill(table, EMPTY);
            }
            size = 0;
            hasEmptyKey = false;
            return out;
        }
    }
}
