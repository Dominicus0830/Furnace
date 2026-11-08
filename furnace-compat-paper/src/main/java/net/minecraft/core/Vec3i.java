package net.minecraft.core;

/**
 * 정수 좌표 값(Mojang 이름 shim, H7b, 등급 A). equals·hashCode·compareTo·toString·거리 계산은 바닐라 1.21.1 바이트코드와 같다
 * (javap 로 확인: hashCode = (y + z·31)·31 + x, compareTo = y → z → x 순, toString = MoreObjects 모양 {@code BlockPos{x=1, y=2, z=3}}).
 * 바닐라처럼 {@code Vec3i} 자체는 불변이고, 가변 하위 클래스({@link BlockPos.MutableBlockPos})만 좌표를 바꾼다.
 */
public class Vec3i implements Comparable<Vec3i> {

    public static final Vec3i ZERO = new Vec3i(0, 0, 0);

    private int x;
    private int y;
    private int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec3i v)) return false;
        return getX() == v.getX() && getY() == v.getY() && getZ() == v.getZ();
    }

    @Override
    public int hashCode() { return (getY() + getZ() * 31) * 31 + getX(); }

    @Override
    public int compareTo(Vec3i o) {
        if (getY() == o.getY()) return getZ() == o.getZ() ? getX() - o.getX() : getZ() - o.getZ();
        return getY() - o.getY();
    }

    public int getX() { return x; }

    public int getY() { return y; }

    public int getZ() { return z; }

    protected Vec3i setX(int x) { this.x = x; return this; }

    protected Vec3i setY(int y) { this.y = y; return this; }

    protected Vec3i setZ(int z) { this.z = z; return this; }

    public Vec3i offset(int dx, int dy, int dz) { return dx == 0 && dy == 0 && dz == 0 ? this : new Vec3i(getX() + dx, getY() + dy, getZ() + dz); }

    public Vec3i offset(Vec3i v) { return offset(v.getX(), v.getY(), v.getZ()); }

    public Vec3i subtract(Vec3i v) { return offset(-v.getX(), -v.getY(), -v.getZ()); }

    public Vec3i multiply(int s) {
        if (s == 1) return this;
        return s == 0 ? ZERO : new Vec3i(getX() * s, getY() * s, getZ() * s);
    }

    public Vec3i above() { return above(1); }

    public Vec3i above(int n) { return relative(Direction.UP, n); }

    public Vec3i below() { return below(1); }

    public Vec3i below(int n) { return relative(Direction.DOWN, n); }

    public Vec3i north() { return north(1); }

    public Vec3i north(int n) { return relative(Direction.NORTH, n); }

    public Vec3i south() { return south(1); }

    public Vec3i south(int n) { return relative(Direction.SOUTH, n); }

    public Vec3i west() { return west(1); }

    public Vec3i west(int n) { return relative(Direction.WEST, n); }

    public Vec3i east() { return east(1); }

    public Vec3i east(int n) { return relative(Direction.EAST, n); }

    public Vec3i relative(Direction d) { return relative(d, 1); }

    public Vec3i relative(Direction d, int n) {
        return n == 0 ? this : new Vec3i(getX() + d.getStepX() * n, getY() + d.getStepY() * n, getZ() + d.getStepZ() * n);
    }

    public Vec3i relative(Direction.Axis axis, int n) {
        if (n == 0) return this;
        int dx = axis == Direction.Axis.X ? n : 0, dy = axis == Direction.Axis.Y ? n : 0, dz = axis == Direction.Axis.Z ? n : 0;
        return new Vec3i(getX() + dx, getY() + dy, getZ() + dz);
    }

    public Vec3i cross(Vec3i o) {
        return new Vec3i(getY() * o.getZ() - getZ() * o.getY(), getZ() * o.getX() - getX() * o.getZ(), getX() * o.getY() - getY() * o.getX());
    }

    /** 바닐라: distSqr(v) < d² (Mth.square). */
    public boolean closerThan(Vec3i v, double d) { return distSqr(v) < d * d; }

    public boolean closerToCenterThan(Position p, double d) { return distToCenterSqr(p) < d * d; }

    /** 바닐라 distSqr: 아래 모서리끼리의 거리 제곱. */
    public double distSqr(Vec3i v) { return distToLowCornerSqr(v.getX(), v.getY(), v.getZ()); }

    public double distToCenterSqr(Position p) { return distToCenterSqr(p.x(), p.y(), p.z()); }

    public double distToCenterSqr(double px, double py, double pz) {
        double dx = getX() + 0.5 - px, dy = getY() + 0.5 - py, dz = getZ() + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distToLowCornerSqr(double px, double py, double pz) {
        double dx = getX() - px, dy = getY() - py, dz = getZ() - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 바닐라: 축마다 |차| 를 float 로 더해 int 로. */
    public int distManhattan(Vec3i v) {
        float dx = Math.abs(v.getX() - getX()), dy = Math.abs(v.getY() - getY()), dz = Math.abs(v.getZ() - getZ());
        return (int) (dx + dy + dz);
    }

    public int get(Direction.Axis axis) { return axis.choose(getX(), getY(), getZ()); }

    @Override
    public String toString() { return getClass().getSimpleName() + "{x=" + getX() + ", y=" + getY() + ", z=" + getZ() + "}"; }

    public String toShortString() { return getX() + ", " + getY() + ", " + getZ(); }
}
