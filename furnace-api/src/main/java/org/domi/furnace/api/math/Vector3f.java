// 실수 3차원 벡터(디스플레이 엔티티의 이동·크기 등). 불변.
package org.domi.furnace.api.math;

public record Vector3f(float x, float y, float z) {

    public static final Vector3f ZERO = new Vector3f(0, 0, 0);
    public static final Vector3f ONE = new Vector3f(1, 1, 1);

    public Vector3f add(Vector3f o) { return new Vector3f(x + o.x, y + o.y, z + o.z); }

    public Vector3f scale(float f) { return new Vector3f(x * f, y * f, z * f); }
}
