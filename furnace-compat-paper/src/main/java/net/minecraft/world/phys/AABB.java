package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 축 정렬 상자(Mojang 이름 shim, 영역 9 에서 새로 씀). 순수 값 클래스. 생성자는 각 축의 작은 값·큰 값을 알아서 고르고,
 * 비교·해시·글 표현·광선 교차(clip)는 바닐라 1.21.1 과 같다(값 대조 시험 CoreVanillaValuesTest 가 견준다).
 */
public class AABB {

    private static final double EPSILON = 1.0E-7;
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public AABB(BlockPos pos) { this(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1); }

    public AABB(Vec3 a, Vec3 b) { this(a.x, a.y, a.z, b.x, b.y, b.z); }

    public static AABB of(BoundingBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    public static AABB unitCubeFromLowerCorner(Vec3 v) { return new AABB(v.x, v.y, v.z, v.x + 1.0, v.y + 1.0, v.z + 1.0); }

    public static AABB encapsulatingFullBlocks(BlockPos a, BlockPos b) {
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    public AABB setMinX(double v) { return new AABB(v, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ); }

    public AABB setMinY(double v) { return new AABB(this.minX, v, this.minZ, this.maxX, this.maxY, this.maxZ); }

    public AABB setMinZ(double v) { return new AABB(this.minX, this.minY, v, this.maxX, this.maxY, this.maxZ); }

    public AABB setMaxX(double v) { return new AABB(this.minX, this.minY, this.minZ, v, this.maxY, this.maxZ); }

    public AABB setMaxY(double v) { return new AABB(this.minX, this.minY, this.minZ, this.maxX, v, this.maxZ); }

    public AABB setMaxZ(double v) { return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, v); }

    public double min(Direction.Axis axis) { return axis.choose(this.minX, this.minY, this.minZ); }

    public double max(Direction.Axis axis) { return axis.choose(this.maxX, this.maxY, this.maxZ); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AABB a)) return false;
        return Double.compare(a.minX, this.minX) == 0 && Double.compare(a.minY, this.minY) == 0 && Double.compare(a.minZ, this.minZ) == 0
                && Double.compare(a.maxX, this.maxX) == 0 && Double.compare(a.maxY, this.maxY) == 0 && Double.compare(a.maxZ, this.maxZ) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.minX);
        int i = (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minY);
        i = 31 * i + (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minZ);
        i = 31 * i + (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxX);
        i = 31 * i + (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxY);
        i = 31 * i + (int) (l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxZ);
        return 31 * i + (int) (l ^ l >>> 32);
    }

    public AABB contract(double x, double y, double z) {
        double a = this.minX, b = this.minY, c = this.minZ, d = this.maxX, e = this.maxY, f = this.maxZ;
        if (x < 0.0) a -= x; else if (x > 0.0) d -= x;
        if (y < 0.0) b -= y; else if (y > 0.0) e -= y;
        if (z < 0.0) c -= z; else if (z > 0.0) f -= z;
        return new AABB(a, b, c, d, e, f);
    }

    public AABB expandTowards(Vec3 v) { return expandTowards(v.x, v.y, v.z); }

    public AABB expandTowards(double x, double y, double z) {
        double a = this.minX, b = this.minY, c = this.minZ, d = this.maxX, e = this.maxY, f = this.maxZ;
        if (x < 0.0) a += x; else if (x > 0.0) d += x;
        if (y < 0.0) b += y; else if (y > 0.0) e += y;
        if (z < 0.0) c += z; else if (z > 0.0) f += z;
        return new AABB(a, b, c, d, e, f);
    }

    public AABB inflate(double x, double y, double z) {
        return new AABB(this.minX - x, this.minY - y, this.minZ - z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AABB inflate(double v) { return inflate(v, v, v); }

    public AABB intersect(AABB o) {
        return new AABB(Math.max(this.minX, o.minX), Math.max(this.minY, o.minY), Math.max(this.minZ, o.minZ),
                Math.min(this.maxX, o.maxX), Math.min(this.maxY, o.maxY), Math.min(this.maxZ, o.maxZ));
    }

    public AABB minmax(AABB o) {
        return new AABB(Math.min(this.minX, o.minX), Math.min(this.minY, o.minY), Math.min(this.minZ, o.minZ),
                Math.max(this.maxX, o.maxX), Math.max(this.maxY, o.maxY), Math.max(this.maxZ, o.maxZ));
    }

    public AABB move(double x, double y, double z) {
        return new AABB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AABB move(BlockPos pos) { return move(pos.getX(), pos.getY(), pos.getZ()); }

    public AABB move(Vec3 v) { return move(v.x, v.y, v.z); }

    public AABB move(org.joml.Vector3f v) { return move(v.x, v.y, v.z); }

    public boolean intersects(AABB o) { return intersects(o.minX, o.minY, o.minZ, o.maxX, o.maxY, o.maxZ); }

    public boolean intersects(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.minX < x2 && this.maxX > x1 && this.minY < y2 && this.maxY > y1 && this.minZ < z2 && this.maxZ > z1;
    }

    public boolean intersects(Vec3 a, Vec3 b) {
        return intersects(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    public boolean contains(Vec3 v) { return contains(v.x, v.y, v.z); }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public double getSize() { return (getXsize() + getYsize() + getZsize()) / 3.0; }

    public double getXsize() { return this.maxX - this.minX; }

    public double getYsize() { return this.maxY - this.minY; }

    public double getZsize() { return this.maxZ - this.minZ; }

    public AABB deflate(double x, double y, double z) { return inflate(-x, -y, -z); }

    public AABB deflate(double v) { return inflate(-v); }

    public java.util.Optional<Vec3> clip(Vec3 from, Vec3 to) {
        double[] t = {1.0};
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        Direction dir = getDirection(this, from, t, null, dx, dy, dz);
        if (dir == null) return java.util.Optional.empty();
        double d = t[0];
        return java.util.Optional.of(from.add(d * dx, d * dy, d * dz));
    }

    public static BlockHitResult clip(Iterable<AABB> boxes, Vec3 from, Vec3 to, BlockPos pos) {
        double[] t = {1.0};
        Direction dir = null;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        for (AABB box : boxes) dir = getDirection(box.move(pos), from, t, dir, dx, dy, dz);
        if (dir == null) return null;
        double d = t[0];
        return new BlockHitResult(from.add(d * dx, d * dy, d * dz), dir, pos, false);
    }

    private static Direction getDirection(AABB box, Vec3 from, double[] t, Direction dir, double dx, double dy, double dz) {
        if (dx > 1.0E-7) {
            dir = clipPoint(t, dir, dx, dy, dz, box.minX, box.minY, box.maxY, box.minZ, box.maxZ, Direction.WEST, from.x, from.y, from.z);
        } else if (dx < -1.0E-7) {
            dir = clipPoint(t, dir, dx, dy, dz, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ, Direction.EAST, from.x, from.y, from.z);
        }
        if (dy > 1.0E-7) {
            dir = clipPoint(t, dir, dy, dz, dx, box.minY, box.minZ, box.maxZ, box.minX, box.maxX, Direction.DOWN, from.y, from.z, from.x);
        } else if (dy < -1.0E-7) {
            dir = clipPoint(t, dir, dy, dz, dx, box.maxY, box.minZ, box.maxZ, box.minX, box.maxX, Direction.UP, from.y, from.z, from.x);
        }
        if (dz > 1.0E-7) {
            dir = clipPoint(t, dir, dz, dx, dy, box.minZ, box.minX, box.maxX, box.minY, box.maxY, Direction.NORTH, from.z, from.x, from.y);
        } else if (dz < -1.0E-7) {
            dir = clipPoint(t, dir, dz, dx, dy, box.maxZ, box.minX, box.maxX, box.minY, box.maxY, Direction.SOUTH, from.z, from.x, from.y);
        }
        return dir;
    }

    private static Direction clipPoint(double[] t, Direction dir, double d, double e, double f, double g, double h, double i, double j, double k,
            Direction hit, double l, double m, double n) {
        double o = (g - l) / d;
        double p = m + o * e;
        double q = n + o * f;
        if (0.0 < o && o < t[0] && h - 1.0E-7 < p && p < i + 1.0E-7 && j - 1.0E-7 < q && q < k + 1.0E-7) {
            t[0] = o;
            return hit;
        }
        return dir;
    }

    public double distanceToSqr(Vec3 v) {
        double d = Math.max(Math.max(this.minX - v.x, v.x - this.maxX), 0.0);
        double e = Math.max(Math.max(this.minY - v.y, v.y - this.maxY), 0.0);
        double f = Math.max(Math.max(this.minZ - v.z, v.z - this.maxZ), 0.0);
        return d * d + e * e + f * f;
    }

    @Override
    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ)
                || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() { return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), Mth.lerp(0.5, this.minY, this.maxY), Mth.lerp(0.5, this.minZ, this.maxZ)); }

    public Vec3 getBottomCenter() { return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), this.minY, Mth.lerp(0.5, this.minZ, this.maxZ)); }

    public Vec3 getMinPosition() { return new Vec3(this.minX, this.minY, this.minZ); }

    public Vec3 getMaxPosition() { return new Vec3(this.maxX, this.maxY, this.maxZ); }

    public static AABB ofSize(Vec3 center, double x, double y, double z) {
        return new AABB(center.x - x / 2.0, center.y - y / 2.0, center.z - z / 2.0, center.x + x / 2.0, center.y + y / 2.0, center.z + z / 2.0);
    }
}
