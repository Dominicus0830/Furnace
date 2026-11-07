// 바닐라 BlockLightEngine: 발광 블록에서 퍼지는 빛. 이웃 = 자기 값 − max(1, 이웃의 빛 막는 정도), 면이 가리면 못 지나간다.
package org.domi.furnace.server.light;

public final class BlockLightEngine extends LightEngine {

    public BlockLightEngine(LightWorld world) { super(world); }

    @Override protected byte[][] layers(ChunkLight cl) { return cl.block; }

    private int emission(long pos, int state) {
        int e = Math.max(LightTables.lightEmission(state), world.extraEmission(px(pos), py(pos), pz(pos)));
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

    /**
     * propagateLightSources: 그 청크의 발광 블록을 모두 증가 대기열에 넣는다. 바닐라 findBlockLightSources 처럼 팔레트에 발광 상태가
     * 없는 구역은 칸을 훑지 않는다(넣는 항목과 순서는 그대로 — 그런 구역에서는 원래 아무것도 넣지 않는다).
     */
    public void propagateLightSources(int cx, int cz) {
        int minY = world.minY(), maxY = world.maxY();
        for (int y = minY; y < maxY; y++) {
            if ((y & 15) == 0 && y + 16 <= maxY && !world.sectionMayEmit(cx, y >> 4, cz)) {
                y += 15;   // 구역 하나를 건너뛴다(for 의 y++ 가 다음 구역 맨 아래로)
                continue;
            }
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int s = world.state(cx * 16 + x, y, cz * 16 + z);
                    int e = LightTables.lightEmission(s);
                    if (e > 0) enqueueIncrease(pack(cx * 16 + x, y, cz * 16 + z), increaseLightFromEmission(e, LightTables.isEmptyShape(s)));
                }
            }
        }
    }

}
