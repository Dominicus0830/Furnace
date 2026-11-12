package org.bukkit;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 월드 안의 좌표와 시선(가변). world 는 null 일 수 있다.
 * 설정 파일에 Paper 와 같은 꼴(별칭 {@code org.bukkit.Location}, 키 world·x·y·z·yaw·pitch)로 저장·되읽는다(A15-01).
 */
public class Location implements Cloneable, org.bukkit.configuration.serialization.ConfigurationSerializable {

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

    public static int locToBlock(double loc) { return org.bukkit.util.NumberConversions.floor(loc); }

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

    public Location multiply(double m) { x *= m; y *= m; z *= m; return this; }

    public Location zero() { x = 0; y = 0; z = 0; return this; }

    public Location set(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }

    public Location add(Location base, double x, double y, double z) { return set(base.x + x, base.y + y, base.z + z); }

    public Location subtract(Location base, double x, double y, double z) { return set(base.x - x, base.y - y, base.z - z); }

    /** 시선 벡터로 yaw·pitch 를 정한다(x·z 가 0 이면 위·아래만 — yaw 는 그대로). */
    public Location setDirection(Vector vector) {
        final double twoPi = 2 * Math.PI;
        final double vx = vector.getX();
        final double vz = vector.getZ();
        if (vx == 0 && vz == 0) {
            pitch = vector.getY() > 0 ? -90 : 90;
            return this;
        }
        double theta = Math.atan2(-vx, vz);
        yaw = (float) Math.toDegrees((theta + twoPi) % twoPi);
        double xz = Math.sqrt(org.bukkit.util.NumberConversions.square(vx) + org.bukkit.util.NumberConversions.square(vz));
        pitch = (float) Math.toDegrees(Math.atan(-vector.getY() / xz));
        return this;
    }

    /** 월드가 있고 그 월드가 지금 서버에 올라 있는가. */
    public boolean isWorldLoaded() {
        return world != null && world.equals(Bukkit.getWorld(world.getUID()));
    }

    /** 이 칸의 청크가 로드돼 있는가(월드가 없으면 NullPointerException — Paper 와 같다). */
    public boolean isChunkLoaded() { return getWorld().isChunkLoaded(locToBlock(x) >> 4, locToBlock(z) >> 4); }

    /** 좌표를 칸 모서리로 내린 복사본(시선은 그대로). */
    public Location toBlockLocation() {
        Location blockLoc = clone();
        blockLoc.setX(getBlockX());
        blockLoc.setY(getBlockY());
        blockLoc.setZ(getBlockZ());
        return blockLoc;
    }

    @Deprecated
    public long toBlockKey() { return Block.getBlockKey(getBlockX(), getBlockY(), getBlockZ()); }

    /** 칸 가운데(+0.5)로 맞춘 복사본(시선은 그대로). */
    public Location toCenterLocation() {
        Location centerLoc = clone();
        centerLoc.setX(getBlockX() + 0.5);
        centerLoc.setY(getBlockY() + 0.5);
        centerLoc.setZ(getBlockZ() + 0.5);
        return centerLoc;
    }

    // ---- getNearby*(Paper): 월드가 없으면 IllegalArgumentException("Location has no world"), 아니면 월드의 같은 메서드

    private World worldForNearby() {
        World w = getWorld();
        if (w == null) throw new IllegalArgumentException("Location has no world");
        return w;
    }

    public Collection<Entity> getNearbyEntities(double x, double y, double z) { return worldForNearby().getNearbyEntities(this, x, y, z); }

    public Collection<LivingEntity> getNearbyLivingEntities(double radius) { return getNearbyEntitiesByType(LivingEntity.class, radius, radius, radius); }

    public Collection<LivingEntity> getNearbyLivingEntities(double xzRadius, double yRadius) { return getNearbyEntitiesByType(LivingEntity.class, xzRadius, yRadius, xzRadius); }

    public Collection<LivingEntity> getNearbyLivingEntities(double xRadius, double yRadius, double zRadius) { return getNearbyEntitiesByType(LivingEntity.class, xRadius, yRadius, zRadius); }

