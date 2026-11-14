// 바닐라 SkyLightEngine: 하늘빛. 열마다 "가장 낮은 원천 y"(ChunkSkyLightSources) 이상은 15, 그 아래는 옆·아래로 퍼지며 줄어든다.
package org.domi.furnace.server.light;

import org.domi.furnace.server.world.ChunkSection;

public final class SkyLightEngine extends LightEngine {
    private static final long DECREASE_ALL_15 = decreaseAllDirections(15);
    private static final long DECREASE_SKIP_UP_15 = decreaseSkipOneDirection(15, UP);
    private static final long INCREASE_SKIP_UP_15 = increaseSkipOneDirection(15, false, UP);

    public SkyLightEngine(LightWorld world) { super(world); }

    @Override protected byte[] layer(ChunkLight cl, int section) { return cl.sky(section); }

    @Override protected byte[] writableLayer(ChunkLight cl, int section) { return cl.writableSky(section); }

    @Override protected boolean isSky() { return true; }

    // ------------------------------------------------------------------ ChunkSkyLightSources

    private boolean blocks(int stateAbove, int stateBelow) {
        if (LightTables.lightBlock(stateBelow) != 0) return true;
        return LightTables.faceShapesOcclude(LightTables.faceShape(stateAbove, DOWN), LightTables.faceShape(stateBelow, UP));
    }

    private int state(int x, int y, int z) {
        if (y < world.minY() || y >= world.maxY()) return 12958;   // void_air
        return world.state(x, y, z);
    }

