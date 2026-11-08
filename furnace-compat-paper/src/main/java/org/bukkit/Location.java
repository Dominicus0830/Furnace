package org.bukkit;

import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/** 월드 안의 좌표와 시선(가변). world 는 null 일 수 있다. */
public class Location implements Cloneable {

    private World world;
    private double x, y, z;
    private float pitch, yaw;

    public Location(World world, double x, double y, double z) { this(world, x, y, z, 0, 0); }

    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public World getWorld() { return world; }

    public void setWorld(World world) { this.world = world; }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getZ() { return z; }

    public void setX(double x) { this.x = x; }

    public void setY(double y) { this.y = y; }

    public void setZ(double z) { this.z = z; }

    public int getBlockX() { return locToBlock(x); }

    public int getBlockY() { return locToBlock(y); }

    public int getBlockZ() { return locToBlock(z); }

    public static int locToBlock(double loc) { return (int) Math.floor(loc); }

    public float getYaw() { return yaw; }

    public float getPitch() { return pitch; }

    public void setYaw(float yaw) { this.yaw = yaw; }

    public void setPitch(float pitch) { this.pitch = pitch; }

    /** 이 위치의 청크(world.getChunkAt(this)). 월드가 없으면 IllegalArgumentException. */
    public Chunk getChunk() {
        if (world == null) throw new IllegalArgumentException("Location has no world");
        return world.getChunkAt(this);
    }

    public Block getBlock() {
        if (world == null) throw new IllegalStateException("world 가 없는 Location");
        return world.getBlockAt(this);
    }

    /** 시선 방향의 단위 벡터. */
    public Vector getDirection() {
        double xz = Math.cos(Math.toRadians(pitch));
        return new Vector(-xz * Math.sin(Math.toRadians(yaw)), -Math.sin(Math.toRadians(pitch)), xz * Math.cos(Math.toRadians(yaw)));
    }

    public Vector toVector() { return new Vector(x, y, z); }

    public Location add(double dx, double dy, double dz) { x += dx; y += dy; z += dz; return this; }

    public Location add(Location o) { return add(o.x, o.y, o.z); }

    public Location add(Vector v) { return add(v.getX(), v.getY(), v.getZ()); }

    public Location subtract(double dx, double dy, double dz) { return add(-dx, -dy, -dz); }

    public Location subtract(Location o) { return add(-o.x, -o.y, -o.z); }

    public Location subtract(Vector v) { return add(-v.getX(), -v.getY(), -v.getZ()); }

    public double lengthSquared() { return x * x + y * y + z * z; }

    public double length() { return Math.sqrt(lengthSquared()); }

    public double distanceSquared(Location o) {
        if (o == null) throw new IllegalArgumentException("Location 이 null");
        if (o.getWorld() != null && getWorld() != null && !o.getWorld().equals(getWorld())) {
            throw new IllegalArgumentException("다른 월드 사이의 거리를 잴 수 없다");
        }
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Location o) { return Math.sqrt(distanceSquared(o)); }

    @Override
    public Location clone() {
        try {
            return (Location) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Location o)) return false;
        return Objects.equals(world, o.world) && Double.doubleToLongBits(x) == Double.doubleToLongBits(o.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(o.y) && Double.doubleToLongBits(z) == Double.doubleToLongBits(o.z)
                && Float.floatToIntBits(pitch) == Float.floatToIntBits(o.pitch) && Float.floatToIntBits(yaw) == Float.floatToIntBits(o.yaw);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + (world != null ? world.hashCode() : 0);
        hash = 19 * hash + (int) (Double.doubleToLongBits(x) ^ (Double.doubleToLongBits(x) >>> 32));
        hash = 19 * hash + (int) (Double.doubleToLongBits(y) ^ (Double.doubleToLongBits(y) >>> 32));
        hash = 19 * hash + (int) (Double.doubleToLongBits(z) ^ (Double.doubleToLongBits(z) >>> 32));
        hash = 19 * hash + Float.floatToIntBits(pitch);
        hash = 19 * hash + Float.floatToIntBits(yaw);
        return hash;
    }

    @Override
    public String toString() {
        return "Location{world=" + world + ",x=" + x + ",y=" + y + ",z=" + z + ",pitch=" + pitch + ",yaw=" + yaw + "}";
    }
}
