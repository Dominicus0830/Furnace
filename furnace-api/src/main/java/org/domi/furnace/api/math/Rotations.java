// 세 축 각도(도)이며 불변이다. 갑옷 거치대 머리·몸·팔·다리 자세에 쓴다(바닐라 Rotations).
package org.domi.furnace.api.math;

public record Rotations(float x, float y, float z) {

    public static final Rotations ZERO = new Rotations(0, 0, 0);
}
