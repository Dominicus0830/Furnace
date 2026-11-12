package net.minecraft.core;

/** 회전 값 하나(Mojang 이름 shim): 아머 스탠드 같은 엔티티 메타데이터의 각도 세 개. 불변. */
public final class Rotations {

    private final float x;
    private final float y;
    private final float z;

    public Rotations(float x, float y, float z) {
        // 바닐라: 360 으로 나눈 나머지(부호 유지), 무한·NaN 은 0
        this.x = !Float.isInfinite(x) && !Float.isNaN(x) ? x % 360.0F : 0.0F;
        this.y = !Float.isInfinite(y) && !Float.isNaN(y) ? y % 360.0F : 0.0F;
        this.z = !Float.isInfinite(z) && !Float.isNaN(z) ? z % 360.0F : 0.0F;
    }

    public float getX() { return x; }

    public float getY() { return y; }

    public float getZ() { return z; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rotations r && x == r.x && y == r.y && z == r.z;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(x, y, z); }
}