    /**
     * 처음부터 모든 열의 원천 높이를 센다(ChunkSkyLightSources.fillFrom → findLowestSourceY). 생성 때와 청크를 올릴 때마다 돈다.
     * 바닐라 특이점: 공기뿐인 구역(hasOnlyAir)을 만나면 위 칸 상태를 AIR 로 되돌리고 구역을 통째로 건너뛴다. 그래서 공기뿐인 구역 바로 위
     * 구역의 맨 아래 줄 블록은 아래 면이 막혀 있어도(바닥 반 블록 등) 원천을 멈추지 않는다. 블록이 바뀔 때의 update·findLowestSourceBelow 는
     * 칸마다 검사해서 이 특이점이 없다({@link #updateSource}).
     */
    public void computeSources(int cx, int cz) {
        ChunkLight cl = world.light(cx, cz);
        int minSection = world.minY() >> 4, maxSection = (world.maxY() - 1) >> 4;
        int[] uniform = new int[maxSection - minSection + 1];
        boolean[] onlyAir = new boolean[uniform.length];
        for (int s = minSection; s <= maxSection; s++) {
            int u = world.uniformSectionState(cx, s, cz);
            uniform[s - minSection] = u;
            onlyAir[s - minSection] = u >= 0 ? ChunkSection.isAir(u) : world.sectionHasOnlyAir(cx, s, cz);
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                int above = ChunkSection.AIR;
                int result = ChunkLight.NO_BLOCKER;
                for (int y = world.maxY() - 1; y >= world.minY(); y--) {
                    if ((y & 15) == 15 && y - 15 >= world.minY()) {
                        int si = (y >> 4) - minSection;
                        if (onlyAir[si]) {
                            // 바닐라 hasOnlyAir 분기: 위 칸과의 면을 검사하지 않고 위 칸 상태를 AIR 로 되돌린 채 구역을 건너뛴다
                            above = ChunkSection.AIR;
                            y -= 15;
                            continue;
                        }
                        // 한 상태로만 찬 구역: 그 칸들은 모두 같은 상태라, 맨 위 칸도 그 아래 칸들도 막지 않으면 구역 전체를 그대로 지난다
                        int u = uniform[si];
                        if (u >= 0 && !blocks(above, u) && !blocks(u, u)) {
                            above = u;
                            y -= 15;   // for 의 y-- 가 아래 구역 맨 위로
                            continue;
                        }
                    }
                    int here = state(x, y, z);
                    if (blocks(above, here)) {
                        result = y + 1;
                        break;
                    }
                    above = here;
                }
                cl.lowestSource[lx + lz * 16] = Math.max(result, ChunkLight.NO_BLOCKER);
            }
        }
    }

    /** 블록 하나가 바뀐 뒤 그 열의 원천 높이를 고친다(ChunkSkyLightSources.update). */
    public boolean updateSource(int x, int y, int z) {
        ChunkLight cl = world.light(x >> 4, z >> 4);
        int idx = (x & 15) + (z & 15) * 16;
        int lowest = cl.lowestSource[idx];
        int above = y + 1;
        if (above < lowest) return false;
        int sAbove = state(x, y + 1, z), sHere = state(x, y, z);
        if (updateStep(cl, idx, lowest, y + 1, sAbove, sHere, x, y, z)) return true;
        int sBelow = state(x, y - 1, z);
        return updateStep(cl, idx, lowest, y, sHere, sBelow, x, y - 1, z);
    }

    private boolean updateStep(ChunkLight cl, int idx, int lowest, int aboveY, int sAbove, int sBelow, int x, int belowY, int z) {
        if (blocks(sAbove, sBelow)) {
            if (aboveY > lowest) {
                cl.lowestSource[idx] = aboveY;
                return true;
            }
        } else if (aboveY == lowest) {
            cl.lowestSource[idx] = scanDown(x, belowY, z, sBelow);
            return true;
        }
        return false;
    }

    private int scanDown(int x, int y, int z, int stateAtY) {
        int aboveY = y;
        int aboveState = stateAtY;
        int by = y - 1;
        while (by >= ChunkLight.NO_BLOCKER) {
            int bs = state(x, by, z);
            if (blocks(aboveState, bs)) return aboveY;
            aboveState = bs;
            aboveY = by;
            by--;
        }
        return ChunkLight.NO_BLOCKER;
    }

    /** 바깥에서 보는 값: 막는 블록이 없는 열은 Integer.MIN_VALUE. */
    private int reported(int raw) { return raw == ChunkLight.NO_BLOCKER ? Integer.MIN_VALUE : raw; }

    private int lowestAt(int x, int z, int missing) {
        ChunkLight cl = world.light(x >> 4, z >> 4);
        return cl == null ? missing : reported(cl.lowestSource[(x & 15) + (z & 15) * 16]);
    }

    // ------------------------------------------------------------------ 검사·전파

    @Override
    protected void checkNode(long pos) {
        int x = px(pos), y = py(pos), z = pz(pos);
        ChunkLight cl = world.light(x >> 4, z >> 4);
        int lowest = cl != null && cl.lightEnabled ? lowestAt(x, z, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        if (lowest != Integer.MAX_VALUE) updateSourcesInColumn(x, z, lowest);
        if (storing(pos)) {
            if (y >= lowest) {
                enqueueDecrease(pos, DECREASE_SKIP_UP_15);
                enqueueIncrease(pos, INCREASE_SKIP_UP_15);
            } else {
                int stored = level(pos);
                if (stored > 0) {
                    setLevel(pos, 0);
                    enqueueDecrease(pos, decreaseAllDirections(stored));
                } else {
                    enqueueDecrease(pos, CHECK_NODE);
                }
            }
        }
    }

    private void updateSourcesInColumn(int x, int z, int lowest) {
        int bottomY = ChunkLight.MIN_Y;
        // 낮아진 원천 아래 15 였던 칸을 지운다
        if (lowest > bottomY) {
            for (int y = lowest - 1; y >= bottomY; y--) {
                long p = pack(x, y, z);
                if (level(p) != 15) break;
                setLevel(p, 0);
                enqueueDecrease(p, y == lowest - 1 ? DECREASE_ALL_15 : DECREASE_SKIP_UP_15);
            }
        }
        // 높아진/새 원천 위를 15 로 채운다
        int neighborMax = Math.max(Math.max(lowestAt(x - 1, z, Integer.MIN_VALUE), lowestAt(x + 1, z, Integer.MIN_VALUE)),
                Math.max(lowestAt(x, z - 1, Integer.MIN_VALUE), lowestAt(x, z + 1, Integer.MIN_VALUE)));
        int start = Math.max(lowest, bottomY);
        for (int y = start; y < ChunkLight.MAX_Y; y++) {
            long p = pack(x, y, z);
            if (level(p) == 15) return;
            setLevel(p, 15);
            if (y < neighborMax || y == lowest) enqueueIncrease(p, INCREASE_SKIP_UP_15);
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
            if (!storing(np)) {
                decreaseBlocked(np);
                continue;
            }
            int nl = level(np);
            if (nl == 0) continue;
            if (nl <= lvl - 1) {
                setLevel(np, 0);
                enqueueDecrease(np, decreaseSkipOneDirection(nl, dir ^ 1));
            } else {
                enqueueIncrease(np, increaseOnlyOneDirection(nl, false, dir ^ 1));
            }
        }
    }

    /** propagateLightSources(eox.b(dcd)): 원천 높이 위를 15 로 채우고 옆으로 퍼질 가장자리 칸을 증가 대기열에 넣는다. */
    public void propagateLightSources(int cx, int cz) {
        ChunkLight cl = world.light(cx, cz);
        // 모든 열이 15 로 채우는 구역(모든 열의 시작 높이보다 위)은 한 번에 채운다 — 칸마다 쓰는 것과 같은 값이다.
        int maxStart = ChunkLight.MIN_Y;
        for (int raw : cl.lowestSource) maxStart = Math.max(maxStart, reported(raw));
        int fillBottom = ChunkLight.MIN_Y + ((maxStart - ChunkLight.MIN_Y + 15) >> 4 << 4);
        for (int y = fillBottom; y < ChunkLight.MAX_Y; y += 16) cl.fillSkyFull(ChunkLight.sectionIndex(y));   // 공유 FULL 층
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                int lowest = reported(cl.lowestSource[lx + lz * 16]);
                int n = lowestAt(x, z - 1, Integer.MIN_VALUE), s = lowestAt(x, z + 1, Integer.MIN_VALUE),
                        w = lowestAt(x - 1, z, Integer.MIN_VALUE), e = lowestAt(x + 1, z, Integer.MIN_VALUE);
                int neighborMax = Math.max(Math.max(n, s), Math.max(w, e));
                int start = Math.max(ChunkLight.MIN_Y, lowest);
                // 채운 구역: 값은 이미 15 — 대기열에 넣는 칸(y == lowest 또는 y < neighborMax)만 위에서 아래 순서 그대로 본다
                int hi = Math.min(ChunkLight.MAX_Y - 1, Math.max(neighborMax == Integer.MIN_VALUE ? Integer.MIN_VALUE : neighborMax - 1, lowest));
                for (int y = hi; y >= Math.max(start, fillBottom); y--) {
                    if (y == lowest || y < neighborMax) {
                        enqueueIncrease(pack(x, y, z), increaseSkySourceInDirections(y == lowest, y < n, y < s, y < w, y < e));
                    }
                }
                for (int y = Math.min(ChunkLight.MAX_Y, fillBottom) - 1; y >= start; y--) {
                    cl.setSky(x, y, z, 15);
                    if (y == lowest || y < neighborMax) {
                        enqueueIncrease(pack(x, y, z), increaseSkySourceInDirections(y == lowest, y < n, y < s, y < w, y < e));
                    }
                }
            }
        }
    }

}
