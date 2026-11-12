package org.bukkit.util;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;

/** 3차원 벡터(가변). 설정 파일에는 Paper 와 같은 별칭 {@code Vector}, 키 x·y·z 로 저장·되읽는다(A15-01). */
@org.bukkit.configuration.serialization.SerializableAs("Vector")
public class Vector implements Cloneable, org.bukkit.configuration.serialization.ConfigurationSerializable {

    private static final Random random = new Random();
    private static final double epsilon = 0.000001;

    protected double x;
    protected double y;
    protected double z;

    public Vector() {}

    public Vector(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

    public Vector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

    public Vector(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getZ() { return z; }

    public int getBlockX() { return NumberConversions.floor(x); }

    public int getBlockY() { return NumberConversions.floor(y); }

    public int getBlockZ() { return NumberConversions.floor(z); }

    public Vector setX(int x) { this.x = x; return this; }

    public Vector setX(double x) { this.x = x; return this; }

    public Vector setX(float x) { this.x = x; return this; }

    public Vector setY(int y) { this.y = y; return this; }

    public Vector setY(double y) { this.y = y; return this; }

    public Vector setY(float y) { this.y = y; return this; }

    public Vector setZ(int z) { this.z = z; return this; }

    public Vector setZ(double z) { this.z = z; return this; }

    public Vector setZ(float z) { this.z = z; return this; }

    public Vector add(Vector o) { x += o.x; y += o.y; z += o.z; return this; }

    public Vector subtract(Vector o) { x -= o.x; y -= o.y; z -= o.z; return this; }

    public Vector multiply(int m) { x *= m; y *= m; z *= m; return this; }

    public Vector multiply(double m) { x *= m; y *= m; z *= m; return this; }

    public Vector multiply(float m) { x *= m; y *= m; z *= m; return this; }

    public Vector multiply(Vector o) { x *= o.x; y *= o.y; z *= o.z; return this; }

    public double lengthSquared() { return NumberConversions.square(x) + NumberConversions.square(y) + NumberConversions.square(z); }

    public double length() { return Math.sqrt(NumberConversions.square(x) + NumberConversions.square(y) + NumberConversions.square(z)); }

    public double distanceSquared(Vector o) {
        return NumberConversions.square(x - o.x) + NumberConversions.square(y - o.y) + NumberConversions.square(z - o.z);
    }

    public double distance(Vector o) { return Math.sqrt(NumberConversions.square(x - o.x) + NumberConversions.square(y - o.y) + NumberConversions.square(z - o.z)); }

    public double dot(Vector o) { return x * o.x + y * o.y + z * o.z; }

    public Vector crossProduct(Vector o) {
        double nx = y * o.z - o.y * z, ny = z * o.x - o.z * x, nz = x * o.y - o.x * y;
        x = nx;
        y = ny;
        z = nz;
        return this;
    }

    public Vector normalize() {
        double length = length();
        x /= length;
        y /= length;
        z /= length;
        return this;
    }

    public Vector zero() { x = y = z = 0; return this; }

    public Vector divide(Vector o) { x /= o.x; y /= o.y; z /= o.z; return this; }

    public Vector copy(Vector o) { x = o.x; y = o.y; z = o.z; return this; }

    /** 두 벡터 사이 각(라디안, float). acos 의 인자는 [-1, 1] 로 자른다(길이 0 이면 NaN, Paper 와 같다). */
    public float angle(Vector other) {
        double dot = dot(other) / (length() * other.length());
        dot = dot < -1.0 ? -1.0 : Math.min(dot, 1.0);
        return (float) Math.acos(dot);
    }

    public Vector midpoint(Vector other) {
        x = (x + other.x) / 2;
        y = (y + other.y) / 2;
        z = (z + other.z) / 2;
        return this;
    }

    public Vector getMidpoint(Vector other) { return new Vector((x + other.x) / 2, (y + other.y) / 2, (z + other.z) / 2); }

    public Vector getCrossProduct(Vector o) {
        return new Vector(y * o.z - o.y * z, z * o.x - o.z * x, x * o.y - o.x * y);
    }

    public boolean isZero() { return x == 0 && y == 0 && z == 0; }

    /** -0.0 을 0.0 으로(패키지 안에서만, Paper 와 같다). */
    Vector normalizeZeros() {
        if (x == -0.0D) x = 0.0D;
        if (y == -0.0D) y = 0.0D;
        if (z == -0.0D) z = 0.0D;
        return this;
    }

    public boolean isInAABB(Vector min, Vector max) {
        return x >= min.x && x <= max.x && y >= min.y && y <= max.y && z >= min.z && z <= max.z;
    }

    public boolean isInSphere(Vector origin, double radius) {
        return (NumberConversions.square(origin.x - x) + NumberConversions.square(origin.y - y) + NumberConversions.square(origin.z - z)) <= NumberConversions.square(radius);
    }

    public boolean isNormalized() { return Math.abs(lengthSquared() - 1) < getEpsilon(); }

    public Vector rotateAroundX(double angle) {
        double angleCos = Math.cos(angle);
        double angleSin = Math.sin(angle);
        double y = angleCos * getY() - angleSin * getZ();
        double z = angleSin * getY() + angleCos * getZ();
        return setY(y).setZ(z);
    }

    public Vector rotateAroundY(double angle) {
        double angleCos = Math.cos(angle);
        double angleSin = Math.sin(angle);
        double x = angleCos * getX() + angleSin * getZ();
        double z = -angleSin * getX() + angleCos * getZ();
        return setX(x).setZ(z);
    }

    public Vector rotateAroundZ(double angle) {
        double angleCos = Math.cos(angle);
        double angleSin = Math.sin(angle);
        double x = angleCos * getX() - angleSin * getY();
        double y = angleSin * getX() + angleCos * getY();
        return setX(x).setY(y);
    }

    /** 축이 단위 벡터가 아니면 복사해 정규화한 뒤 돌린다. axis 가 null 이면 IllegalArgumentException. */
    public Vector rotateAroundAxis(Vector axis, double angle) throws IllegalArgumentException {
        if (axis == null) throw new IllegalArgumentException("The provided axis vector was null");
        return rotateAroundNonUnitAxis(axis.isNormalized() ? axis : axis.clone().normalize(), angle);
    }

    /** 로드리게스 회전 공식. 축이 단위 벡터가 아니면 결과가 틀린다(Paper 와 같다). */
    public Vector rotateAroundNonUnitAxis(Vector axis, double angle) throws IllegalArgumentException {
        if (axis == null) throw new IllegalArgumentException("The provided axis vector was null");
        double x = getX(), y = getY(), z = getZ();
        double x2 = axis.getX(), y2 = axis.getY(), z2 = axis.getZ();
        double cosTheta = Math.cos(angle);
        double sinTheta = Math.sin(angle);
        double dotProduct = this.dot(axis);
        double xPrime = x2 * dotProduct * (1d - cosTheta) + x * cosTheta + (-z2 * y + y2 * z) * sinTheta;
        double yPrime = y2 * dotProduct * (1d - cosTheta) + y * cosTheta + (z2 * x - x2 * z) * sinTheta;
        double zPrime = z2 * dotProduct * (1d - cosTheta) + z * cosTheta + (-y2 * x + x2 * y) * sinTheta;
        return setX(xPrime).setY(yPrime).setZ(zPrime);
    }

    public Location toLocation(World world) { return new Location(world, x, y, z); }

    public Location toLocation(World world, float yaw, float pitch) { return new Location(world, x, y, z, yaw, pitch); }

    public BlockVector toBlockVector() { return new BlockVector(x, y, z); }

    public void checkFinite() throws IllegalArgumentException {
        NumberConversions.checkFinite(x, "x not finite");
        NumberConversions.checkFinite(y, "y not finite");
        NumberConversions.checkFinite(z, "z not finite");
    }

    public static double getEpsilon() { return epsilon; }

    public static Vector getMinimum(Vector v1, Vector v2) { return new Vector(Math.min(v1.x, v2.x), Math.min(v1.y, v2.y), Math.min(v1.z, v2.z)); }

    public static Vector getMaximum(Vector v1, Vector v2) { return new Vector(Math.max(v1.x, v2.x), Math.max(v1.y, v2.y), Math.max(v1.z, v2.z)); }

    public static Vector getRandom() { return new Vector(random.nextDouble(), random.nextDouble(), random.nextDouble()); }

    @Override
    public Vector clone() {
        try {
            return (Vector) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Vector v && Math.abs(x - v.x) < epsilon && Math.abs(y - v.y) < epsilon && Math.abs(z - v.z) < epsilon
                && this.getClass().equals(o.getClass());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (int) (Double.doubleToLongBits(x) ^ (Double.doubleToLongBits(x) >>> 32));
        hash = 79 * hash + (int) (Double.doubleToLongBits(y) ^ (Double.doubleToLongBits(y) >>> 32));
        hash = 79 * hash + (int) (Double.doubleToLongBits(z) ^ (Double.doubleToLongBits(z) >>> 32));
        return hash;
    }

    @Override
    public String toString() { return x + "," + y + "," + z; }

    @Override
    public java.util.Map<String, Object> serialize() {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("x", x);
        data.put("y", y);
        data.put("z", z);
        return data;
    }

    /** serialize() 의 맵에서(빠진 축은 0). */
    public static Vector deserialize(java.util.Map<String, Object> args) {
        return new Vector(args.containsKey("x") ? NumberConversions.toDouble(args.get("x")) : 0,
                args.containsKey("y") ? NumberConversions.toDouble(args.get("y")) : 0,
                args.containsKey("z") ? NumberConversions.toDouble(args.get("z")) : 0);
    }
}
