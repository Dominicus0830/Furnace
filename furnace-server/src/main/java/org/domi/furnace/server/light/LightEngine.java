// 바닐라 LightEngine(블록빛·하늘빛 공통): 칸 검사 대기열 → 감소 전파 → 증가 전파. 큐 항목은 (칸, 플래그)이며
// 플래그는 하위 4비트가 밝기, 비트 4..9 가 방향별 전파 여부, 비트 10 이 "빈 모양에서 왔다", 비트 11 이 "발광에서 시작"이다.
package org.domi.furnace.server.light;

import java.util.LinkedHashSet;

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
    private final LinkedHashSet<Long> checks = new LinkedHashSet<>();
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

    protected abstract byte[][] layers(ChunkLight cl);

    protected int level(long pos) {
        int x = px(pos), y = py(pos), z = pz(pos);
        ChunkLight cl = world.light(x >> 4, z >> 4);
        return ChunkLight.get(layers(cl)[ChunkLight.sectionIndex(y)], x, y, z);
    }

    protected void setLevel(long pos, int v) {
        int x = px(pos), y = py(pos), z = pz(pos);
        ChunkLight cl = world.light(x >> 4, z >> 4);
        ChunkLight.set(layers(cl)[ChunkLight.sectionIndex(y)], x, y, z, v);
    }

    protected static int opacity(int state) { return Math.max(1, LightTables.lightBlock(state)); }

    protected boolean shapeOccludes(int fromState, int toState, int dir) { return LightTables.shapeOccludes(fromState, toState, dir); }

    public void checkBlock(long pos) { checks.add(pos); }

    public boolean hasWork() { return !checks.isEmpty() || !increase.isEmpty() || !decrease.isEmpty(); }

    /** runLightUpdates: 검사 → 감소 전파 → 증가 전파. */
    public int runUpdates() {
        for (long p : checks.toArray(new Long[0])) checkNode(p);
        checks.clear();
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
        byte[][] mine = layers(cl), theirs = layers(other);
        int fixedX = towardDir == WEST ? 0 : towardDir == EAST ? 15 : -1, fixedZ = towardDir == NORTH ? 0 : towardDir == SOUTH ? 15 : -1;
        for (int i = 0; i < 16; i++) {
            int lx = fixedX >= 0 ? fixedX : i, lz = fixedZ >= 0 ? fixedZ : i;
            int nlx = towardDir == WEST ? 15 : towardDir == EAST ? 0 : lx, nlz = towardDir == NORTH ? 15 : towardDir == SOUTH ? 0 : lz;
            for (int sec = 0; sec < ChunkLight.SECTIONS; sec++) {
                byte[] layer = mine[sec], nlayer = theirs[sec];
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

    /** 단순한 long 큐. */
    public static final class LongQueue {
        private long[] a = new long[1024];
        private int head, size;

        public boolean isEmpty() { return size == 0; }

        public void enqueue(long v) {
            if (size == a.length) {
                long[] n = new long[a.length * 2];
                for (int i = 0; i < size; i++) n[i] = a[(head + i) % a.length];
                a = n;
                head = 0;
            }
            a[(head + size) % a.length] = v;
            size++;
        }

        public long dequeue() {
            long v = a[head];
            head = (head + 1) % a.length;
            size--;
            return v;
        }
    }
}