    public Collection<LivingEntity> getNearbyLivingEntities(double radius, Predicate<? super LivingEntity> predicate) { return getNearbyEntitiesByType(LivingEntity.class, radius, radius, radius, predicate); }

    public Collection<LivingEntity> getNearbyLivingEntities(double xzRadius, double yRadius, Predicate<? super LivingEntity> predicate) { return getNearbyEntitiesByType(LivingEntity.class, xzRadius, yRadius, xzRadius, predicate); }

    public Collection<LivingEntity> getNearbyLivingEntities(double xRadius, double yRadius, double zRadius, Predicate<? super LivingEntity> predicate) { return getNearbyEntitiesByType(LivingEntity.class, xRadius, yRadius, zRadius, predicate); }

    public Collection<Player> getNearbyPlayers(double radius) { return getNearbyEntitiesByType(Player.class, radius, radius, radius); }

    public Collection<Player> getNearbyPlayers(double xzRadius, double yRadius) { return getNearbyEntitiesByType(Player.class, xzRadius, yRadius, xzRadius); }

    public Collection<Player> getNearbyPlayers(double xRadius, double yRadius, double zRadius) { return getNearbyEntitiesByType(Player.class, xRadius, yRadius, zRadius); }

    public Collection<Player> getNearbyPlayers(double radius, Predicate<? super Player> predicate) { return getNearbyEntitiesByType(Player.class, radius, radius, radius, predicate); }

    public Collection<Player> getNearbyPlayers(double xzRadius, double yRadius, Predicate<? super Player> predicate) { return getNearbyEntitiesByType(Player.class, xzRadius, yRadius, xzRadius, predicate); }

    public Collection<Player> getNearbyPlayers(double xRadius, double yRadius, double zRadius, Predicate<? super Player> predicate) { return getNearbyEntitiesByType(Player.class, xRadius, yRadius, zRadius, predicate); }

