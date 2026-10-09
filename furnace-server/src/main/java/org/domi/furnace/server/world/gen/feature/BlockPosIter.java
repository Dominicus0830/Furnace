// 바닐라 BlockPos.withinManhattan 이터레이터 포팅 — 맨해튼 거리 순(깊이 → x → y → z, z 대칭 쌍) 방문 순서를 그대로 재현
package org.domi.furnace.server.world.gen.feature;

public final class BlockPosIter {

    private BlockPosIter() {}

    /** 방문 콜백 — false 를 돌려주면 순회를 멈춘다(바닐라 for 루프의 break). */
    @FunctionalInterface
    public interface Visitor {
        boolean visit(int x, int y, int z);
    }

    /**
     * BlockPos.withinManhattan(origin, xSize, ySize, zSize). 바닐라 AbstractIterator 상태기계를 그대로 옮겼다:
     * z 오프셋이 0 이 아니면 같은 (x,y) 에서 +z 다음에 -z(zMirror) 를 곧바로 내놓는다.
     */
    public static void withinManhattan(int ox, int oy, int oz, int xSize, int ySize, int zSize, Visitor visitor) {
        int maxDepth = xSize + ySize + zSize;
        int currentDepth = 0, maxX = 0, maxY = 0, x = 0, y = 0;
        boolean zMirror = false;
        int cx = 0, cy = 0, cz = 0;
        while (true) {
            if (zMirror) {
                zMirror = false;
                cz = oz - (cz - oz);
                if (!visitor.visit(cx, cy, cz)) return;
                continue;
            }
            boolean found = false;
            for (; !found; y++) {
                if (y > maxY) {
                    x++;
                    if (x > maxX) {
                        currentDepth++;
                        if (currentDepth > maxDepth) return;
                        maxX = Math.min(xSize, currentDepth);
                        x = -maxX;
                    }
                    maxY = Math.min(ySize, currentDepth - Math.abs(x));
                    y = -maxY;
                }
                int i1 = x;
                int j1 = y;
                int k1 = currentDepth - Math.abs(i1) - Math.abs(j1);
                if (k1 <= zSize) {
                    zMirror = k1 != 0;
                    cx = ox + i1;
                    cy = oy + j1;
                    cz = oz + k1;
                    found = true;
                }
            }
            if (!visitor.visit(cx, cy, cz)) return;
        }
    }

    /** BlockPos.distManhattan — 바닐라는 float 로 합산 후 int 캐스팅(좌표 차가 작아 정확). */
    public static int distManhattan(int x, int y, int z, int ox, int oy, int oz) {
        return (int) ((float) Math.abs(ox - x) + (float) Math.abs(oy - y) + (float) Math.abs(oz - z));
    }
}
