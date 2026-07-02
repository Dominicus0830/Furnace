// 구조물 조각/StructureStart 의 3D 정수 경계상자 — 바닐라 BoundingBox 최소 이식(minX..maxZ, intersects, isInside).
package org.domi.furnace.server.world.gen.structure;

public final class BoundingBox {
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    public boolean intersects(BoundingBox other) {
        return maxX >= other.minX && minX <= other.maxX
            && maxY >= other.minY && minY <= other.maxY
            && maxZ >= other.minZ && minZ <= other.maxZ;
    }

    public boolean isInside(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** 이 박스와 다른 박스를 모두 포함하는 최소 박스. */
    public static BoundingBox encapsulate(BoundingBox a, BoundingBox b) {
        return new BoundingBox(
            Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
            Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ)
        );
    }
}
