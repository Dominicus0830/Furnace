// 바닐라 PortalShape: 흑요석 틀 안 지옥문 모양 찾기(불 붙이기로 만들기, 틀이 깨지면 무너뜨리기).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public final class PortalShape {

    private static final int MAX = 21;
    private static final int OBSIDIAN = B.id("obsidian"), NETHER_PORTAL = B.id("nether_portal");
    private static final Prop AXIS = B.prop("axis");

    private final BlockLevel level;
    private final int axis;
    private final int rightDir;
    private int numPortalBlocks;
    private Pos bottomLeft;
    private int height;
    private final int width;

    public PortalShape(BlockLevel level, Pos pos, int axis) {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Dir.AXIS_X ? Dir.WEST : Dir.SOUTH;
        this.bottomLeft = calculateBottomLeft(pos);
        if (bottomLeft == null) {
            bottomLeft = pos;
            width = 1;
            height = 1;
        } else {
            width = calculateWidth();
            if (width > 0) height = calculateHeight();
        }
    }

    /** findEmptyPortalShape: 빈(지옥문 칸이 없는) 올바른 틀 — 주어진 축, 안 되면 다른 수평 축. */
    public static PortalShape findEmpty(BlockLevel level, Pos pos, int axis) {
        PortalShape s = new PortalShape(level, pos, axis);
        if (s.isValid() && s.numPortalBlocks == 0) return s;
        s = new PortalShape(level, pos, axis == Dir.AXIS_X ? Dir.AXIS_Z : Dir.AXIS_X);
        return s.isValid() && s.numPortalBlocks == 0 ? s : null;
    }

    private static boolean frame(int state) { return B.is(state, OBSIDIAN); }

    private static boolean isEmpty(int state) { return B.isAir(state) || B.isTag(state, "minecraft:fire") || B.is(state, NETHER_PORTAL); }

    private Pos calculateBottomLeft(Pos pos) {
        int minY = Math.max(level.minBuildHeight(), pos.y() - MAX);
        while (pos.y() > minY && isEmpty(level.getBlockState(pos.below()))) pos = pos.below();
        int dir = Dir.opposite(rightDir);
        int d = getDistanceUntilEdgeAboveFrame(pos, dir) - 1;
        return d < 0 ? null : pos.relative(dir, d);
    }

    private int calculateWidth() {
        int w = getDistanceUntilEdgeAboveFrame(bottomLeft, rightDir);
        return w >= 2 && w <= MAX ? w : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(Pos pos, int dir) {
        for (int i = 0; i <= MAX; i++) {
            Pos p = pos.relative(dir, i);
            int s = level.getBlockState(p);
            if (!isEmpty(s)) {
                if (frame(s)) return i;
                break;
            }
            if (!frame(level.getBlockState(p.below()))) break;
        }
        return 0;
    }

    private int calculateHeight() {
        int h = getDistanceUntilTop();
        return h >= 3 && h <= MAX && hasTopFrame(h) ? h : 0;
    }

    private boolean hasTopFrame(int h) {
        for (int i = 0; i < width; i++) {
            if (!frame(level.getBlockState(bottomLeft.relative(Dir.UP, h).relative(rightDir, i)))) return false;
        }
        return true;
    }

    private int getDistanceUntilTop() {
        for (int h = 0; h < MAX; h++) {
            Pos row = bottomLeft.relative(Dir.UP, h);
            if (!frame(level.getBlockState(row.relative(rightDir, -1)))) return h;
            if (!frame(level.getBlockState(row.relative(rightDir, width)))) return h;
            for (int i = 0; i < width; i++) {
                int s = level.getBlockState(row.relative(rightDir, i));
                if (!isEmpty(s)) return h;
                if (B.is(s, NETHER_PORTAL)) numPortalBlocks++;
            }
        }
        return MAX;
    }

    public boolean isValid() { return bottomLeft != null && width >= 2 && width <= MAX && height >= 3 && height <= MAX; }

    /** createPortalBlocks: 틀 안을 지옥문(축)으로 채운다(플래그 18 — 이웃 알림·모양 갱신 없음). */
    public void createPortalBlocks() {
        int portal = AXIS.with(B.defaultState(NETHER_PORTAL), Dir.axisName(axis));
        for (int h = 0; h < height; h++) {
            for (int i = 0; i < width; i++) level.setBlock(bottomLeft.relative(Dir.UP, h).relative(rightDir, i), portal, 18);
        }
    }

    /**
     * getRelativePosition: 포털 사각형 안에서 몸의 상대 위치 {가로 0..1, 세로 0..1, 앞뒤 오프셋}. 사각형이 몸보다 좁으면 가로 0.5, 낮으면 세로 0.
     */
    public static double[] getRelativePosition(org.domi.furnace.server.block.BlockUtil.FoundRectangle rect, int axis,
                                               double x, double y, double z, double width, double height) {
        double w = rect.axis1Size() - width, h = rect.axis2Size() - height;
        Pos min = rect.minCorner();
        double horiz;
        if (w > 0.0) {
            double start = (axis == Dir.AXIS_X ? min.x() : min.z()) + width / 2.0;
            double v = axis == Dir.AXIS_X ? x : z;
            horiz = Math.clamp(inverseLerp(v - start, 0.0, w), 0.0, 1.0);
        } else {
            horiz = 0.5;
        }
        double vert = h > 0.0 ? Math.clamp(inverseLerp(y - min.y(), 0.0, h), 0.0, 1.0) : 0.0;
        double depth = axis == Dir.AXIS_X ? z - (min.z() + 0.5) : x - (min.x() + 0.5);
        return new double[] {horiz, vert, depth};
    }

    /** Mth.inverseLerp. */
    private static double inverseLerp(double delta, double start, double end) { return (delta - start) / (end - start); }

    /**
     * findCollisionFreePosition: 몸이 4 이하면 발 위치를 몸 가운데로 올려 둘레 블록 충돌(보더 안의 것만, 몸 반만큼 부풀림)을 뺀 빈 곳에서
     * 가장 가까운 점(CollisionGetter.findFreePosition — VoxelShape.closestPointTo)을 찾아 다시 발 위치로 내린다. 빈 곳이 없으면 그대로.
     */
    public static double[] findCollisionFreePosition(org.domi.furnace.server.block.ChunkLevel level, double x, double y, double z,
                                                     double width, double height) {
        if (width > 4.0 || height > 4.0) return new double[] {x, y, z};
        double half = height / 2.0;
        double cx = x, cy = y + half, cz = z;
        // AABB.ofSize(center, w, 0, w).expandTowards(0, 1, 0).deflate(1.0E-6)
        double[] start = {cx - width / 2.0 + 1.0E-6, cy + 1.0E-6, cz - width / 2.0 + 1.0E-6,
                cx + width / 2.0 - 1.0E-6, cy + 1.0 - 1.0E-6, cz + width / 2.0 - 1.0E-6};
        var search = new org.domi.furnace.server.entity.AABB(start[0], start[1], start[2], start[3], start[4], start[5]).inflate(width, height, width);
        java.util.List<double[]> free = new java.util.ArrayList<>();
        free.add(start);
        for (org.domi.furnace.server.block.Shape s : level.blockCollisions(search)) {
            double[] b = s.bounds();
            if (b == null || !withinBorder(level, b)) continue;
            for (int i = 0; i < s.boxCount(); i++) {
                double[] o = s.box(i);
                double[] inf = {o[0] - width / 2.0, o[1] - half, o[2] - width / 2.0, o[3] + width / 2.0, o[4] + half, o[5] + width / 2.0};
                java.util.List<double[]> next = new java.util.ArrayList<>(free.size() + 4);
                for (double[] f : free) subtract(f, inf, next);
                free = next;
            }
        }
        double[] best = null;
        double bestD = 0;
        for (double[] f : free) {
            double px = Math.clamp(cx, f[0], f[3]), py = Math.clamp(cy, f[1], f[4]), pz = Math.clamp(cz, f[2], f[5]);
            double d = (px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz);
            if (best == null || d < bestD) {
                best = new double[] {px, py, pz};
                bestD = d;
            }
        }
        return best == null ? new double[] {x, y, z} : new double[] {best[0], best[1] - half, best[2]};
    }

    /** WorldBorder.isWithinBounds(AABB): 두 모서리(최대는 1e-5 안쪽)가 보더 안. */
    private static boolean withinBorder(org.domi.furnace.server.block.ChunkLevel level, double[] b) {
        double[] w = level.borderBounds();
        return inside(w, b[0], b[2]) && inside(w, b[3] - 1.0E-5F, b[5] - 1.0E-5F);
    }

    private static boolean inside(double[] w, double x, double z) { return x >= w[0] && x < w[2] && z >= w[1] && z < w[3]; }

    /** 상자 a 에서 b 를 뺀 나머지(부피가 있는 조각만)를 out 에(Shapes.join ONLY_FIRST 의 기하). */
    private static void subtract(double[] a, double[] b, java.util.List<double[]> out) {
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

    /** isComplete: 올바른 틀이고 안이 모두 지옥문. */
    public boolean isComplete() { return isValid() && numPortalBlocks == width * height; }
}
