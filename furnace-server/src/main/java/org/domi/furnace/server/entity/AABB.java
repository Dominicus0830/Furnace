// 바닐라 AABB: 축 정렬 상자. 엔티티 몸통·충돌 판정에 쓴다. 불변.
package org.domi.furnace.server.entity;

public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /** 두 꼭짓점으로(작은 쪽을 min 으로). */
    public static AABB of(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public double min(int axis) { return axis == 0 ? minX : axis == 1 ? minY : minZ; }

    public double max(int axis) { return axis == 0 ? maxX : axis == 1 ? maxY : maxZ; }

    public AABB move(double dx, double dy, double dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /** expandTowards: 움직임 쪽으로만 늘린다. */
    public AABB expandTowards(double dx, double dy, double dz) {
        double x0 = minX, y0 = minY, z0 = minZ, x1 = maxX, y1 = maxY, z1 = maxZ;
        if (dx < 0.0) x0 += dx; else if (dx > 0.0) x1 += dx;
        if (dy < 0.0) y0 += dy; else if (dy > 0.0) y1 += dy;
        if (dz < 0.0) z0 += dz; else if (dz > 0.0) z1 += dz;
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public AABB inflate(double dx, double dy, double dz) {
        return new AABB(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    /** getSize: 세 변 길이의 평균. */
    public double size() { return ((maxX - minX) + (maxY - minY) + (maxZ - minZ)) / 3.0; }

    public AABB inflate(double d) { return inflate(d, d, d); }

    public AABB deflate(double d) { return inflate(-d, -d, -d); }

    public boolean intersects(double x0, double y0, double z0, double x1, double y1, double z1) {
        return minX < x1 && maxX > x0 && minY < y1 && maxY > y0 && minZ < z1 && maxZ > z0;
    }

    public boolean intersects(AABB o) { return intersects(o.minX, o.minY, o.minZ, o.maxX, o.maxY, o.maxZ); }

    public double getXsize() { return maxX - minX; }

    public double getYsize() { return maxY - minY; }

    public double getZsize() { return maxZ - minZ; }

    public boolean contains(double x, double y, double z) { return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ; }

    /**
     * AABB.clip(from, to): 선분이 상자 면을 들어갈 때의 첫 점({x, y, z}), 안 만나면 null.
     * 시작점이 상자 안이면 바닐라처럼 null(들어오는 면이 없다).
     */
    public double[] clip(double fx, double fy, double fz, double tx, double ty, double tz) {
        double[] t = {1.0};
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        boolean hit = false;
        if (dx > 1.0E-7) hit |= clipPoint(t, dx, dy, dz, minX, minY, maxY, minZ, maxZ, fx, fy, fz, 0);
        else if (dx < -1.0E-7) hit |= clipPoint(t, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, fx, fy, fz, 0);
        if (dy > 1.0E-7) hit |= clipPoint(t, dy, dz, dx, minY, minZ, maxZ, minX, maxX, fy, fz, fx, 1);
        else if (dy < -1.0E-7) hit |= clipPoint(t, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, fy, fz, fx, 1);
        if (dz > 1.0E-7) hit |= clipPoint(t, dz, dx, dy, minZ, minX, maxX, minY, maxY, fz, fx, fy, 2);
        else if (dz < -1.0E-7) hit |= clipPoint(t, dz, dx, dy, maxZ, minX, maxX, minY, maxY, fz, fx, fy, 2);
        if (!hit) return null;
        return new double[] {fx + t[0] * dx, fy + t[0] * dy, fz + t[0] * dz};
    }

    private static boolean clipPoint(double[] t, double d0, double d1, double d2, double plane, double aMin, double aMax, double bMin, double bMax,
            double s0, double s1, double s2, int axis) {
        double t0 = (plane - s0) / d0;
        double u = s1 + t0 * d1;
        double v = s2 + t0 * d2;
        if (0.0 < t0 && t0 < t[0] && aMin - 1.0E-7 < u && u < aMax + 1.0E-7 && bMin - 1.0E-7 < v && v < bMax + 1.0E-7) {
            t[0] = t0;
            return true;
        }
        return false;
    }
}
