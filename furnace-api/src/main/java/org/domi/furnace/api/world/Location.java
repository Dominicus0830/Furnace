// 위치 — 차원(minecraft:overworld 등) + 좌표 + 시선. 불변.
package org.domi.furnace.api.world;

import java.util.Objects;

public record Location(String world, double x, double y, double z, float yaw, float pitch) {

    public Location {
        Objects.requireNonNull(world, "world");
    }

    public Location(String world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }

    public int blockX() { return (int) Math.floor(x); }

    public int blockY() { return (int) Math.floor(y); }

    public int blockZ() { return (int) Math.floor(z); }

    public BlockPos blockPos() { return new BlockPos(blockX(), blockY(), blockZ()); }

    public Location withPosition(double nx, double ny, double nz) { return new Location(world, nx, ny, nz, yaw, pitch); }

    public Location withRotation(float nyaw, float npitch) { return new Location(world, x, y, z, nyaw, npitch); }

    public double distanceSquared(Location o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
