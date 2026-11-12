// 회전 쿼터니언(x, y, z, w) — 디스플레이 엔티티의 왼쪽·오른쪽 회전. 불변. 정규화는 클라이언트가 한다(바닐라와 같다).
package org.domi.furnace.api.math;

public record Quaternionf(float x, float y, float z, float w) {

    public static final Quaternionf IDENTITY = new Quaternionf(0, 0, 0, 1);

    /** 축(정규화된 x, y, z) 둘레로 angle 라디안 돌리는 회전. */
    public static Quaternionf axisAngle(float ax, float ay, float az, float angle) {
        float s = (float) Math.sin(angle / 2), c = (float) Math.cos(angle / 2);
        return new Quaternionf(ax * s, ay * s, az * s, c);
    }

    /** Y 축 둘레로 도(degree) 단위 회전. */
    public static Quaternionf rotationY(float degrees) { return axisAngle(0, 1, 0, (float) Math.toRadians(degrees)); }
}
