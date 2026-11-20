// 배정밀도 3차원 벡터(엔티티 속도 등). 불변이다.
package org.domi.furnace.api.math;

public record Vector3d(double x, double y, double z) {

    public static final Vector3d ZERO = new Vector3d(0, 0, 0);
}
