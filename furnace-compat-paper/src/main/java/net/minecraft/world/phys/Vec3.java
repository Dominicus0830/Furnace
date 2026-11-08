package net.minecraft.world.phys;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;

/**
 * 실수 3차원 벡터(Mojang 이름 shim, H7b, 등급 A, 불변). equals(Double.compare 셋)·hashCode(doubleToLongBits 접기)·toString
 * ({@code (x, y, z)})는 바닐라 1.21.1 바이트코드와 같다. 삼각 함수 표를 쓰는 회전(xRot·yRot)은 두지 않았다(없는 멤버 — 호출 위치에서 실패).
 */
public class Vec3 implements Position {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3 atLowerCornerOf(Vec3i v) { return new Vec3(v.getX(), v.getY(), v.getZ()); }

    public static Vec3 atLowerCornerWithOffset(Vec3i v, double dx, double dy, double dz) { return new Vec3(v.getX() + dx, v.getY() + dy, v.getZ() + dz); }

    public static Vec3 atCenterOf(Vec3i v) { return atLowerCornerWithOffset(v, 0.5, 0.5, 0.5); }

    public static Vec3 atBottomCenterOf(Vec3i v) { return atLowerCornerWithOffset(v, 0.5, 0.0, 0.5); }

    public static Vec3 upFromBottomCenterOf(Vec3i v, double dy) { return atLowerCornerWithOffset(v, 0.5, dy, 0.5); }

    public Vec3 vectorTo(Vec3 o) { return new Vec3(o.x - x, o.y - y, o.z - z); }

    public Vec3 normalize() {
        double d = Math.sqrt(x * x + y * y + z * z);
        return d < 1.0E-4 ? ZERO : new Vec3(x / d, y / d, z / d);
    }

    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }

    public Vec3 subtract(Vec3 o) { return subtract(o.x, o.y, o.z); }

    public Vec3 subtract(double dx, double dy, double dz) { return add(-dx, -dy, -dz); }

    public Vec3 add(Vec3 o) { return add(o.x, o.y, o.z); }

    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }

    public boolean closerThan(Position p, double d) { return distanceToSqr(p.x(), p.y(), p.z()) < d * d; }

    public double distanceTo(Vec3 o) {
        double dx = o.x - x, dy = o.y - y, dz = o.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceToSqr(Vec3 o) {
        double dx = o.x - x, dy = o.y - y, dz = o.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceToSqr(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3 scale(double s) { return multiply(s, s, s); }

    public Vec3 reverse() { return scale(-1.0); }

    public Vec3 multiply(Vec3 o) { return multiply(o.x, o.y, o.z); }

    public Vec3 multiply(double sx, double sy, double sz) { return new Vec3(x * sx, y * sy, z * sz); }

    public double length() { return Math.sqrt(x * x + y * y + z * z); }

    public double lengthSqr() { return x * x + y * y + z * z; }

    public double horizontalDistance() { return Math.sqrt(x * x + z * z); }

    public double horizontalDistanceSqr() { return x * x + z * z; }

    public Vec3 lerp(Vec3 o, double t) { return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }

    public double get(Direction.Axis axis) { return axis.choose(x, y, z); }

    public Vec3 with(Direction.Axis axis, double v) {
        double nx = axis == Direction.Axis.X ? v : x, ny = axis == Direction.Axis.Y ? v : y, nz = axis == Direction.Axis.Z ? v : z;
        return new Vec3(nx, ny, nz);
    }

    public Vec3 relative(Direction d, double n) {
        Vec3i v = d.getNormal();
        return new Vec3(x + n * v.getX(), y + n * v.getY(), z + n * v.getZ());
    }

    @Override
    public double x() { return x; }

    @Override
    public double y() { return y; }

    @Override
    public double z() { return z; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec3 v)) return false;
        return Double.compare(v.x, x) == 0 && Double.compare(v.y, y) == 0 && Double.compare(v.z, z) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(x);
        int i = (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(y);
        i = 31 * i + (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(z);
        return 31 * i + (int) (l ^ l >>> 32);
    }

    @Override
    public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
