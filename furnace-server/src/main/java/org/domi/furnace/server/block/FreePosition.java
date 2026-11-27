package org.domi.furnace.server.block;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.AABB;

/**
 * 바닐라 CollisionGetter.findFreePosition(entity, shape, pos, x, y, z): 시작 상자(shape)에서 둘레 블록 충돌(보더 안의 것만, x/2·y/2·z/2 만큼 부풀림)을
 * 뺀 빈 곳 가운데 pos 에 가장 가까운 점(VoxelShape.closestPointTo). 빈 곳이 없으면 null(Optional.empty).
 * 블록 충돌은 주어진 문맥(getBlockCollisions(entity) 의 엔티티 문맥)으로 묻는다. 네더 포털 출구 자리 찾기와 몸이 커질 때 자리 밀기가 쓴다.
 * 가정: 빼기는 상자 조각 목록으로 한다 — 같은 거리의 점이 둘 이상이면 바닐라(복셀 모양의 상자 순서)와 고르는 점이 다를 수 있다.
 */
public final class FreePosition {

    private FreePosition() {}

    /** @param start 시작 상자 {minX, minY, minZ, maxX, maxY, maxZ}(바닐라 shape — 부피가 있어야 한다) @return 가장 가까운 빈 점 {x, y, z} 또는 null */
    public static double[] find(ChunkLevel level, CollisionContext ctx, double[] start, double px, double py, double pz, double x, double y, double z) {
        var search = new AABB(start[0], start[1], start[2], start[3], start[4], start[5]).inflate(x, y, z);
        List<double[]> free = new ArrayList<>();
        free.add(start);
        for (Shape s : level.blockCollisionsWith(ctx, search)) {
            double[] b = s.bounds();
            if (b == null || !withinBorder(level, b)) continue;
            for (int i = 0; i < s.boxCount(); i++) {
                double[] o = s.box(i);
                double[] inf = {o[0] - x / 2.0, o[1] - y / 2.0, o[2] - z / 2.0, o[3] + x / 2.0, o[4] + y / 2.0, o[5] + z / 2.0};
                List<double[]> next = new ArrayList<>(free.size() + 4);
                for (double[] f : free) subtract(f, inf, next);
                free = next;
            }
        }
        double[] best = null;
        double bestD = 0;
        for (double[] f : free) {
            double cx = Math.clamp(px, f[0], f[3]), cy = Math.clamp(py, f[1], f[4]), cz = Math.clamp(pz, f[2], f[5]);
            double d = (cx - px) * (cx - px) + (cy - py) * (cy - py) + (cz - pz) * (cz - pz);
            if (best == null || d < bestD) {
                best = new double[] {cx, cy, cz};
                bestD = d;
            }
        }
        return best;
    }

    /** AABB.ofSize(center, w, h, d). */
    public static double[] ofSize(double cx, double cy, double cz, double w, double h, double d) {
        return new double[] {cx - w / 2.0, cy - h / 2.0, cz - d / 2.0, cx + w / 2.0, cy + h / 2.0, cz + d / 2.0};
    }

    /** WorldBorder.isWithinBounds(AABB): 두 모서리(최대는 1e-5 안쪽)가 보더 안. */
    private static boolean withinBorder(ChunkLevel level, double[] b) {
        double[] w = level.borderBounds();
        return inside(w, b[0], b[2]) && inside(w, b[3] - 1.0E-5F, b[5] - 1.0E-5F);
    }

    private static boolean inside(double[] w, double x, double z) { return x >= w[0] && x < w[2] && z >= w[1] && z < w[3]; }

    /** 상자 a 에서 b 를 뺀 나머지(부피가 있는 조각만)를 out 에(Shapes.join ONLY_FIRST 의 기하). */
    private static void subtract(double[] a, double[] b, List<double[]> out) {
        if (b[0] >= a[3] || b[3] <= a[0] || b[1] >= a[4] || b[4] <= a[1] || b[2] >= a[5] || b[5] <= a[2]) {
            out.add(a);
            return;
        }
        double x0 = a[0], x1 = a[3], y0 = a[1], y1 = a[4];
        if (b[0] > x0) out.add(new double[] {x0, a[1], a[2], b[0], a[4], a[5]});
        if (b[3] < x1) out.add(new double[] {b[3], a[1], a[2], x1, a[4], a[5]});
        double ix0 = Math.max(x0, b[0]), ix1 = Math.min(x1, b[3]);
        if (b[1] > y0) out.add(new double[] {ix0, y0, a[2], ix1, b[1], a[5]});
        if (b[4] < y1) out.add(new double[] {ix0, b[4], a[2], ix1, y1, a[5]});
        double iy0 = Math.max(y0, b[1]), iy1 = Math.min(y1, b[4]);
        if (b[2] > a[2]) out.add(new double[] {ix0, iy0, a[2], ix1, iy1, b[2]});
        if (b[5] < a[5]) out.add(new double[] {ix0, iy0, b[5], ix1, iy1, a[5]});
    }
}
