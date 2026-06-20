package org.domi.furnace.server.world;

public record SpawnPoint(int x, int y, int z, float yaw) {
    public double playerX() { return x + 0.5; }
    public double playerY() { return y; }
    public double playerZ() { return z + 0.5; }
}
