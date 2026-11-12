// 축 정렬 상자(월드 좌표, 바닐라 AABB). 엔티티 충돌 상자와 상자 조회에 쓴다. 불변. min 이 max 보다 클 수 있는 값은 만들 때 그대로 둔다(바닐라 AABB 와 같다).
package org.domi.furnace.api.world;

public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /** 바닐라 AABB.inflate: 각 축 양쪽으로 늘린다(음수면 줄인다). */
    public Aabb inflate(double x, double y, double z) {
        return new Aabb(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }

    /** 바닐라 AABB.intersects: 경계만 닿는 것은 겹침이 아니다. */
    public boolean intersects(Aabb o) {
        return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
    }

    public double centerX() { return (minX + maxX) * 0.5; }

    public double centerY() { return (minY + maxY) * 0.5; }

    public double centerZ() { return (minZ + maxZ) * 0.5; }
}
