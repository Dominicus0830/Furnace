// 바닐라 BlockLightEngine: 발광 블록에서 퍼지는 빛. 이웃 = 자기 값 − max(1, 이웃의 빛 막는 정도), 면이 가리면 못 지나간다.
package org.domi.furnace.server.light;

public final class BlockLightEngine extends LightEngine {

    public BlockLightEngine(LightWorld world) { super(world); }

    @Override protected byte[][] layers(ChunkLight cl) { return cl.block; }

    private int emission(long pos, int state) {
        int e = LightTables.lightEmission(state);
        ChunkLight cl = world.light(px(pos) >> 4, pz(pos) >> 4);
        return e > 0 && cl != null && cl.lightEnabled ? e : 0;
    }

    @Override
    protected void checkNode(long pos) {
        if (storing(pos)) {
            int state = stateAt(pos);
            int em = emission(pos, state);
            int stored = level(pos);
            if (em < stored) {
                setLevel(pos, 0);
                enqueueDecrease(pos, decreaseAllDirections(stored));
            } else {
                enqueueDecrease(pos, CHECK_NODE);
            }
            if (em > 0) enqueueIncrease(pos, increaseLightFromEmission(em, LightTables.isEmptyShape(state)));
        }
    }

    @Override
    protected void propagateIncrease(long pos, long flags, int level) {
        Integer from = null;
        for (int dir = 0; dir < 6; dir++) {
            if (!shouldPropagateInDirection(flags, dir)) continue;
            long np = offset(pos, dir);
            if (!storing(np)) continue;
            int nl = level(np);
            if (level - 1 <= nl) continue;
            int ns = stateAt(np);
            int cand = level - opacity(ns);
            if (cand <= nl) continue;
            if (from == null) from = isFromEmptyShape(flags) ? 0 : stateAt(pos);
            if (!shapeOccludes(from, ns, dir)) {
                setLevel(np, cand);
                if (cand > 1) enqueueIncrease(np, increaseSkipOneDirection(cand, LightTables.isEmptyShape(ns), dir ^ 1));
            }
        }
    }

    @Override
    protected void propagateDecrease(long pos, long flags) {
        int lvl = fromLevel(flags);
        for (int dir = 0; dir < 6; dir++) {
            if (!shouldPropagateInDirection(flags, dir)) continue;
            long np = offset(pos, dir);
            if (!storing(np)) continue;
            int nl = level(np);
            if (nl == 0) continue;
            if (nl <= lvl - 1) {
                int ns = stateAt(np);
                int em = emission(np, ns);
                setLevel(np, 0);
                if (em < nl) enqueueDecrease(np, decreaseSkipOneDirection(nl, dir ^ 1));
                if (em > 0) enqueueIncrease(np, increaseLightFromEmission(em, LightTables.isEmptyShape(ns)));
            } else {
                enqueueIncrease(np, increaseOnlyOneDirection(nl, false, dir ^ 1));
            }
        }
    }

    /** propagateLightSources: 그 청크의 발광 블록을 모두 증가 대기열에 넣는다. */
    public void propagateLightSources(int cx, int cz) {
        int minY = world.minY(), maxY = world.maxY();
        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int s = world.state(cx * 16 + x, y, cz * 16 + z);
                    int e = LightTables.lightEmission(s);
                    if (e > 0) enqueueIncrease(pack(cx * 16 + x, y, cz * 16 + z), increaseLightFromEmission(e, LightTables.isEmptyShape(s)));
                }
            }
        }
    }

    /** 이웃 청크가 새로 켜질 때: 이미 있는 이쪽 가장자리 칸의 빛을 그 방향으로 한 번 밀어 넣는다(바닐라는 나중에 같은 고정점에 이른다). */
    public void pushEdge(int cx, int cz, int towardDir) {
        ChunkLight cl = world.light(cx, cz);
        if (cl == null) return;
        int fixedX = towardDir == WEST ? 0 : towardDir == EAST ? 15 : -1, fixedZ = towardDir == NORTH ? 0 : towardDir == SOUTH ? 15 : -1;
        for (int i = 0; i < 16; i++) {
            int lx = fixedX >= 0 ? fixedX : i, lz = fixedZ >= 0 ? fixedZ : i;
            for (int y = ChunkLight.MIN_Y; y < ChunkLight.MAX_Y; y++) {
                long p = pack(cx * 16 + lx, y, cz * 16 + lz);
                int lvl = level(p);
                if (lvl > 1) enqueueIncrease(p, increaseOnlyOneDirection(lvl, false, towardDir));
            }
        }
    }
}
