package org.bukkit.util;

/** 3차원 벡터(가변). 설정 파일에는 Paper 와 같은 별칭 {@code Vector}, 키 x·y·z 로 저장·되읽는다(A15-01). */
@org.bukkit.configuration.serialization.SerializableAs("Vector")
public class Vector implements Cloneable, org.bukkit.configuration.serialization.ConfigurationSerializable {

    private double x, y, z;

    public Vector() {}

    public Vector(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

    public Vector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

    public Vector(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getZ() { return z; }

    public int getBlockX() { return (int) Math.floor(x); }

    public int getBlockY() { return (int) Math.floor(y); }

    public int getBlockZ() { return (int) Math.floor(z); }

    public Vector setX(double x) { this.x = x; return this; }

    public Vector setY(double y) { this.y = y; return this; }

    public Vector setZ(double z) { this.z = z; return this; }

    public Vector add(Vector o) { x += o.x; y += o.y; z += o.z; return this; }

    public Vector subtract(Vector o) { x -= o.x; y -= o.y; z -= o.z; return this; }

    public Vector multiply(double m) { x *= m; y *= m; z *= m; return this; }

    public Vector multiply(Vector o) { x *= o.x; y *= o.y; z *= o.z; return this; }

    public double lengthSquared() { return x * x + y * y + z * z; }

    public double length() { return Math.sqrt(lengthSquared()); }

    public double distanceSquared(Vector o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Vector o) { return Math.sqrt(distanceSquared(o)); }

    public double dot(Vector o) { return x * o.x + y * o.y + z * o.z; }

    public Vector crossProduct(Vector o) {
        double nx = y * o.z - o.y * z, ny = z * o.x - o.z * x, nz = x * o.y - o.x * y;
        x = nx;
        y = ny;
        z = nz;
        return this;
    }

    public Vector normalize() {
        double len = length();
        x /= len;
        y /= len;
        z /= len;
        return this;
    }

    public Vector zero() { x = y = z = 0; return this; }

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
        return o instanceof Vector v && Math.abs(x - v.x) < 1e-6 && Math.abs(y - v.y) < 1e-6 && Math.abs(z - v.z) < 1e-6;
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