    public <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, double radius) { return getNearbyEntitiesByType(clazz, radius, radius, radius, null); }

    public <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, double xzRadius, double yRadius) { return getNearbyEntitiesByType(clazz, xzRadius, yRadius, xzRadius, null); }

    public <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, double xRadius, double yRadius, double zRadius) { return getNearbyEntitiesByType(clazz, xRadius, yRadius, zRadius, null); }

    public <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, double radius, Predicate<? super T> predicate) { return getNearbyEntitiesByType(clazz, radius, radius, radius, predicate); }

    public <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, double xzRadius, double yRadius, Predicate<? super T> predicate) { return getNearbyEntitiesByType(clazz, xzRadius, yRadius, xzRadius, predicate); }

    public <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends Entity> clazz, double xRadius, double yRadius, double zRadius, Predicate<? super T> predicate) {
        return worldForNearby().getNearbyEntitiesByType(clazz, this, xRadius, yRadius, zRadius, predicate);
    }

    // ---- 위치 수학(Paper 의 Position 인터페이스 멤버 가운데 값만 보는 것)

    public double x() { return getX(); }

    public double y() { return getY(); }

    public double z() { return getZ(); }

    /** 좌표 셋과 yaw·pitch 가 모두 유한한가. */
    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw) && Float.isFinite(pitch);
    }

    /** 이 좌표·시선을 world 에서 쓰는 새 Location. */
    public Location toLocation(World world) { return new Location(world, x, y, z, yaw, pitch); }

    public void checkFinite() throws IllegalArgumentException {
        org.bukkit.util.NumberConversions.checkFinite(x, "x not finite");
        org.bukkit.util.NumberConversions.checkFinite(y, "y not finite");
        org.bukkit.util.NumberConversions.checkFinite(z, "z not finite");
        org.bukkit.util.NumberConversions.checkFinite(pitch, "pitch not finite");
        org.bukkit.util.NumberConversions.checkFinite(yaw, "yaw not finite");
    }

    /** "world=이름,x=..,y=..,z=..,pitch=..,yaw=.." — 월드가 없으면 NullPointerException(Paper 와 같다). */
    public String asString() {
        return "world=" + world.getName() + ",x=" + x + ",y=" + y + ",z=" + z + ",pitch=" + pitch + ",yaw=" + yaw;
    }

    public static float normalizeYaw(float yaw) {
        yaw %= 360.0f;
        if (yaw >= 180.0f) {
            yaw -= 360.0f;
        } else if (yaw < -180.0f) {
            yaw += 360.0f;
        }
        return yaw;
    }

    public static float normalizePitch(float pitch) {
        if (pitch > 90.0f) {
            pitch = 90.0f;
        } else if (pitch < -90.0f) {
            pitch = -90.0f;
        }
        return pitch;
    }

    public Location add(double dx, double dy, double dz) { x += dx; y += dy; z += dz; return this; }

    /** 다른 월드(또는 null)의 Location 이면 IllegalArgumentException — Paper 와 같다. */
    public Location add(Location o) {
        if (o == null || o.getWorld() != getWorld()) throw new IllegalArgumentException("Cannot add Locations of differing worlds");
        return add(o.x, o.y, o.z);
    }

    public Location add(Vector v) { return add(v.getX(), v.getY(), v.getZ()); }

    public Location subtract(double dx, double dy, double dz) { x -= dx; y -= dy; z -= dz; return this; }

    public Location subtract(Location o) {
        if (o == null || o.getWorld() != getWorld()) throw new IllegalArgumentException("Cannot add Locations of differing worlds");
        x -= o.x;
        y -= o.y;
        z -= o.z;
        return this;
    }

    public Location subtract(Vector v) { x -= v.getX(); y -= v.getY(); z -= v.getZ(); return this; }

    public double lengthSquared() { return org.bukkit.util.NumberConversions.square(x) + org.bukkit.util.NumberConversions.square(y) + org.bukkit.util.NumberConversions.square(z); }

    public double length() { return Math.sqrt(lengthSquared()); }

    /** Paper 처럼: null·월드 없음·다른 월드는 IllegalArgumentException(문구도 같다). */
    public double distanceSquared(Location o) {
        if (o == null) {
            throw new IllegalArgumentException("Cannot measure distance to a null location");
        } else if (o.getWorld() == null || getWorld() == null) {
            throw new IllegalArgumentException("Cannot measure distance to a null world");
        } else if (!o.getWorld().equals(getWorld())) {
            throw new IllegalArgumentException("Cannot measure distance between " + getWorld().getName() + " and " + o.getWorld().getName());
        }
        return org.bukkit.util.NumberConversions.square(x - o.x) + org.bukkit.util.NumberConversions.square(y - o.y) + org.bukkit.util.NumberConversions.square(z - o.z);
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

    /** 설정에 쓸 값: 월드가 있으면 그 이름, 좌표는 double, 시선은 float. */
    @Override
    public java.util.Map<String, Object> serialize() {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (world != null) data.put("world", world.getName());
        data.put("x", x);
        data.put("y", y);
        data.put("z", z);
        data.put("yaw", yaw);
        data.put("pitch", pitch);
        return data;
    }

    /** serialize() 의 맵에서. 이름의 월드가 이 서버에 없으면 IllegalArgumentException("unknown world") — Paper 와 같다. */
    public static Location deserialize(java.util.Map<String, Object> args) {
        World w = null;
        if (args.containsKey("world")) {
            w = Bukkit.getWorld(String.valueOf(args.get("world")));
            if (w == null) throw new IllegalArgumentException("unknown world");
        }
        return new Location(w, org.bukkit.util.NumberConversions.toDouble(args.get("x")), org.bukkit.util.NumberConversions.toDouble(args.get("y")),
                org.bukkit.util.NumberConversions.toDouble(args.get("z")), org.bukkit.util.NumberConversions.toFloat(args.get("yaw")),
                org.bukkit.util.NumberConversions.toFloat(args.get("pitch")));
    }
}
